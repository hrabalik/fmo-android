package cz.fmo.recording;

import android.media.MediaCodec;
import android.media.MediaMuxer;

import java.io.File;
import java.nio.ByteBuffer;

import cz.fmo.util.GenericThread;

/**
 * Thread for saving videos from frames stored in a buffer. The buffer needs to be filled with
 * MPEG-4 frames and the exact format has to be specified using the CyclicBuffer.setFormat() method.
 */
public class SaveMovieThread extends GenericThread<SaveMovieThreadHandler> {
    private static final int OUTPUT_FORMAT = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
    private static final float SECOND_US = 1e6f;
    private static final float SECOND_MS = 1e3f;
    private static final float SECOND_FRAMES = 30;
    private static final long AUTOMATIC_MIN_LENGTH_US = (long) (1.5f * SECOND_US);
    private static final float MIN_MARGIN_SEC = 1.f;
    private static final long MIN_MARGIN_US = (long) (SECOND_US * MIN_MARGIN_SEC);
    private static final int MIN_MARGIN_FRAMES = (int) (SECOND_FRAMES * MIN_MARGIN_SEC);
    private final CyclicBuffer mBuf;
    private final ByteBuffer mBufCache;
    private final MediaCodec.BufferInfo mInfoCache;
    private final Callback mCb;

    public SaveMovieThread(CyclicBuffer buf, Callback cb) {
        super("SaveMovieThread");
        mBuf = buf;
        mBufCache = buf.getCache();
        mInfoCache = new MediaCodec.BufferInfo();
        mCb = cb;
    }

    /**
     * @return Cyclic buffer object that is used for temporary storing of pre-encoded H.264 frames.
     */
    private CyclicBuffer getBuffer() {
        return mBuf;
    }

    private void sendCallback(File file, boolean success) {
        if (mCb != null) {
            mCb.saveCompleted(file, success);
        }
    }

    @Override
    protected SaveMovieThreadHandler makeHandler() {
        return new SaveMovieThreadHandler(this);
    }

    private void writeFrames(int first, int last, MediaMuxer muxer, int track) {
        synchronized (mBufCache) {
            for (int index = first; index != last; index = mBuf.next(index)) {
                mBuf.get(index, mBufCache, mInfoCache);
                muxer.writeSampleData(track, mBufCache, mInfoCache);
            }
        }
    }

    public interface Task {
        void perform(SaveMovieThread thread);

        void terminate(SaveMovieThread thread);
    }

    @SuppressWarnings("UnusedParameters")
    public interface Callback {
        void saveCompleted(File file, boolean success);
    }

    public static class AutomaticRecordingTask implements Task {
        private final Object mLock = new Object();
        private final long mWaitMs;
        private final long mWaitUs;
        private final int mWaitFrames;
        private final long mLenUs;
        private final File mFile;
        private final CyclicBuffer mBuf;
        private final SaveMovieThreadHandler mHandler;
        private boolean mPerformed = false;
        private boolean mCancelled = false;
        private int mFirst;

        /**
         * Saves the latest lengthSec of frames to the specified file. If the start of the movie is
         * less than MIN_MARGIN_SEC from the start of the buffer, the method refuses to save the
         * file. The MIN_MARGIN_SEC requirement is a precaution to avoid overwriting data of a frame
         * while it is still being saved.
         * <p>
         * Use terminate() to cancel the task during the waiting period.
         *
         * @param waitSec   time to wait for before saving, in seconds
         * @param lengthSec length of the movie to be saved
         * @param file      file to save to, should be writable and have a .mp4 extension
         * @param thread    thread to use for saving
         */
        public AutomaticRecordingTask(float waitSec, float lengthSec, File file,
                                      SaveMovieThread thread) {
            this.mWaitMs = (long) (waitSec * SECOND_MS);
            this.mWaitUs = (long) (waitSec * SECOND_US);
            this.mWaitFrames = (int) (waitSec * SECOND_FRAMES);
            this.mLenUs = (long) (lengthSec * SECOND_US);
            this.mFile = file;
            this.mBuf = thread.getBuffer();
            this.mHandler = thread.getHandler();
            boolean win = init();

            if (!win) {
                mCancelled = true;
                thread.sendCallback(file, false);
                return;
            }

            mHandler.sendTask(this, mWaitMs);
        }

        private boolean init() {
            if (mHandler == null) return false;

            synchronized (mBuf) {
                if (mBuf.empty()) return false;
                long nowUs = mBuf.getTimeUs(mBuf.prev(mBuf.end()));
                long startUs = nowUs + mWaitUs - mLenUs;
                mFirst = mBuf.findByTime(mBuf.begin(), mBuf.end(), startUs);
                mFirst = mBuf.findIFrame(mFirst);
                if (!mBuf.isIFrame(mFirst)) return false;
                long marginUs = mBuf.getDurationUs(mBuf.begin(), mFirst);
                if (marginUs < MIN_MARGIN_US + mWaitUs) return false;
                int marginFrames = mBuf.getNumFrames(mBuf.begin(), mFirst);
                if (marginFrames < MIN_MARGIN_FRAMES + mWaitFrames) return false;
            }

            return true;
        }

        @Override
        public void perform(SaveMovieThread thread) {
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
                    muxer = new MediaMuxer(mFile.getPath(), OUTPUT_FORMAT);
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
        public void terminate(SaveMovieThread thread) {
            synchronized (mLock) {
                if (mPerformed || mCancelled) return;
                mCancelled = true;
                mHandler.cancelTask(this);
            }
        }
    }

    public static class ManualRecordingTask implements Task {
        private static final float CHUNK_SEC = 1.f;
        private static final long CHUNK_MS = (long) (SECOND_MS * CHUNK_SEC);
        private static final long CHUNK_US = (long) (SECOND_US * CHUNK_SEC);
        private static final int CHUNK_FRAMES = (int) (SECOND_FRAMES * CHUNK_SEC);
        private final Object mLock = new Object();
        private final File mFile;
        private final CyclicBuffer mBuf;
        private final SaveMovieThreadHandler mHandler;
        private int mFirst;
        private MediaMuxer mMuxer;
        private int mTrack;
        private int mFramesWritten = 0;

        /**
         * Saves the contents of the buffer to the specified file. Recording is performed
         * indefinitely, until terminate() is called.
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

            mHandler.sendTask(this, CHUNK_MS);
        }

        private boolean init() {
            if (mHandler == null) return false;

            synchronized (mBuf) {
                if (mBuf.empty()) return false;
                int last = mBuf.prev(mBuf.end());
                mFirst = mBuf.findIFrame(last);
                if (!mBuf.isIFrame(mFirst)) return false;
                long marginUs = mBuf.getDurationUs(mBuf.begin(), mFirst);
                if (marginUs < MIN_MARGIN_US + CHUNK_US) return false;
                int marginFrames = mBuf.getNumFrames(mBuf.begin(), mFirst);
                if (marginFrames < MIN_MARGIN_FRAMES + CHUNK_FRAMES) return false;
            }

            try {
                mMuxer = new MediaMuxer(mFile.getPath(), OUTPUT_FORMAT);
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
            mHandler.sendTask(this, CHUNK_MS);
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
}
