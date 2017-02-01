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
    private static final long MOVIE_LENGTH = 3 * SECONDS;
    private static final long MOVIE_MIN_LENGTH = 2 * SECONDS;
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
     * Saves the latest MOVIE_LENGTH of frames to the specified file. If the start of the movie
     * is less than TIME_TO_SAVE from the start of the buffer, the method refuses to save the file.
     * The TIME_TO_SAVE requirement is a precaution to avoid overwriting data of a frame while it is
     * still being saved.
     *
     * @param file file to save to, should be writable and have a .mp4 extension
     */
    void save(File file) {
        boolean win = saveImpl(file);
        mCb.saveCompleted(file.getName(), win);
    }

    private boolean saveImpl(File file) {
        int first;
        int last;

        synchronized (mBuf) {
            if (mBuf.empty()) return false;
            long endTime = mBuf.getTime(mBuf.prev(mBuf.end()));
            long startTime = endTime - MOVIE_LENGTH;
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

    public interface Callback {
        void saveCompleted(String filename, boolean success);
    }
}
