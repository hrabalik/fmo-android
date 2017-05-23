package cz.fmo.recording;

import android.media.MediaMuxer;

import java.io.File;

import cz.fmo.util.Time;

/**
 * For recording short movies in reaction to ongoing events.
 */
public class AutomaticRecordingTask implements SaveThread.Task {
    private static final float CHUNK_SEC = 1.f;
    private final Object mLock = new Object();
    private final File mFile;
    private final long mMarginUs;
    private final SaveThread mThread;
    private final CyclicBuffer mBuf;
    private final SaveThreadHandler mHandler;
    private int mFirst = -1;
    private long mEndUs = -1;
    private MediaMuxer mMuxer;
    private int mTrack;
    private int mFramesWritten = 0;
    private boolean mFinished = false;

    /**
     * Saves a video that contains interesting events, surrounded by a given time margin. If new
     * events happen before the recording is finished, the extend() method can be used to postpone
     * the end of the video. Once the end of the video is saved, the recording is stopped
     * automatically.
     *
     * @param marginSec number of seconds to include before the first event and after the last one
     * @param file      file to save to, should be writable and have a .mp4 extension
     * @param thread    thread to use for saving
     */
    public AutomaticRecordingTask(float marginSec, File file, SaveThread thread) {
        mFile = file;
        mMarginUs = Time.toUs(marginSec);
        mThread = thread;
        mBuf = thread.getBuffer();
        mHandler = thread.getHandler();

        if (mHandler == null) {
            error();
            return;
        }

        if (!init()) {
            error();
            return;
        }

        mHandler.sendTask(this, 0);
    }

    private void error() {
        mThread.sendCallback(mFile, false);
        mFinished = true;
    }

    private long latestUs(CyclicBuffer b) {
        return b.getTimeUs(b.prev(b.end()));
    }

    private boolean init() {
        synchronized (mBuf) {
            if (mBuf.empty()) return false;
            long nowUs = latestUs(mBuf);
            long startUs = nowUs - mMarginUs;
            mEndUs = nowUs + mMarginUs;
            mFirst = mBuf.findByTime(mBuf.begin(), mBuf.end(), startUs);
            mFirst = mBuf.findIFrame(mFirst);
            if (!mBuf.isIFrame(mFirst)) return false;
        }

        try {
            mMuxer = new MediaMuxer(mFile.getPath(), SaveThread.OUTPUT_FORMAT);
        } catch (java.io.IOException e) {
            return false;
        }

        mTrack = mMuxer.addTrack(mBuf.getFormat());
        mMuxer.start();
        return true;
    }

    private boolean writeFrames() {
        boolean finish = false;
        int last;
        synchronized (mBuf) {
            last = mBuf.end();

            // detect end of video
            long nowUs = latestUs(mBuf);
            if (nowUs > mEndUs) {
                last = mBuf.findByTime(mBuf.begin(), mBuf.end(), mEndUs);
                last = mBuf.next(last);
                finish = true;
            }
        }

        int numFrames = mBuf.getNumFrames(mFirst, last);
        mThread.writeFrames(mFirst, last, mMuxer, mTrack);
        mFramesWritten += numFrames;
        mFirst = last;
        return finish;
    }

    @Override
    public void perform() {
        synchronized (mLock) {
            if (mFinished) return;
            mFinished = writeFrames();
        }

        if (mFinished) {
            cleanUp();
            mThread.sendCallback(mFile, mFramesWritten > 0);
        } else {
            mHandler.sendTask(this, Time.toMs(CHUNK_SEC));
        }
    }

    @Override
    public void terminate() {
        synchronized (mLock) {
            if (mFinished) return;
            mFinished = true;
        }

        writeFrames();
        cleanUp();
        mThread.sendCallback(mFile, mFramesWritten > 0);
    }

    private void cleanUp() {
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }

        mHandler.cancelTask(this);
    }

    @Override
    public boolean extend() {
        synchronized (mLock) {
            if (mFinished) return false;
            if (!extendImpl()) {
                error();
                return false;
            }
        }
        return true;
    }

    private boolean extendImpl() {
         synchronized (mBuf) {
            if (mBuf.empty()) return false;
            long nowUs = latestUs(mBuf);
            mEndUs = nowUs + mMarginUs;
        }
        return true;
    }
}
