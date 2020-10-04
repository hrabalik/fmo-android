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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.opengl.GLES20;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import java.io.IOException;
import java.lang.ref.WeakReference;

import cz.fmo.Lib;
import cz.fmo.R;
import cz.fmo.data.Track;
import cz.fmo.data.TrackSet;
import cz.fmo.graphics.EGL;
import cz.fmo.util.Config;
import cz.fmo.util.FileManager;

/**
 * Play a movie from a file on disk.  Output goes to a SurfaceView.
 * <p>
 * This is very similar to PlayMovieActivity, but the output goes to a SurfaceView instead of
 * a TextureView.  There are some important differences:
 * <ul>
 * <li> TextureViews behave like normal views.  SurfaceViews don't.  A SurfaceView has
 * a transparent "hole" in the UI through which an independent Surface layer can
 * be seen.  This Surface is sent directly to the system graphics compositor.
 * <li> Because the video is being composited with the UI by the system compositor,
 * rather than the application, it can often be done more efficiently (e.g. using
 * a hardware composer "overlay").  This can lead to significant battery savings
 * when playing a long movie.
 * <li> On the other hand, the TextureView contents can be freely scaled and rotated
 * with a simple matrix.  The SurfaceView output is limited to scaling, and it's
 * more awkward to do.
 * <li> DRM-protected content can't be touched by the app (or even the system compositor).
 * We have to point the MediaCodec decoder at a Surface that is composited by a
 * hardware composer overlay.  The only way to do the app side of this is with
 * SurfaceView.
 * </ul>
 * <p>
 * The MediaCodec decoder requests buffers from the Surface, passing the video dimensions
 * in as arguments.  The Surface provides buffers with a matching size, which means
 * the video data will completely cover the Surface.  As a result, there's no need to
 * use SurfaceHolder#setFixedSize() to set the dimensions.  The hardware scaler will scale
 * the video to match the view size, so if we want to preserve the correct aspect ratio
 * we need to adjust the View layout.  We can use our custom AspectFrameLayout for this.
 * <p>)
 * The actual playback of the video -- sending frames to a Surface -- is the same for
 * TextureView and SurfaceView.
 */
