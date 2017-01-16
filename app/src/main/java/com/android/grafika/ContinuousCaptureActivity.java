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

import android.app.Activity;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;

import cz.fmo.R;
import cz.fmo.graphics.EGL;
import cz.fmo.graphics.Renderer;
import cz.fmo.recording.CyclicBuffer;
import cz.fmo.recording.CameraCapture;
import cz.fmo.recording.EncodeThread;
import cz.fmo.recording.SaveMovieThread;
import cz.fmo.util.FileManager;

/**
 * Demonstrates capturing video into a ring buffer.  When the "capture" button is clicked,
 * the buffered video is saved.
 * <p>
 * Capturing and storing raw frames would be slow and require lots of memory.  Instead, we
 * feed the frames into the video encoder and buffer the output.
 * <p>
 * Whenever we receive a new frame from the camera, our SurfaceTexture callback gets
 * notified.  That can happen on an arbitrary thread, so we use it to send a message
 * through our Handler.  That causes us to render the new frame to the display and to
 * our video encoder.
 */
public class ContinuousCaptureActivity extends Activity implements SurfaceHolder.Callback,
        Renderer.Callback {
    private static final float BUFFER_SIZE_SEC = 7.f;

    private final FileManager mFileMan = new FileManager(this);
    private EGL mEGL;
    private EGL.Surface mDisplaySurface;
    private Renderer mRenderer;
    private int mFrameNum;

    private CameraCapture mCapture;

    private File mOutputFile;
    private EGL.Surface mEncoderSurface;
    private boolean mFileSaveInProgress;

    private MainHandler mHandler;
    private float mSecondsOfVideo;
    private boolean mSurfaceCreated = false;
    private EncodeThread mEncodeThread;
    private SaveMovieThread mSaveMovieThread;

    /**
     * Adds a bit of extra stuff to the display just to give it flavor.
     */
    private static void drawExtra(int frameNum) {
        // We "draw" with the scissor rect and clear calls.  Note this uses window coordinates.
        int val = frameNum % 2;
        switch (val) {
            case 0:
                GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f);
                break;
            case 1:
                GLES20.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
                break;
        }

        //int xpos = (int) (width * ((frameNum % 100) / 100.0f));
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(0, 0, 128, 128);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_continuous_capture);

        getSurfaceHolder().addCallback(this);

        mHandler = new MainHandler(this);
        mHandler.blinkText(1500);

        mOutputFile = mFileMan.open("continuous-capture.mp4");
        mSecondsOfVideo = 0.0f;
        updateControls();
    }

    private SurfaceHolder getSurfaceHolder() {
        SurfaceView sv = (SurfaceView) findViewById(R.id.continuousCapture_surfaceView);
        return sv.getHolder();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mSurfaceCreated) {
            onResumeImpl();
        }
    }

    private void onResumeImpl() {
        // Set up everything that requires an EGL context.
        //
        // We had to wait until we had a surface because you can't make an EGL context current
        // without one, and creating a temporary 1x1 pbuffer is a waste of time.
        //
        // The display surface that we use for the SurfaceView, and the encoder surface we
        // use for video, use the same EGL context.
        mEGL = new EGL();
        mDisplaySurface = mEGL.makeSurface(getSurfaceHolder().getSurface());
        mDisplaySurface.makeCurrent();

        mRenderer = new Renderer(this);
        mCapture = new CameraCapture(new CameraCapture.Callback() {
            public void onCameraReady() {
            }
        });
        mCapture.start(mRenderer.getInputTexture());

        // TODO: adjust bit rate based on frame rate?
        // TODO: adjust video width/height based on what we're getting from the camera preview?
        //       (can we guarantee that camera preview size is compatible with AVC video encoder?)
        CyclicBuffer buf = new CyclicBuffer(mCapture.getBitRate(), mCapture.getFrameRate(), BUFFER_SIZE_SEC);
        mEncodeThread = new EncodeThread(mCapture.getMediaFormat(), buf, mHandler);
        mEncodeThread.start();
        mSaveMovieThread = new SaveMovieThread(buf, mHandler);
        mSaveMovieThread.start();

        mEncoderSurface = mEGL.makeSurface(mEncodeThread.getInputSurface());

        updateControls();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mEncoderSurface != null) {
            mEncoderSurface.release();
            mEncoderSurface = null;
        }

        mEncodeThread.getHandler().sendKill();
        mSaveMovieThread.getHandler().sendKill();
        try {
            mEncodeThread.join();
            mSaveMovieThread.join();
        } catch (InterruptedException ie) {
            throw new RuntimeException("Interrupted");
        }
        mEncodeThread = null;
        mSaveMovieThread = null;

        if (mCapture != null) {
            mCapture.release();
            mCapture = null;
        }
        if (mRenderer != null) {
            mRenderer.release();
            mRenderer = null;
        }
        if (mDisplaySurface != null) {
            mDisplaySurface.release();
            mDisplaySurface = null;
        }
        if (mEGL != null) {
            mEGL.release();
            mEGL = null;
        }
        Log.d("onPause() done");
    }

    /**
     * Updates the current state of the controls.
     */
    private void updateControls() {
        String str = getString(R.string.secondsOfVideo, mSecondsOfVideo);
        TextView tv = (TextView) findViewById(R.id.capturedVideoDesc_text);
        tv.setText(str);

        boolean wantEnabled = (mEncodeThread != null) && !mFileSaveInProgress;
        Button button = (Button) findViewById(R.id.capture_button);
        if (button.isEnabled() != wantEnabled) {
            Log.d("setting enabled = " + wantEnabled);
            button.setEnabled(wantEnabled);
        }
    }

    /**
     * Handles onClick for "capture" button.
     */
    public void clickCapture(@SuppressWarnings("UnusedParameters") View unused) {
        Log.d("capture");
        if (mFileSaveInProgress) {
            Log.w("HEY: file save is already in progress");
            return;
        }

        // The button is disabled in onCreate(), and not enabled until the encoder and output
        // surface is ready, so it shouldn't be possible to get here with a null mCircEncoder.
        mFileSaveInProgress = true;
        updateControls();
        TextView tv = (TextView) findViewById(R.id.recording_text);
        String str = getString(R.string.nowSaving);
        tv.setText(str);

        mSaveMovieThread.getHandler().sendSave(mOutputFile);
    }

    /**
     * The file save has completed.  We can resume recording.
     */
    private void fileSaveComplete(int status) {
        Log.d("fileSaveComplete " + status);
        if (!mFileSaveInProgress) {
            throw new RuntimeException("WEIRD: got fileSaveCmplete when not in progress");
        }
        mFileSaveInProgress = false;
        updateControls();
        TextView tv = (TextView) findViewById(R.id.recording_text);
        String str = getString(R.string.nowRecording);
        tv.setText(str);

        if (status == 0) {
            str = getString(R.string.recordingSucceeded);
        } else {
            str = getString(R.string.recordingFailed, status);
        }
        Toast toast = Toast.makeText(this, str, Toast.LENGTH_SHORT);
        toast.show();
    }

    /**
     * Updates the buffer status UI.
     */
    private void updateBufferStatus(long durationUsec) {
        mSecondsOfVideo = durationUsec / 1000000.0f;
        updateControls();
    }


    @Override   // SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder != getSurfaceHolder()) throw new RuntimeException("Unexpected callback");
        Log.d("surfaceCreated holder=" + holder);
        mSurfaceCreated = true;
        onResumeImpl();
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d("surfaceChanged fmt=" + format + " size=" + width + "x" + height +
                " holder=" + holder);
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d("surfaceDestroyed holder=" + holder);
    }

    @Override   // Renderer.Callback; runs on arbitrary thread
    public void onFrameAvailable() {
        mHandler.frameAvailable();
    }

    /**
     * Draws a frame onto the SurfaceView and the encoder surface.
     * <p>
     * This will be called whenever we get a new preview frame from the camera.  This runs
     * on the UI thread, which ordinarily isn't a great idea -- you really want heavy work
     * to be on a different thread -- but we're really just throwing a few things at the GPU.
     * The upside is that we don't have to worry about managing state changes between threads.
     * <p>
     * If there was a pending frame available notification when we shut down, we might get
     * here after onPause().
     */
    private void processFrame() {
        if (mEGL == null) {
            Log.d("Skipping processFrame after shutdown");
            return;
        }

        // Latch the next frame from the camera.
        mDisplaySurface.makeCurrent();

        // Fill the SurfaceView with it.
        SurfaceView sv = (SurfaceView) findViewById(R.id.continuousCapture_surfaceView);
        int viewWidth = sv.getWidth();
        int viewHeight = sv.getHeight();
        GLES20.glViewport(0, 0, viewWidth, viewHeight);
        mRenderer.drawRectangle();
        drawExtra(mFrameNum);
        mDisplaySurface.swapBuffers();

        // Send it to the video encoder.
        mEncoderSurface.makeCurrent();
        GLES20.glViewport(0, 0, mCapture.getWidth(), mCapture.getHeight());
        mRenderer.drawRectangle();
        drawExtra(mFrameNum);
        mEncodeThread.getHandler().sendFlush();
        mEncoderSurface.presentationTime(mRenderer.getTimestamp());
        mEncoderSurface.swapBuffers();

        mFrameNum++;
    }

    /**
     * Custom message handler for main UI thread.
     * <p>
     * Used to handle camera preview "frame available" notifications, and implement the
     * blinking "recording" text.  Receives callback messages from the encoder thread.
     */
    private static class MainHandler extends Handler implements SaveMovieThread.Callback,
            EncodeThread.Callback {
        private static final int BLINK_TEXT = 0;
        private static final int FRAME_AVAILABLE = 1;
        private static final int SAVE_COMPLETED = 2;
        private static final int FLUSH_COMPLETED = 3;

        private final WeakReference<ContinuousCaptureActivity> mWeakActivity;

        MainHandler(ContinuousCaptureActivity activity) {
            mWeakActivity = new WeakReference<>(activity);
        }

        void frameAvailable() {
            sendEmptyMessage(MainHandler.FRAME_AVAILABLE);
        }

        void blinkText(long delayMs) {
            sendEmptyMessageDelayed(BLINK_TEXT, delayMs);
        }

        @Override
        public void flushCompleted(EncodeThread thread) {
            long duration = thread.getBufferContentsDuration();
            sendMessage(obtainMessage(FLUSH_COMPLETED, (int) (duration >> 32), (int) duration));
        }

        @Override
        public void saveCompleted(String filename, boolean success) {
            int status = success ? 0 : 1;
            sendMessage(obtainMessage(SAVE_COMPLETED, status, 0, null));
        }

        @Override
        public void handleMessage(Message msg) {
            ContinuousCaptureActivity activity = mWeakActivity.get();
            if (activity == null) {
                Log.d("Got message for dead activity");
                return;
            }

            switch (msg.what) {
                case BLINK_TEXT: {
                    TextView tv = (TextView) activity.findViewById(R.id.recording_text);

                    // Attempting to make it blink by using setEnabled() doesn't work --
                    // it just changes the color.  We want to change the visibility.
                    int visibility = tv.getVisibility();
                    if (visibility == View.VISIBLE) {
                        visibility = View.INVISIBLE;
                    } else {
                        visibility = View.VISIBLE;
                    }
                    tv.setVisibility(visibility);

                    int delay = (visibility == View.VISIBLE) ? 1000 : 200;
                    blinkText(delay);
                    break;
                }
                case FRAME_AVAILABLE: {
                    activity.processFrame();
                    break;
                }
                case SAVE_COMPLETED: {
                    activity.fileSaveComplete(msg.arg1);
                    break;
                }
                case FLUSH_COMPLETED: {
                    long duration = (((long) msg.arg1) << 32) |
                            (((long) msg.arg2) & 0xffffffffL);
                    activity.updateBufferStatus(duration);
                    break;
                }
                default:
                    throw new RuntimeException("Unknown message " + msg.what);
            }
        }
    }
}
