package cz.fmo.recording;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

import cz.fmo.CameraCapture;
import cz.fmo.util.GenericThread;

public class EncodeThread extends GenericThread<EncodeThreadHandler> {
    private final CameraCapture mCapture;
    private final Buffer mBuf;
    private final Callback mCb;
    private final MediaCodec.BufferInfo mInfo;
    private final MediaCodec mCodec;
    private final Surface mInputSurface;
    private boolean mReleased = false;

    public EncodeThread(CameraCapture capture, Buffer buf, Callback cb) {
        mCapture = capture;
        mBuf = buf;
        mCb = cb;
        mInfo = new MediaCodec.BufferInfo();

        try {
            mCodec = MediaCodec.createEncoderByType(capture.getMIMEType());
        } catch (IOException e) {
            throw new RuntimeException("Cannot create codec");
        }

        MediaFormat format = mCapture.getMediaFormat();
        mCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mCodec.createInputSurface();
        mCodec.start();
    }

    @Override
    protected EncodeThreadHandler makeHandler() {
        return new EncodeThreadHandler(this);
    }

    @Override
    protected void teardown() {
        release();
    }

    private void release() {
        if (mReleased) return;
        mReleased = true;
        mInputSurface.release();
        mCodec.stop();
        mCodec.release();
    }

    void drain() {
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
        mCb.drainCompleted();
    }

    public Surface getInputSurface() {
        return mInputSurface;
    }

    public interface Callback {
        void drainCompleted();
    }
}
