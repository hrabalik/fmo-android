/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.grafika;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.view.Surface;

import java.io.File;
import java.io.IOException;

import cz.fmo.CameraCapture;
import cz.fmo.recording.Buffer;
import cz.fmo.recording.SaveMovieThread;

/**
 * Encodes video in a fixed-size circular buffer.
 * <p>
 * The obvious way to do this would be to store each packet in its own buffer and hook it
 * into a linked list.  The trouble with this approach is that it requires constant
 * allocation, which means we'll be driving the GC to distraction as the frame rate and
 * bit rate increase.  Instead we create fixed-size pools for video data and metadata,
 * which requires a bit more work for us but avoids allocations in the steady state.
 * <p>
 * Video must always start with a sync frame (a/k/a key frame, a/k/a I-frame).  When the
 * circular buffer wraps around, we either need to delete all of the data between the frame at
 * the head of the list and the next sync frame, or have the file save function know that
 * it needs to scan forward for a sync frame before it can start saving data.
 * <p>
 * When we're told to save a snapshot, we create a MediaMuxer, write all the frames out,
 * and then go back to what we were doing.
 */
class CircularEncoder implements SaveMovieThread.Callback {
    private static final boolean VERBOSE = false;

    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int IFRAME_INTERVAL = 1;           // sync frame every second
    private final Callback mCb;
    private EncoderThread mEncoderThread;
    private SaveMovieThread mSaveMovieThread;
    private Surface mInputSurface;
    private MediaCodec mEncoder;

    /**
     * Configures encoder, and prepares the input Surface.
     *
     * @param desiredSpanSec How many seconds of video we want to have in our buffer at any time.
     */
    public CircularEncoder(CameraCapture capture, float desiredSpanSec, Callback cb) throws IOException {
        // The goal is to size the buffer so that we can accumulate N seconds worth of video,
        // where N is passed in as "desiredSpanSec".  If the codec generates data at roughly
        // the requested bit rate, we can compute it as time * bitRate / bitsPerByte.
        //
        // Sync frames will appear every (frameRate * IFRAME_INTERVAL) frames.  If the frame
        // rate is higher or lower than expected, various calculations may not work out right.
        //
        // Since we have to start muxing from a sync frame, we want to ensure that there's
        // room for at least one full GOP in the buffer, preferrably two.
        if (desiredSpanSec < IFRAME_INTERVAL * 2) {
            throw new RuntimeException("Requested time span is too short: " + desiredSpanSec +
                    " vs. " + (IFRAME_INTERVAL * 2));
        }
        Buffer encBuffer = new Buffer(capture.getBitRate(), capture.getFrameRate(), desiredSpanSec);
        MediaFormat format = capture.getMediaFormat();

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();

        // Start the encoder thread last.  That way we're sure it can see all of the state
        // we've initialized.
        mEncoderThread = new EncoderThread(mEncoder, encBuffer, cb);
        mEncoderThread.start();
        mEncoderThread.waitUntilReady();

        mSaveMovieThread = new SaveMovieThread(encBuffer, this);
        mSaveMovieThread.start();
        mCb = cb;

        mEncoderThread.setPriority(Thread.MAX_PRIORITY);
        mSaveMovieThread.setPriority(Thread.MIN_PRIORITY);
    }

    /**
     * Returns the encoder's input surface.
     */
    public Surface getInputSurface() {
        return mInputSurface;
    }

    /**
     * Shuts down the encoder thread, and releases encoder resources.
     * <p>
     * Does not return until the encoder thread has stopped.
     */
    public void shutdown() {
        if (VERBOSE) Log.d("releasing encoder objects");

        Handler handler = mEncoderThread.getHandler();
        handler.sendMessage(handler.obtainMessage(EncoderThread.EncoderHandler.MSG_SHUTDOWN));

        mSaveMovieThread.getHandler().sendKill();

        try {
            mEncoderThread.join();
            mSaveMovieThread.join();
        } catch (InterruptedException ie) {
            throw new RuntimeException("Interrupted");
        }

        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
    }

    /**
     * Notifies the encoder thread that a new frame will shortly be provided to the encoder.
     * <p>
     * There may or may not yet be data available from the encoder output.  The encoder
     * has a fair mount of latency due to processing, and it may want to accumulate a
     * few additional buffers before producing output.  We just need to drain it regularly
     * to avoid a situation where the producer gets wedged up because there's no room for
     * additional frames.
     * <p>
     * If the caller sends the frame and then notifies us, it could get wedged up.  If it
     * notifies us first and then sends the frame, we guarantee that the output buffers
     * were emptied, and it will be impossible for a single additional frame to block
     * indefinitely.
     */
    public void frameAvailableSoon() {
        Handler handler = mEncoderThread.getHandler();
        handler.sendMessage(handler.obtainMessage(
                EncoderThread.EncoderHandler.MSG_FRAME_AVAILABLE_SOON));
    }

    /**
     * Initiates saving the currently-buffered frames to the specified output file.  The
     * data will be written as a .mp4 file.  The call returns immediately.  When the file
     * save completes, the callback will be notified.
     * <p>
     * The file generation is performed on the encoder thread, which means we won't be
     * draining the output buffers while this runs.  It would be wise to stop submitting
     * frames during this time.
     */
    public void saveVideo(File outputFile) {
        mSaveMovieThread.getHandler().sendSave(outputFile);
    }

    @Override
    public void saveCompleted(String filename, boolean success) {
        mCb.fileSaveComplete(success ? 0 : 1);
    }

    /**
     * Callback function definitions.  CircularEncoder caller must provide one.
     */
    public interface Callback {
        /**
         * Called some time after saveVideo(), when all data has been written to the
         * output file.
         *
         * @param status Zero means success, nonzero indicates failure.
         */
        void fileSaveComplete(int status);

        /**
         * Called occasionally.
         *
         * @param totalTimeMsec Total length, in milliseconds, of buffered video.
         */
        void bufferStatus(long totalTimeMsec);
    }
}
