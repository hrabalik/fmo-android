package com.android.grafika;

import android.media.MediaCodec;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import cz.fmo.recording.Buffer;

/**
 * Object that encapsulates the encoder thread.
 * <p>
 * We want to sleep until there's work to do.  We don't actually know when a new frame
 * arrives at the encoder, because the other thread is sending frames directly to the
 * input surface.  We will see data appear at the decoder output, so we can either use
 * an infinite timeout on dequeueOutputBuffer() or wait() on an object and require the
 * calling app wake us.  It's very useful to have all of the buffer management local to
 * this thread -- avoids synchronization -- so we want to do the file muxing in here.
 * So, it's best to sleep on an object and do something appropriate when awakened.
 * <p>
 * This class does not manage the MediaCodec encoder startup/shutdown.  The encoder
 * should be fully started before the thread is created, and not shut down until this
 * thread has been joined.
 */
public class EncoderThread extends Thread {
    /**
     * Saves the encoder output to a .mp4 file.
     * <p>
     * We'll drain the encoder to get any lingering data, but we're not going to shut
     * the encoder down or use other tricks to try to "flush" the encoder.  This may
     * mean we miss the last couple of submitted frames if they're still working their
     * way through.
     * <p>
     * We may want to reset the buffer after this -- if they hit "capture" again right
     * away they'll end up saving video with a gap where we paused to write the file.
     */
    private static final long SECONDS = 1000000;
    private static final long MOVIE_LENGTH = 3 * SECONDS;
    private static final long MOVIE_LENGTH_MIN = 2 * SECONDS;
    private static final long TIME_TO_SAVE_MIN = SECONDS / 2;
    private final MediaCodec mEncoder;
    private final MediaCodec.BufferInfo mInfoCache;
    private final Buffer mBuf;
    private final ByteBuffer mBufCache;
    private final CircularEncoder.Callback mCallback;
    private final Object mLock = new Object();
    private EncoderHandler mHandler;
    private int mFrameNum;
    private volatile boolean mReady = false;

    public EncoderThread(MediaCodec mediaCodec, Buffer encBuffer,
                         CircularEncoder.Callback callback) {
        mEncoder = mediaCodec;
        mBuf = encBuffer;
        mBufCache = mBuf.getCache();
        mCallback = callback;

        mInfoCache = new MediaCodec.BufferInfo();
    }

    /**
     * Thread entry point.
     * <p>
     * Prepares the Looper, Handler, and signals anybody watching that we're ready to go.
     */
    @Override
    public void run() {
        Looper.prepare();
        mHandler = new EncoderHandler(this);    // must create on encoder thread
        Log.d("encoder thread ready");
        synchronized (mLock) {
            mReady = true;
            mLock.notify();    // signal waitUntilReady()
        }

        Looper.loop();

        synchronized (mLock) {
            mReady = false;
            mHandler = null;
        }
        Log.d("looper quit");
    }

    /**
     * Waits until the encoder thread is ready to receive messages.
     * <p>
     * Call from non-encoder thread.
     */
    public void waitUntilReady() {
        synchronized (mLock) {
            while (!mReady) {
                try {
                    mLock.wait();
                } catch (InterruptedException ie) { /* not expected */ }
            }
        }
    }

    /**
     * Returns the Handler used to send messages to the encoder thread.
     */
    public EncoderHandler getHandler() {
        synchronized (mLock) {
            // Confirm ready state.
            if (!mReady) {
                throw new RuntimeException("not ready");
            }
        }
        return mHandler;
    }

