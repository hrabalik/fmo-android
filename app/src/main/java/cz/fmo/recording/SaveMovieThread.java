package cz.fmo.recording;

import android.media.MediaCodec;
import android.media.MediaMuxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import cz.fmo.util.GenericThread;

/**
 * Thread for saving videos from frames stored in a buffer. The buffer needs to be filled with
 * MPEG-4 frames and the exact format has to be specified using the CyclicBuffer.setFormat() method.
 */
public class SaveMovieThread extends GenericThread<SaveMovieThreadHandler> {
    private static final int OUTPUT_FORMAT = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
    private static final long SECONDS = 1000000;
    private static final long MOVIE_MIN_LENGTH = (3 * SECONDS) / 2;
    private static final long TIME_TO_SAVE = SECONDS;
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

    @Override
    protected SaveMovieThreadHandler makeHandler() {
        return new SaveMovieThreadHandler(this);
    }

    /**
     * Saves the latest lengthUs of frames to the specified file. If the start of the movie is less
     * than TIME_TO_SAVE from the start of the buffer, the method refuses to save the file. The
     * TIME_TO_SAVE requirement is a precaution to avoid overwriting data of a frame while it is
     * still being saved.
     *
     * @param file file to save to, should be writable and have a .mp4 extension
     */
    private void saveAutomatic(long lengthUs, File file) {
        boolean win = saveAutomaticImpl(lengthUs, file);
        mCb.saveCompleted(file, win);
    }

    private boolean saveAutomaticImpl(long lengthUs, File file) {
        int first;
        int last;

        synchronized (mBuf) {
            if (mBuf.empty()) return false;
            long endTime = mBuf.getTime(mBuf.prev(mBuf.end()));
            long startTime = endTime - lengthUs;
            first = mBuf.findByTime(mBuf.begin(), mBuf.end(), startTime);
            first = mBuf.findIFrame(first);
            last = mBuf.end();
            if (mBuf.getDuration(first, last) < MOVIE_MIN_LENGTH) return false;
            if (mBuf.getDuration(mBuf.begin(), first) < TIME_TO_SAVE) return false;
        }

        boolean win = true;
        MediaMuxer muxer = null;
        try {
            muxer = new MediaMuxer(file.getPath(), OUTPUT_FORMAT);
            int track = muxer.addTrack(mBuf.getFormat());
            muxer.start();

            for (int index = first; index != last; index = mBuf.next(index)) {
                mBuf.get(index, mBufCache, mInfoCache);
                muxer.writeSampleData(track, mBufCache, mInfoCache);
            }
        } catch (IOException e) {
            win = false;
        } finally {
            if (muxer != null) {
                muxer.stop();
                muxer.release();
            }
        }

        return win;
    }

    public interface Task {
        void perform(SaveMovieThread thread);
    }

    @SuppressWarnings("UnusedParameters")
    public interface Callback {
        void saveCompleted(File file, boolean success);
    }

    public static class AutomaticRecordingTask implements Task {
        private final Object mLock = new Object();
        private final long mWaitMs;
        private final long mVideoLenUs;
        private final File mFile;
        private final SaveMovieThreadHandler mHandler;
        private boolean mPerformed = false;
        private boolean mCancelled = false;

        public AutomaticRecordingTask(float waitSec, float videoLenSec, File file,
                                      SaveMovieThread thread) {
            this.mWaitMs = (long) (waitSec * 1e3f);
            this.mVideoLenUs = (long) (videoLenSec * 1e6f);
            this.mFile = file;
            this.mHandler = thread.getHandler();

            if (mHandler == null) return;
            mHandler.sendTask(this, mWaitMs);
        }

        @Override
        public void perform(SaveMovieThread thread) {
            synchronized (mLock) {
                if (mPerformed || mCancelled) return;
                mPerformed = true;
                thread.saveAutomatic(mVideoLenUs, mFile);
            }
        }

        public void cancel() {
            synchronized (mLock) {
                mHandler.cancelTask(this);
                mCancelled = true;
            }
        }
    }
}
