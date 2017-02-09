package cz.fmo.recording;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

import cz.fmo.util.GenericThread;

/**
 * A separate thread to manage an encoder and save its output into a buffer.
 */
public class EncodeThread extends GenericThread<EncodeThreadHandler> {
    private final CyclicBuffer mBuf;
    private final Callback mCb;
    private final MediaCodec.BufferInfo mInfo;
    private final MediaCodec mCodec;
    private final Surface mInputSurface;
    private boolean mReleased = false;

    public EncodeThread(MediaFormat format, CyclicBuffer buf, Callback cb) {
        super("EncodeThread");
        mBuf = buf;
        mCb = cb;
        mInfo = new MediaCodec.BufferInfo();

        try {
            mCodec = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            throw new RuntimeException("Cannot create codec");
        }

        mCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mCodec.createInputSurface();
        mCodec.start();
    }

    private void release() {
        if (mReleased) return;
        mReleased = true;
        mInputSurface.release();
        mCodec.stop();
        mCodec.release();
    }

    /**
     * Writes all available output frames from the encoder into the buffer. Non-blocking: if there
     * are no frames in the output of the encoder, no work is done. Always calls the
     * flushCompleted() callback.
     */
    void flush() {
        //noinspection deprecation
        ByteBuffer[] buffers = mCodec.getOutputBuffers();
        while (true) {
            int status = mCodec.dequeueOutputBuffer(mInfo, 0);
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) break;
            if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mBuf.setFormat(mCodec.getOutputFormat());
                continue;
            }
            if (status < 0) continue;
            ByteBuffer buffer = buffers[status];
            if (mInfo.size != 0 && (mInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                synchronized (mBuf) {
                    mBuf.pushBack(buffer, mInfo);
                }
            }
            mCodec.releaseOutputBuffer(status, false);
            if ((mInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
        }
        mCb.flushCompleted(this);
    }

    @Override
    protected EncodeThreadHandler makeHandler() {
        return new EncodeThreadHandler(this);
    }

    @Override
    protected void teardown() {
        release();
    }

    public Surface getInputSurface() {
        return mInputSurface;
    }

    /**
     * @return The time between the first and the last stored frame, in microseconds.
     */
    public long getBufferContentsDuration() {
        long duration;
        synchronized (mBuf) {
            duration = mBuf.getDuration(mBuf.begin(), mBuf.end());
        }
        return duration;
    }

    public interface Callback {
        void flushCompleted(EncodeThread thread);
    }
}
