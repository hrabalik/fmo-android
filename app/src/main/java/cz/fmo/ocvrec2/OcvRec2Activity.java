package cz.fmo.ocvrec2;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.SurfaceHolder;
import android.widget.TextView;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import java.lang.ref.WeakReference;
import java.util.Locale;

import cz.fmo.Lib;
import cz.fmo.R;

/**
 * The main activity, facilitating video preview, encoding and saving.
 */
public final class OcvRec2Activity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private final Handler mHandler = new Handler(this);
    private final GUI mGUI = new GUI();
    private Status mStatus = Status.STOPPED;

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
        if (mStatus == Status.RUNNING) return;
        if (!mGUI.isPreviewReady()) return;

        if (isPermissionDenied()) {
            mStatus = Status.PERMISSION_ERROR;
            mGUI.update();
            return;
        }

        boolean ocvInit = OpenCVLoader.initDebug();
        if (!ocvInit) {
            mStatus = Status.OPENCV_ERROR;
            mGUI.update();
            return;
        }

        mGUI.startPreview();

        mStatus = Status.RUNNING;
        mGUI.update();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mGUI.stopPreview();

        mStatus = Status.STOPPED;
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mGUI.q95 = width;
        mGUI.q99 = height;
    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mGUI.q50 += 1;
        mHandler.frameTimings(mGUI.q50, mGUI.q95, mGUI.q99);
        return inputFrame.gray();
    }

    private enum Status {
        STOPPED, RUNNING, CAMERA_ERROR, PERMISSION_ERROR, OPENCV_ERROR
    }

    private static class Timings {
        float q50 = 0;
        float q95 = 0;
        float q99 = 0;
    }

    private static class Handler extends android.os.Handler implements Lib.Callback {
        private static final int FRAME_TIMINGS = 1;
        private static final int CAMERA_ERROR = 2;
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
            }
        }
    }

    /**
     * A subclass that handles visual elements -- buttons, labels, and suchlike.
     */
    private class GUI implements SurfaceHolder.Callback {
        float q50;
        float q95;
        float q99;
        private JavaCameraView mPreview;
        private boolean mPreviewReady = false;
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
        }

        void update() {
            if (mStatus == Status.STOPPED) return;

            String fpsString;
            if (mStatus == Status.CAMERA_ERROR) {
                fpsString = getString(R.string.errorOther);
            } else if (mStatus == Status.PERMISSION_ERROR) {
                fpsString = getString(R.string.errorPermissionFail);
            } else if (mStatus == Status.OPENCV_ERROR) {
                fpsString = getString(R.string.errorOpenCVInitFail);
            } else {
                fpsString = String.format(Locale.US, "%.1f / %.1f / %.1f", q50, q95, q99);
            }

            if (!mBottomTextLast.equals(fpsString)) {
                mBottomText.setText(fpsString);
                mBottomTextLast = fpsString;
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
