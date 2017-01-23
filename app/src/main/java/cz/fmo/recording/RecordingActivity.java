package cz.fmo.recording;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

import cz.fmo.R;
import cz.fmo.util.FileManager;

/**
 * The main activity, facilitating video preview, encoding, saving, and passing images along to the
 * fast object detector. The user sees a preview of the captured scene, while the images are being
 * simultaneously encoded as video and sent to the FMO C++ library for processing. There are 3
 * separate threads for video encoding, saving video files and image processing.
 */
public class RecordingActivity extends Activity {
    private static final float BUFFER_SIZE_SEC = 7.f;
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private final Handler mHandler = new Handler(this);
    private final FileManager mFileMan = new FileManager(this);
    private final GUI mGUI = new GUI();
    private java.io.File mFile;
    private Status mStatus = Status.STOPPED;
    private CameraCapture2.Error mCameraError;
    private SaveStatus mSaveStatus = SaveStatus.NOT_SAVING;
    private GUISurfaceStatus mGUISurfaceStatus = GUISurfaceStatus.NOT_READY;
    private CameraCapture2 mCapture2;
    private EncodeThread mEncodeThread;
    private SaveMovieThread mSaveMovieThread;
    private ProcessingThread mProcessingThread;

    @Override
    protected void onCreate(android.os.Bundle savedBundle) {
        super.onCreate(savedBundle);
        mFile = mFileMan.open("continuous-capture.mp4");
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
            ActivityCompat.requestPermissions(this, new String[]{CAMERA_PERMISSION}, 0);
        }
    }

    /**
     * Queries the camera permission status.
     */
    private boolean isPermissionDenied() {
        int permissionStatus = ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION);
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
        if (mStatus != Status.STOPPED && mStatus != Status.CAMERA_ERROR) return;
        if (mGUISurfaceStatus != GUISurfaceStatus.READY) return;

        if (isPermissionDenied()) {
            mStatus = Status.CAMERA_ERROR;
            mCameraError = CameraCapture2.Error.PERMISSION_FAIL;
            mGUI.update();
            return;
        }

        mCapture2 = new CameraCapture2(this, mHandler);
        mStatus = Status.CAMERA_INIT;
        mGUI.update();
    }

    /**
     * Called when the camera has just become ready. At this point, the camera component has
     * selected and configured a suitable device. The decision about capture format and image size
     * has been made.
     */
    private void cameraOpened() {
        if (mStatus != Status.CAMERA_INIT) return;

        CyclicBuffer buf = new CyclicBuffer(mCapture2.getBitRate(), mCapture2.getFrameRate(),
                BUFFER_SIZE_SEC);
        mEncodeThread = new EncodeThread(mCapture2.getMediaFormat(), buf, mHandler);
        mSaveMovieThread = new SaveMovieThread(buf, mHandler);
        mProcessingThread = new ProcessingThread(mCapture2.getWidth(), mCapture2.getHeight(),
                mCapture2.getFormat());
        mEncodeThread.start();
        mSaveMovieThread.start();
        mProcessingThread.start();

        mCapture2.addTarget(mGUI.getPreviewSurface());
        mCapture2.addTarget(mEncodeThread.getInputSurface());
        mCapture2.addTarget(mProcessingThread.getInputSurface());
        mCapture2.start();

        mStatus = Status.RUNNING;
        mGUI.update();
    }

    /**
     * Called when there has been an error while opening or operating the camera device. From now
     * on, the activity is in a state of unrecoverable error. The only way to recover is to pause
     * and resume the activity.
     *
     * @param error Indicates the nature of the error.
     */
    private void cameraError(CameraCapture2.Error error) {
        mStatus = Status.CAMERA_ERROR;
        mCameraError = error;
        mGUI.update();
    }

    /**
     * Disposes of all resources, including the camera device and all the threads.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mStatus = Status.STOPPED;
        if (mCapture2 != null) {
            mCapture2.release();
            mCapture2 = null;
        }
        if (mEncodeThread != null) {
            mEncodeThread.getHandler().sendKill();
            try {
                mEncodeThread.join();
            } catch (InterruptedException ie) {
                throw new RuntimeException("Interrupted when closing EncodeThread");
            }
            mEncodeThread = null;
        }
        if (mSaveMovieThread != null) {
            mSaveMovieThread.getHandler().sendKill();
            try {
                mSaveMovieThread.join();
            } catch (InterruptedException ie) {
                throw new RuntimeException("Interrupted when closing SaveMovieThread");
            }
            mSaveMovieThread = null;
        }
        if (mProcessingThread != null) {
            mProcessingThread.getHandler().sendKill();
            try {
                mProcessingThread.join();
            } catch (InterruptedException ie) {
                throw new RuntimeException("Interrupted when closing ProcessingThread");
            }
            mProcessingThread = null;
        }
    }

    public void clickSave(@SuppressWarnings("UnusedParameters") android.view.View unused) {
        if (mStatus != Status.RUNNING) return;
        if (mSaveStatus != SaveStatus.NOT_SAVING) return;
        mSaveStatus = SaveStatus.SAVING;
        mSaveMovieThread.getHandler().sendSave(mFile);
        mGUI.update();
    }

    private void saveCompleted(boolean success) {
        mSaveStatus = SaveStatus.NOT_SAVING;
        if (!success) {
            Toast.makeText(this, "Failed to save video", Toast.LENGTH_SHORT).show();
        }
        mGUI.update();
    }

    private void flushCompleted() {
        mGUI.update();
    }

    private void cameraFrame() {
        if (mStatus != Status.RUNNING) return;
        mEncodeThread.getHandler().sendFlush();
    }

    private enum Status {
        STOPPED, RUNNING, CAMERA_ERROR, CAMERA_INIT
    }

    private enum SaveStatus {
        NOT_SAVING, SAVING
    }

    private enum GUISurfaceStatus {
        NOT_READY, READY
    }

    /**
     * A subclass that receives all relevant messages on an arbitrary thread and forwards them to
     * the main thread.
     */
    private static class Handler extends android.os.Handler implements SaveMovieThread.Callback,
            EncodeThread.Callback, CameraCapture2.Callback {
        private static final int FLUSH_COMPLETED = 1;
        private static final int SAVE_COMPLETED = 2;
        private static final int CAMERA_ERROR = 4;
        private static final int CAMERA_OPENED = 5;
        private static final int CAMERA_FRAME = 6;
        private final WeakReference<RecordingActivity> mActivity;

        Handler(RecordingActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void flushCompleted(@SuppressWarnings("UnusedParameters") EncodeThread thread) {
            sendMessage(obtainMessage(FLUSH_COMPLETED));
        }

        @Override
        public void saveCompleted(String filename, boolean success) {
            sendMessage(obtainMessage(SAVE_COMPLETED, success));
        }

        @Override
        public void onCameraError(CameraCapture2.Error error) {
            sendMessage(obtainMessage(CAMERA_ERROR, error));
        }

        @Override
        public void onCameraOpened() {
            sendMessage(obtainMessage(CAMERA_OPENED));
        }

        @Override
        public void onCameraFrame() {
            sendMessage(obtainMessage(CAMERA_FRAME));
        }

        @Override
        public void handleMessage(android.os.Message msg) {
            RecordingActivity activity = mActivity.get();
            if (activity == null) return;

            switch (msg.what) {
                case FLUSH_COMPLETED:
                    activity.flushCompleted();
                    break;
                case SAVE_COMPLETED:
                    activity.saveCompleted((Boolean) msg.obj);
                    break;
                case CAMERA_ERROR:
                    activity.cameraError((CameraCapture2.Error) msg.obj);
                    break;
                case CAMERA_OPENED:
                    activity.cameraOpened();
                    break;
                case CAMERA_FRAME:
                    activity.cameraFrame();
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * A subclass that handles visual elements -- buttons, labels, and suchlike.
     */
    private class GUI implements SurfaceHolder.Callback {
        private TextView mStatusText;
        private String mStatusTextLast;
        private Button mSaveButton;
        private boolean mSaveButtonLast;
        private SurfaceView mPreview;

        /**
         * Prepares all static UI elements.
         */
        void init() {
            setContentView(R.layout.activity_recording);
            mStatusText = (TextView) findViewById(R.id.status_text);
            mStatusTextLast = mStatusText.getText().toString();
            mSaveButton = (Button) findViewById(R.id.save_button);
            mSaveButtonLast = mSaveButton.isEnabled();
            mPreview = (SurfaceView) findViewById(R.id.preview_surface);
            mPreview.getHolder().addCallback(this);
        }

        /**
         * Provides access to the preview, user-visible surface.
         */
        Surface getPreviewSurface() {
            return mPreview.getHolder().getSurface();
        }

        /**
         * Updates all dynamic UI elements, such as labels and buttons.
         */
        void update() {
            boolean enableSaveButton = false;
            String statusString;

            if (mStatus == Status.STOPPED) {
                statusString = getString(R.string.recordingStopped);
            } else if (mStatus == Status.CAMERA_ERROR) {
                switch (mCameraError) {
                    case PERMISSION_FAIL:
                        statusString = getString(R.string.errorPermissionFail);
                        break;
                    case CONFIGURE_FAIL:
                        statusString = getString(R.string.errorConfigureFail);
                        break;
                    case INACCESSIBLE:
                        statusString = getString(R.string.errorInaccessible);
                        break;
                    case DISCONNECTED:
                        statusString = getString(R.string.errorDisconnected);
                        break;
                    default:
                        statusString = getString(R.string.errorOther);
                        break;
                }
            } else if (mStatus == Status.CAMERA_INIT) {
                statusString = getString(R.string.cameraInitializing);
            } else if (mSaveStatus == SaveStatus.SAVING) {
                statusString = getString(R.string.savingVideo);
            } else {
                enableSaveButton = true;
                long lengthUs = mEncodeThread.getBufferContentsDuration();
                float lengthSec = ((float) lengthUs) / 1000000.f;
                statusString = getString(R.string.videoLength, lengthSec);
            }

            if (!mStatusTextLast.equals(statusString)) {
                mStatusText.setText(statusString);
                mStatusTextLast = statusString;
            }

            if (mSaveButtonLast != enableSaveButton) {
                mSaveButton.setEnabled(enableSaveButton);
                mSaveButtonLast = enableSaveButton;
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mGUISurfaceStatus = GUISurfaceStatus.READY;
            RecordingActivity.this.init();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            mGUISurfaceStatus = GUISurfaceStatus.NOT_READY;
        }
    }
}