public class PlayMovieSurfaceActivity extends Activity implements OnItemSelectedListener,
        SurfaceHolder.Callback, MoviePlayer.PlayerFeedback {
    private final FileManager mFileMan = new FileManager(this);
    private SurfaceView mSurfaceView;
    private SurfaceView mSurfaceTrack;
    private String[] mMovieFiles;
    private int mSelectedMovie;
    private boolean mShowStopLabel;
    private MoviePlayer.PlayTask mPlayTask;
    private boolean mSurfaceHolderReady = false;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_movie_surface);

        mSurfaceView = findViewById(R.id.playMovie_surface);
        mSurfaceView.getHolder().addCallback(this);
        mHandler = new Handler(this);
        mSurfaceTrack = findViewById(R.id.playTracks_surface);
        mSurfaceTrack.setZOrderOnTop(true);
        mSurfaceTrack.getHolder().addCallback(this);
        mSurfaceTrack.getHolder().setFormat(PixelFormat.TRANSPARENT);
        // Populate file-selection spinner.
        Spinner spinner = findViewById(R.id.playMovieFile_spinner);
        // Need to create one of these fancy ArrayAdapter thingies, and specify the generic layout
        // for the widget itself.
        mMovieFiles = mFileMan.listMP4();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, mMovieFiles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner.
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
        updateControls();
    }

    @Override
    protected void onResume() {
        Log.d("PlayMovieSurfaceActivity onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d("PlayMovieSurfaceActivity onPause");
        super.onPause();
        // We're not keeping track of the state in static fields, so we need to shut the
        // playback down.  Ideally we'd preserve the state so that the player would continue
        // after a device rotation.
        //
        // We want to be sure that the player won't continue to send frames after we pause,
        // because we're tearing the view down.  So we wait for it to stop here.
        if (mPlayTask != null) {
            stopPlayback();
            mPlayTask.waitForStop();
        }
        Lib.detectionStop();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // There's a short delay between the start of the activity and the initialization
        // of the SurfaceHolder that backs the SurfaceView.  We don't want to try to
        // send a video stream to the SurfaceView before it has initialized, so we disable
        // the "play" button until this callback fires.
        Log.d("surfaceCreated");
        mSurfaceHolderReady = true;
        updateControls();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // ignore
        Log.d("surfaceChanged fmt=" + format + " size=" + width + "x" + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // ignore
        Log.d("Surface destroyed");
    }

    /*
     * Called when the movie Spinner gets touched.
     */
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        Spinner spinner = (Spinner) parent;
        mSelectedMovie = spinner.getSelectedItemPosition();

        Log.d("onItemSelected: " + mSelectedMovie + " '" + mMovieFiles[mSelectedMovie] + "'");
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    /**
     * onClick handler for "play"/"stop" button.
     */
    public void clickPlayStop(@SuppressWarnings("UnusedParameters") View unused) {
        if (mShowStopLabel) {
            Log.d("stopping movie");
            stopPlayback();
            // Don't update the controls here -- let the task thread do it after the movie has
            // actually stopped.
            //mShowStopLabel = false;
            //updateControls();
        } else {
            if (mPlayTask != null) {
                Log.w("movie already playing");
                return;
            }

            Log.d("starting movie");
            SpeedControlCallback callback = new SpeedControlCallback();
            SurfaceHolder holder = mSurfaceView.getHolder();
            Surface surface = holder.getSurface();

            // Don't leave the last frame of the previous video hanging on the screen.
            // Looks weird if the aspect ratio changes.
            clearSurface(surface);

            MoviePlayer player;
            try {
                player = new MoviePlayer(mFileMan.open(mMovieFiles[mSelectedMovie]), surface,
                        callback, mHandler);
                mHandler.setVideoHeight(player.getVideoHeight());
                mHandler.setVideoWidth(player.getVideoWidth());
                Config mConfig = new Config(this);
                TrackSet.getInstance().setConfig(mConfig);
                Lib.detectionStart(player.getVideoWidth(), player.getVideoHeight(), mConfig.procRes, mConfig.gray, mHandler);

            } catch (IOException ioe) {
                Log.e("Unable to play movie", ioe);
                surface.release();
                return;
            }

            mPlayTask = new MoviePlayer.PlayTask(player, this, true);

            mShowStopLabel = true;
            updateControls();
            mPlayTask.execute();
        }
    }

    /**
     * Requests stoppage if a movie is currently playing.
     */
    private void stopPlayback() {
        if (mPlayTask != null) {
            mPlayTask.requestStop();
        }
    }

    @Override   // MoviePlayer.PlayerFeedback
    public void playbackStopped() {
        Log.d("playback stopped");
        mShowStopLabel = false;
        mPlayTask = null;
        updateControls();
    }

    /**
     * Updates the on-screen controls to reflect the current state of the app.
     */
    private void updateControls() {
        Button play = findViewById(R.id.play_stop_button);
        if (mShowStopLabel) {
            play.setText(R.string.stop_button_text);
        } else {
            play.setText(R.string.play_button_text);
        }
        play.setEnabled(mSurfaceHolderReady);
    }

    /**
     * Clears the playback surface to black.
     */
    private void clearSurface(Surface surface) {
        // We need to do this with OpenGL ES (*not* Canvas -- the "software render" bits
        // are sticky).  We can't stay connected to the Surface after we're done because
        // that'd prevent the video encoder from attaching.
        //
        // If the Surface is resized to be larger, the new portions will be black, so
        // clearing to something other than black may look weird unless we do the clear
        // post-resize.
        EGL egl = new EGL();
        EGL.Surface win = egl.makeSurface(surface);
        win.makeCurrent();
        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        win.swapBuffers();
        win.release();
        egl.release();
    }

    private static class Handler extends android.os.Handler implements Lib.Callback, PlayMovieDetectionCallback {
        private final WeakReference<PlayMovieSurfaceActivity> mActivity;
        private int canvasWidth, canvasHeight;
        private Canvas canvas;
        private Paint p;
        private int videoWidth, videoHeight;

        Handler(@NonNull PlayMovieSurfaceActivity activity) {
            mActivity = new WeakReference<>(activity);
            TrackSet.getInstance().clear();
            p = new Paint();
        }

        public void setVideoHeight(int videoHeight) {
            this.videoHeight = videoHeight;
        }

        public void setVideoWidth(int videoWidth) {
            this.videoWidth = videoWidth;
        }

        @Override
        public void onEncodedFrame(byte[] dataYUV420SP) {
            try {
                Lib.detectionFrame(dataYUV420SP);
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }

        @Override
        public void log(String message) {
            System.out.println(message);
        }

        @Override
        public void onObjectsDetected(Lib.Detection[] detections) {
            System.out.println("detections:");
            for (Lib.Detection detection : detections) {
                System.out.println(detection);
            }
            TrackSet set = TrackSet.getInstance();
            set.addDetections(detections, this.videoWidth, this.videoHeight);
            PlayMovieSurfaceActivity activity = mActivity.get();
            if (activity == null) {
                return;
            }
            if (activity.mSurfaceHolderReady) {
                SurfaceHolder surfaceHolder = activity.mSurfaceTrack.getHolder();
                canvas = surfaceHolder.lockCanvas();
                if (canvas == null) {
                    return;
                }
                if (canvasWidth == 0 || canvasHeight == 0) {
                    canvasWidth = canvas.getWidth();
                    canvasHeight = canvas.getHeight();
                }
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                drawAllTracks(canvas, set);
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }

        private void drawAllTracks(Canvas canvas, TrackSet set) {
            for (Track t : set.getTracks()) {
                t.updateColor();
                Lib.Detection pre = t.getLatest();
                cz.fmo.util.Color.RGBA r = t.getColor();
                int c = Color.argb(255, Math.round(r.rgba[0] * 255), Math.round(r.rgba[1] * 255), Math.round(r.rgba[2] * 255));
                p.setColor(c);
                p.setStrokeWidth(pre.radius);
                while (pre != null) {
                    canvas.drawCircle(scaleX(pre.centerX), scaleY(pre.centerY), pre.radius, p);
                    if (pre.predecessor != null) {
                        int x1 = scaleX(pre.centerX);
                        int x2 = scaleX(pre.predecessor.centerX);
                        int y1 = scaleY(pre.centerY);
                        int y2 = scaleY(pre.predecessor.centerY);
                        canvas.drawLine(x1, y1, x2, y2, p);
                    }
                    pre = pre.predecessor;
                }
            }
        }

        private int scaleY(int value) {
            float relPercentage = ((float) value) / ((float) this.videoHeight);
            return Math.round(relPercentage * this.canvasHeight);
        }

        private int scaleX(int value) {
            float relPercentage = ((float) value) / ((float) this.videoWidth);
            return Math.round(relPercentage * this.canvasWidth);
        }
    }
}
