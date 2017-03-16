package cz.fmo.recording;

import android.media.MediaMuxer;

import java.io.File;

import cz.fmo.util.Time;

/**
 * For recording long movies.
 */
public class ManualRecordingTask implements SaveMovieThread.Task {
    private static final float CHUNK_SEC = 1.f;
    private final Object mLock = new Object();
    private final File mFile;
    private final CyclicBuffer mBuf;
    private final SaveMovieThreadHandler mHandler;
    private int mFirst;
    private MediaMuxer mMuxer;
    private int mTrack;
    private int mFramesWritten = 0;

    /**
     * Saves the contents of the buffer to the specified file. Recording is performed indefinitely,
     * until terminate() is called. The constructor does all scheduling on its own, therefore
     * there's no need to send the task object to an event queue.
     *
     * @param file   file to save to, should be writable and have a .mp4 extension
     * @param thread thread to use for saving
     */
    public ManualRecordingTask(File file, SaveMovieThread thread) {
        this.mFile = file;
        this.mBuf = thread.getBuffer();
        this.mHandler = thread.getHandler();
        boolean win = init();

        if (!win) {
            thread.sendCallback(file, false);
            return;
        }

        mHandler.sendTask(this, Time.toMs(CHUNK_SEC));
    }

    private boolean init() {
        if (mHandler == null) return false;

        synchronized (mBuf) {
            if (mBuf.empty()) return false;
            int last = mBuf.prev(mBuf.end());
            mFirst = mBuf.findIFrame(last);
            if (!mBuf.isIFrame(mFirst)) return false;
            float minMargin = SaveMovieThread.MIN_MARGIN_SEC + CHUNK_SEC;
            long marginUs = mBuf.getDurationUs(mBuf.begin(), mFirst);
            if (marginUs < Time.toUs(minMargin)) return false;
            int marginFrames = mBuf.getNumFrames(mBuf.begin(), mFirst);
            if (marginFrames < Time.toFrames(minMargin)) return false;
        }

        try {
            mMuxer = new MediaMuxer(mFile.getPath(), SaveMovieThread.OUTPUT_FORMAT);
        } catch (java.io.IOException e) {
            return false;
        }

        mTrack = mMuxer.addTrack(mBuf.getFormat());
        mMuxer.start();
        return true;
    }

    private void writeFrames(SaveMovieThread thread) {
        int last;
        synchronized (mBuf) {
            last = mBuf.end();
        }
        int numFrames = mBuf.getNumFrames(mFirst, last);
        thread.writeFrames(mFirst, last, mMuxer, mTrack);
        mFramesWritten += numFrames;
        mFirst = last;
    }

    @Override
    public void perform(SaveMovieThread thread) {
        synchronized (mLock) {
            if (mMuxer == null) return;
            writeFrames(thread);
        }
        mHandler.sendTask(this, Time.toMs(CHUNK_SEC));
    }

    @Override
    public void terminate(SaveMovieThread thread) {
        synchronized (mLock) {
            if (mMuxer == null) return;
            writeFrames(thread);
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
            mHandler.cancelTask(this);
        }
        thread.sendCallback(mFile, mFramesWritten > 0);
    }
}