    /**
     * Drains all pending output from the decoder, and adds it to the circular buffer.
     */
    private void drainEncoder() {
        final int TIMEOUT_USEC = 0;     // no timeout -- check for buffers, bail if none

        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        while (true) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mInfoCache, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                break;
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Should happen before receiving buffers, and should only happen once.
                // The MediaFormat contains the csd-0 and csd-1 keys, which we'll need
                // for MediaMuxer.  It's unclear what else MediaMuxer might want, so
                // rather than extract the codec-specific data and reconstruct a new
                // MediaFormat later, we just grab it here and keep it around.
                mBuf.setFormat(mEncoder.getOutputFormat());
                Log.d("encoder output format changed: " + mBuf.getFormat());
            } else if (encoderStatus < 0) {
                Log.w("unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus);
                // let's ignore it
            } else {
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                if ((mInfoCache.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out when we got the
                    // INFO_OUTPUT_FORMAT_CHANGED status.  The MediaMuxer won't accept
                    // a single big blob -- it wants separate csd-0/csd-1 chunks --
                    // so simply saving this off won't work.
                    //if (VERBOSE) Log.d("ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mInfoCache.size = 0;
                }

                if (mInfoCache.size != 0) {
                    //// adjust the ByteBuffer values to match BufferInfo (not needed?)
                    //encodedData.position(mInfoCache.offset);
                    //encodedData.limit(mInfoCache.offset + mInfoCache.size);
                    //
                    //mBuf.add(encodedData, mInfoCache.flags,
                    //        mInfoCache.presentationTimeUs);
                    synchronized (mBuf) {
                        mBuf.pushBack(encodedData, mInfoCache);
                    }

                    //if (VERBOSE) {
                    //    Log.d("sent " + mInfoCache.size + " bytes to muxer, ts=" +
                    //            mInfoCache.presentationTimeUs);
                    //}
                }

                mEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((mInfoCache.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.w("reached end of stream unexpectedly");
                    break;      // out of while
                }
            }
        }
    }

    /**
     * Drains the encoder output.
     * <p>
     * See notes for {@link CircularEncoder#frameAvailableSoon()}.
     */
    private void frameAvailableSoon() {
        //if (VERBOSE) Log.d("frameAvailableSoon");
        drainEncoder();

        mFrameNum++;
        if ((mFrameNum % 10) == 0) {        // TODO: should base off frame rate or clock?
            synchronized (mBuf) {
                long duration = mBuf.getDuration(mBuf.begin(), mBuf.end());
                mCallback.bufferStatus(duration);
            }
        }
    }

    private void saveVideo(File outputFile) {
        int first, last;
        synchronized (mBuf) {
            if (mBuf.empty()) {
                Log.w("Buffer is empty");
                mCallback.fileSaveComplete(1);
                return;
            }

            long endTime = mBuf.getTime(mBuf.prev(mBuf.end()));
            long startTime = endTime - MOVIE_LENGTH;
            first = mBuf.findByTime(mBuf.begin(), mBuf.end(), startTime);
            first = mBuf.findIFrame(first);
            last = mBuf.end();

            if (mBuf.getDuration(first, last) < MOVIE_LENGTH_MIN) {
                Log.w("Movie is too short");
                mCallback.fileSaveComplete(1);
                return;
            }
            if (mBuf.getDuration(mBuf.begin(), first) < TIME_TO_SAVE_MIN) {
                Log.w("Not enough time to save");
                mCallback.fileSaveComplete(1);
                return;
            }
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        MediaMuxer muxer = null;
        int result = -1;
        try {
            muxer = new MediaMuxer(outputFile.getPath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int videoTrack = muxer.addTrack(mBuf.getFormat());
            muxer.start();

            for (int index = first; index != last; index = mBuf.next(index)) {
                mBuf.get(index, mBufCache, info);
                //if (VERBOSE) {
                //    Log.d("SAVE " + index + " flags=0x" + Integer.toHexString(info.flags));
                //}
                muxer.writeSampleData(videoTrack, mBufCache, info);
            }
            result = 0;
        } catch (IOException ioe) {
            Log.w("muxer failed", ioe);
            result = 2;
        } finally {
            if (muxer != null) {
                muxer.stop();
                muxer.release();
            }
        }

        //if (VERBOSE) {
        //    Log.d("muxer stopped, result=" + result);
        //}
        mCallback.fileSaveComplete(result);
    }

    /**
     * Tells the Looper to quit.
     */
    private void shutdown() {
        //if (VERBOSE) Log.d("shutdown");

        Looper looper = Looper.myLooper();
        if (looper != null) {
            looper.quit();
        }
    }

    /**
     * Handler for EncoderThread.  Used for messages sent from the UI thread (or whatever
     * is driving the encoder) to the encoder thread.
     * <p>
     * The object is created on the encoder thread.
     */
    public static class EncoderHandler extends Handler {
        public static final int MSG_FRAME_AVAILABLE_SOON = 1;
        public static final int MSG_SAVE_VIDEO = 2;
        public static final int MSG_SHUTDOWN = 3;

        // This shouldn't need to be a weak ref, since we'll go away when the Looper quits,
        // but no real harm in it.
        private final WeakReference<EncoderThread> mWeakEncoderThread;

        /**
         * Constructor.  Instantiate object from encoder thread.
         */
        public EncoderHandler(EncoderThread et) {
            mWeakEncoderThread = new WeakReference<>(et);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message msg) {
            int what = msg.what;
            //if (VERBOSE) {
            //    Log.v("EncoderHandler: what=" + what);
            //}

            EncoderThread encoderThread = mWeakEncoderThread.get();
            if (encoderThread == null) {
                Log.w("EncoderHandler.handleMessage: weak ref is null");
                return;
            }

            switch (what) {
                case MSG_FRAME_AVAILABLE_SOON:
                    encoderThread.frameAvailableSoon();
                    break;
                case MSG_SAVE_VIDEO:
                    encoderThread.saveVideo((File) msg.obj);
                    break;
                case MSG_SHUTDOWN:
                    encoderThread.shutdown();
                    break;
                default:
                    throw new RuntimeException("unknown message " + what);
            }
        }
    }
}
