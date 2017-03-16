package cz.fmo.recording;

import android.media.MediaMuxer;

import java.io.File;

import cz.fmo.util.Time;

/**
 * For recording long movies.
 */
public class ManualRecordingTask implements SaveThread.Task {
    private static final int NO_FIRST = -1;
    private static final float CHUNK_SEC = 1.f;
    private final Object mLock = new Object();
    private final File mFile;
    private final CyclicBuffer mBuf;
    private final SaveThreadHandler mHandler;
    private int mFirst = NO_FIRST;
    private MediaMuxer mMuxer;
    private int mTrack;
    private int mFramesWritten = 0;
    private boolean mInitialized = false;
    private boolean mFinished = false;

    /**
     * Saves the contents of the buffer to the specified file. Recording is performed indefinitely,
     * until terminate() is called. The constructor does all scheduling on its own, therefore
     * there's no need to send the task object to an event queue.
     *
     * @param file   file to save to, should be writable and have a .mp4 extension
     * @param thread thread to use for saving
     */
    public ManualRecordingTask(File file, SaveThread thread) {
        this.mFile = file;
        this.mBuf = thread.getBuffer();
        this.mHandler = thread.getHandler();

        if (mHandler == null) {
            thread.sendCallback(file, false);
            return;
        }

        mHandler.sendTask(this, Time.toMs(CHUNK_SEC));
    }

    private boolean init() {
        synchronized (mBuf) {
            if (mBuf.empty()) return false;
            mFirst = mBuf.begin();
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

    private void writeFrames(SaveThread thread) {
        if (!mInitialized) {
            boolean win = init();
            if (!win) return;
            mInitialized = true;
        }

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
    public void perform(SaveThread thread) {
        synchronized (mLock) {
            if (mFinished) return;
            writeFrames(thread);
        }
        mHandler.sendTask(this, Time.toMs(CHUNK_SEC));
    }

    @Override
    public void terminate(SaveThread thread) {
        synchronized (mLock) {
            if (mFinished) return;
            mFinished = true;

            writeFrames(thread);

            if (mMuxer != null) {
                mMuxer.stop();
                mMuxer.release();
                mMuxer = null;
            }

            mHandler.cancelTask(this);
        }
        thread.sendCallback(mFile, mFramesWritten > 0);
    }
}
