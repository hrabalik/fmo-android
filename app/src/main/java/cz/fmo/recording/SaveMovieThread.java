package cz.fmo.recording;

import android.media.MediaCodec;
import android.media.MediaMuxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import cz.fmo.util.GenericThread;

public class SaveMovieThread extends GenericThread<SaveMovieThreadHandler> {
    private static final int OUTPUT_FORMAT = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
    private static final long SECONDS = 1000000;
    private static final long MOVIE_LENGTH = 3 * SECONDS;
    private static final long MOVIE_MIN_LENGTH = 2 * SECONDS;
    private static final long TIME_TO_SAVE = SECONDS / 2;
    private final Buffer mBuf;
    private final ByteBuffer mBufCache;
    private final MediaCodec.BufferInfo mInfoCache;
    private final Callback mCb;

    public SaveMovieThread(Buffer buf, Callback cb) {
        mBuf = buf;
        mBufCache = buf.getCache();
        mInfoCache = new MediaCodec.BufferInfo();
        mCb = cb;
    }

    protected android.os.Handler makeHandler() {
        return new SaveMovieThreadHandler(this);
    }

    public void work(File file) {
        boolean win = save(file);
        mCb.saveCompleted(file.getName(), win);
    }

    private boolean save(File file) {
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
            muxer.start();
            int track = muxer.addTrack(mBuf.getFormat());
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
