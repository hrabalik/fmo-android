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
    static final int OUTPUT_FORMAT = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
    static final float MIN_MARGIN_SEC = 1.f;
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
    CyclicBuffer getBuffer() {
        return mBuf;
    }

    void sendCallback(File file, boolean success) {
        if (mCb != null) {
            mCb.saveCompleted(file, success);
        }
    }

    @Override
    protected SaveMovieThreadHandler makeHandler() {
        return new SaveMovieThreadHandler(this);
    }

    void writeFrames(int first, int last, MediaMuxer muxer, int track) {
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

}
