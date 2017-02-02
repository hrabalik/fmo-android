package cz.fmo.ocvrec2;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.TextView;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Locale;

import cz.fmo.Lib;
import cz.fmo.R;
import cz.fmo.recording.CyclicBuffer;
import cz.fmo.recording.EncodeThread;
import cz.fmo.recording.SaveMovieThread;
import cz.fmo.util.FileManager;

/**
 * The main activity, facilitating video preview, encoding and saving.
 */
public final class OcvRec2Activity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final int BIT_RATE = 6 * 1024 * 1024;
    private static final int FRAME_RATE = 30;
    private static final int I_FRAME_INTERVAL = 1;
    private static final float BUFFER_SECONDS = 7;
    private static final String VIDEO_MIME = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String FILENAME = "video.mp4";
    private final Handler mHandler = new Handler(this);
    private final GUI mGUI = new GUI();
    private Status mStatus = Status.STOPPED;
    private EncodeThread mEncode;
    private SaveMovieThread mSaveMovie;
    private Bitmap mBitmap;

    @Override
    protected void onCreate(android.os.Bundle savedBundle) {
        super.onCreate(savedBundle);
        mGUI.init();
        mGUI.update();
    }

    /**
     * Responsible for querying and acquiring camera permissions. Whatever the response will be,
     * the permission request could result in the application being paused and resumed. For that
     * reason, requesting permissions at any later point, including in onResume(), might cause an
     * infinite loop.
     */
    @Override
    protected void onStart() {
        super.onStart();
        if (isPermissionDenied()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0);
        }
    }

    /**
     * Queries the camera permission status.
     */
    private boolean isPermissionDenied() {
        int permissionStatus = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        return permissionStatus != PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Called when a decision has been made regarding the camera permission. Whatever the response
     * is, the initialization procedure continues. If the permission is denied, the init() method
     * will display a proper error message on the screen.
     */
    @Override
    public void onRequestPermissionsResult(int requestID, @NonNull String[] permissionList,
                                           @NonNull int[] grantedList) {
        init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        init();
    }

    /**
     * The main initialization step. There are multiple callers of this method, but mechanisms are
     * put into place so that the actual initialization happens exactly once. There is no need for
     * a mutex, assuming that all entry points are run by the main thread.
     * <p>
     * Called by:
     * - onResume()
     * - GUI.surfaceCreated(), when GUI preview surface has just been created
     * - onRequestPermissionsResult(), when camera permissions have just been granted
     */
    private void init() {
        // reality check: don't initialize twice
        if (mStatus == Status.RUNNING) return;

        // stop if the preview surface has not been created yet
        if (!mGUI.isPreviewReady()) return;

        // stop if the camera permissions haven't been granted
        if (isPermissionDenied()) {
            mStatus = Status.PERMISSION_ERROR;
            mGUI.update();
            return;
        }

        // stop if OpenCV initialization fails
        boolean ocvInit = OpenCVLoader.initDebug();
        if (!ocvInit) {
            mStatus = Status.OPENCV_ERROR;
            mGUI.update();
            return;
        }

        // everything is ready, start sending frames (triggers onCameraViewStarted)
        mGUI.startPreview();

        // refresh GUI
        mStatus = Status.RUNNING;
        mGUI.update();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mGUI.stopPreview();

        mStatus = Status.STOPPED;
    }

    /**
     * Prepare to receive camera frames (see onCameraFrame).
     */
    @Override
    public void onCameraViewStarted(int width, int height) {
        // make a suitably-sized cyclic buffer
        CyclicBuffer buffer = new CyclicBuffer(BIT_RATE, FRAME_RATE, BUFFER_SECONDS);

        // create and fill a MediaFormat instance
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME, width, height);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        // create and start dedicated threads
        mEncode = new EncodeThread(format, buffer, mHandler);
        mEncode.start();
        mSaveMovie = new SaveMovieThread(buffer, mHandler);
        mSaveMovie.start();

        // create a bitmap for caching
        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        // C++ initialization
        Lib.ocvRec2Start(width, height, mHandler);
    }

    /**
     * Perform cleanup after the last camera frame (see onCameraFrame) has been received.
     */
    @Override
    public void onCameraViewStopped() {
        Lib.ocvRec2Stop();

        if (mBitmap != null) {
            mBitmap.recycle();
            mBitmap = null;
        }

        if (mSaveMovie != null) {
            mSaveMovie.getHandler().sendKill();
            try {
                mSaveMovie.join();
            } catch (InterruptedException ie) {
                throw new RuntimeException("Interrupted when closing SaveMovieThread");
            }
            mSaveMovie = null;
        }

        if (mEncode != null) {
            mEncode.getHandler().sendKill();
            try {
                mEncode.join();
            } catch (InterruptedException ie) {
                throw new RuntimeException("Interrupted when closing EncodeThread");
            }
            mEncode = null;
        }
    }

    /**
     * Process a frame.
     *
     * @return Image to be rendered on the screen.
     */
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        // get a timestamp that will represent the frame time
        long timeNs = System.nanoTime();

        // request that the output of the encoder is emptied
        mEncode.getHandler().sendFlush();

        // send the unmodified frame to the video encoder input
        Mat rgba = inputFrame.rgba();
        org.opencv.android.Utils.matToBitmap(rgba, mBitmap);
        Canvas canvas = mEncode.getInputSurface().lockCanvas(null);
        canvas.drawBitmap(mBitmap, 0, 0, null);
        mEncode.getInputSurface().unlockCanvasAndPost(canvas);

        // send the gray-scale version of the frame to C++
        Mat gray = inputFrame.gray();
        Lib.ocvRec2Frame(gray.getNativeObjAddr(), timeNs);
        return gray;
    }

    /**
     * Called when the encoder output has been moved into the cyclic buffer.
     */
    private void onEncoderFlushed() {
        mGUI.timeInBuffer = mEncode.getBufferContentsDuration() / 1e6f;
        mGUI.update();
    }

    public void onClickSave(@SuppressWarnings("UnusedParameters") View view) {
        if (mStatus != Status.RUNNING) return;
        mStatus = Status.SAVING;
        FileManager fileMan = new FileManager(this);
        File outFile = fileMan.open(FILENAME);
        mSaveMovie.getHandler().sendSave(outFile);
    }

    private void onSaveCompleted() {
        if (mStatus != Status.SAVING) return;
        mStatus = Status.RUNNING;
    }

    private enum Status {
        STOPPED, RUNNING, SAVING, CAMERA_ERROR, PERMISSION_ERROR, OPENCV_ERROR
    }

    private static class Timings {
        float q50 = 0;
        float q95 = 0;
        float q99 = 0;
    }

    /**
     * A subclass that receives all relevant messages on an arbitrary thread and forwards them to
     * the main thread.
     */
    private static class Handler extends android.os.Handler implements Lib.Callback,
            EncodeThread.Callback, SaveMovieThread.Callback {
        private static final int FRAME_TIMINGS = 1;
        private static final int CAMERA_ERROR = 2;
        private static final int ENCODER_FLUSHED = 3;
        private static final int SAVE_COMPLETED = 4;
        private final WeakReference<OcvRec2Activity> mActivity;

        Handler(OcvRec2Activity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void frameTimings(float q50, float q95, float q99) {
            Timings timings = new Timings();
            timings.q50 = q50;
            timings.q95 = q95;
            timings.q99 = q99;
            sendMessage(obtainMessage(FRAME_TIMINGS, timings));
        }

        @Override
        public void cameraError() {
            sendMessage(obtainMessage(CAMERA_ERROR));
        }

        @Override
        public void flushCompleted(EncodeThread thread) {
            sendMessage(obtainMessage(ENCODER_FLUSHED, thread));
        }

        @Override
        public void saveCompleted(String filename, boolean success) {
            sendMessage(obtainMessage(SAVE_COMPLETED));
        }

        @Override
        public void handleMessage(android.os.Message msg) {
            OcvRec2Activity activity = mActivity.get();
            if (activity == null) return;

            switch (msg.what) {
                case FRAME_TIMINGS:
                    Timings timings = (Timings) msg.obj;
                    activity.mGUI.q50 = timings.q50;
                    activity.mGUI.q95 = timings.q95;
                    activity.mGUI.q99 = timings.q99;
                    activity.mGUI.update();
                    break;
                case CAMERA_ERROR:
                    activity.mStatus = Status.CAMERA_ERROR;
                    activity.mGUI.update();
                    break;
                case ENCODER_FLUSHED:
                    activity.onEncoderFlushed();
                    break;
                case SAVE_COMPLETED:
                    activity.onSaveCompleted();
                    break;
            }
        }
    }

    /**
     * A subclass that handles visual elements -- buttons, labels, and suchlike.
     */
    private class GUI implements SurfaceHolder.Callback {
        float timeInBuffer;
        float q50;
        float q95;
        float q99;
        private JavaCameraView mPreview;
        private boolean mPreviewReady = false;
        private TextView mTopText;
        private String mTopTextLast;
        private TextView mBottomText;
        private String mBottomTextLast;

        /**
         * Prepares all static UI elements.
         */
        void init() {
            setContentView(R.layout.activity_ocvrec2);
            mPreview = (JavaCameraView) findViewById(R.id.ocvrec2_preview);
            mPreview.setVisibility(JavaCameraView.VISIBLE);
            mPreview.setCvCameraViewListener(OcvRec2Activity.this);
            mPreview.getHolder().addCallback(this);
            mBottomText = (TextView) findViewById(R.id.ocvrec2_bottom_text);
            mBottomTextLast = mBottomText.getText().toString();
            mTopText = (TextView) findViewById(R.id.ocvrec2_top_text);
            mTopTextLast = mTopText.getText().toString();
        }

        void update() {
            if (mStatus == Status.STOPPED) return;

            String topText;
            if (mStatus == Status.RUNNING) {
                topText = getString(R.string.secondsOfVideo, timeInBuffer);
            } else {
                topText = "";
            }

            if (!mTopTextLast.equals(topText)) {
                mTopText.setText(topText);
                mTopTextLast = topText;
            }

            String bottomText;
            if (mStatus == Status.CAMERA_ERROR) {
                bottomText = getString(R.string.errorOther);
            } else if (mStatus == Status.PERMISSION_ERROR) {
                bottomText = getString(R.string.errorPermissionFail);
            } else if (mStatus == Status.OPENCV_ERROR) {
                bottomText = getString(R.string.errorOpenCVInitFail);
            } else {
                bottomText = String.format(Locale.US, "%.2f / %.2f / %.2f", q50, q95, q99);
            }

            if (!mBottomTextLast.equals(bottomText)) {
                mBottomText.setText(bottomText);
                mBottomTextLast = bottomText;
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mPreviewReady = true;
            OcvRec2Activity.this.init();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            mPreviewReady = false;
        }

        boolean isPreviewReady() {
            return mPreviewReady;
        }

        void startPreview() {
            mPreview.enableView();
        }

        void stopPreview() {
            mPreview.disableView();
        }
    }
}
