package cz.fmo.recording;

import android.media.MediaCodec;

import java.nio.ByteBuffer;

import cz.fmo.util.GenericThread;

public class EncodeThread extends GenericThread<EncodeThreadHandler> {
    private final Buffer mBuf;
    private final Callback mCb;
    private ByteBuffer mBufCache;
    private MediaCodec.BufferInfo mInfoCache;

    public EncodeThread(Buffer buf, Callback cb) {
        mBuf = buf;
        mCb = cb;
    }

    @Override
    protected EncodeThreadHandler makeHandler() {
        return new EncodeThreadHandler(this);
    }

    @Override
    protected void setup() {
        mBufCache = mBuf.getCache();
        mInfoCache = new MediaCodec.BufferInfo();
    }

    @Override
    protected void teardown() {

    }

    void encode() {
        //
    }

    public interface Callback {
        void encodeCompleted();
    }
}
