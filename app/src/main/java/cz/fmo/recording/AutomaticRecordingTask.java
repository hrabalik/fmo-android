package cz.fmo.recording;

import android.media.MediaMuxer;

import java.io.File;

import cz.fmo.util.Time;

/**
 * For recording short movies on demand.
 */
public class AutomaticRecordingTask implements SaveThread.Task {
    private final Object mLock = new Object();
    private final float mWaitSec;
    private final float mLengthSec;
    private final File mFile;
    private final CyclicBuffer mBuf;
    private final SaveThreadHandler mHandler;
    private boolean mPerformed = false;
    private boolean mCancelled = false;
    private int mFirst;

    /**
     * Saves the latest lengthSec of frames to the specified file. If the start of the movie is less
     * than MIN_MARGIN_SEC from the start of the buffer, the method refuses to save the file. The
     * MIN_MARGIN_SEC requirement is a precaution to avoid overwriting data of a frame while it is
     * still being saved.
     * <p>
     * Use terminate() to cancel the task during the waiting period. The constructor does all
     * scheduling on its own, therefore there's no need to send the task object to an event queue.
     *
     * @param waitSec   time to wait for before saving, in seconds
     * @param lengthSec length of the movie to be saved
     * @param file      file to save to, should be writable and have a .mp4 extension
     * @param thread    thread to use for saving
     */
    public AutomaticRecordingTask(float waitSec, float lengthSec, File file,
                                  SaveThread thread) {
        this.mWaitSec = waitSec;
        this.mLengthSec = lengthSec;
        this.mFile = file;
        this.mBuf = thread.getBuffer();
        this.mHandler = thread.getHandler();
        boolean win = init();

        if (!win) {
            mCancelled = true;
            thread.sendCallback(file, false);
            return;
        }

        mHandler.sendTask(this, Time.toMs(mWaitSec));
    }

    private boolean init() {
        if (mHandler == null) return false;

        synchronized (mBuf) {
            if (mBuf.empty()) return false;
            long nowUs = mBuf.getTimeUs(mBuf.prev(mBuf.end()));
            long startUs = nowUs + Time.toUs(mWaitSec) - Time.toUs(mLengthSec);
            mFirst = mBuf.findByTime(mBuf.begin(), mBuf.end(), startUs);
            mFirst = mBuf.findIFrame(mFirst);
            if (!mBuf.isIFrame(mFirst)) return false;
            float minMargin = SaveThread.MIN_MARGIN_SEC + mWaitSec;
            long marginUs = mBuf.getDurationUs(mBuf.begin(), mFirst);
            if (marginUs < Time.toUs(minMargin)) return false;
            int marginFrames = mBuf.getNumFrames(mBuf.begin(), mFirst);
            if (marginFrames < Time.toFrames(minMargin)) return false;
        }

        return true;
    }

    @Override
    public void perform(SaveThread thread) {
        synchronized (mLock) {
            if (mPerformed || mCancelled) return;
            mPerformed = true;

            int last;
            synchronized (mBuf) {
                last = mBuf.end();
            }

            boolean win = true;
            MediaMuxer muxer = null;
            try {
                muxer = new MediaMuxer(mFile.getPath(), SaveThread.OUTPUT_FORMAT);
                int track = muxer.addTrack(mBuf.getFormat());
                muxer.start();
                thread.writeFrames(mFirst, last, muxer, track);
            } catch (Exception e) {
                win = false;
            }

            if (muxer != null) {
                muxer.stop();
                muxer.release();
            }

            thread.sendCallback(mFile, win);
        }
    }

    @Override
    public void terminate(SaveThread thread) {
        synchronized (mLock) {
            if (mPerformed || mCancelled) return;
            mCancelled = true;
            mHandler.cancelTask(this);
        }
    }
}
