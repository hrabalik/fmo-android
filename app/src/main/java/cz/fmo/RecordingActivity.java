package cz.fmo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Locale;

import cz.fmo.camera.CameraThread;
import cz.fmo.recording.AutomaticRecordingTask;
import cz.fmo.recording.CyclicBuffer;
import cz.fmo.recording.EncodeThread;
import cz.fmo.recording.ManualRecordingTask;
import cz.fmo.recording.SaveThread;
import cz.fmo.util.Config;
import cz.fmo.util.FileManager;

/**
 * The main activity, facilitating video preview, encoding and saving.
 */
public final class RecordingActivity extends Activity {
    private static final float BUFFER_SECONDS = 8;
    private static final float AUTOMATIC_LEN = 4;
    private static final float AUTOMATIC_WAIT = 2;
    private static final String FILENAME = "video.mp4";
    private final Handler mHandler = new Handler(this);
    private final GUI mGUI = new GUI();
    private final FileManager mFileMan = new FileManager(this);
    private Config mConfig;
    private Status mStatus = Status.STOPPED;
    private CameraThread mCamera;
    private EncodeThread mEncode;
    private SaveThread mSaveMovie;
    private SaveThread.Task mSaveTask;
    private CameraThread.Target mEncodeTarget;

    @Override
    protected void onCreate(android.os.Bundle savedBundle) {
        super.onCreate(savedBundle);
        mConfig = new Config(this);
        mGUI.init(mConfig);
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
        if (isCameraPermissionDenied() || isStoragePermissionDenied()) {
            String[] perms = new String[]{Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(this, perms, 0);
        }
    }

    /**
     * Queries the camera permission status.
     */
    private boolean isCameraPermissionDenied() {
        int permissionStatus = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        return permissionStatus != PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Queries the storage permission status.
     */
    private boolean isStoragePermissionDenied() {
        int permissionStatus = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
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

        // stop if permissions haven't been granted
        if (isCameraPermissionDenied()) {
            mStatus = Status.CAMERA_PERMISSION_ERROR;
            mGUI.update();
            return;
        }

        if (isStoragePermissionDenied()) {
            mStatus = Status.STORAGE_PERMISSION_ERROR;
            mGUI.update();
            return;
        }

        // create a dedicated camera input thread
        mCamera = new CameraThread(mHandler);

        // add preview as camera target
        mCamera.addTarget(mGUI.getPreviewSurface(), mGUI.getPreviewWidth(),
                mGUI.getPreviewHeight());

        if (mConfig.record) {
            // make a suitably-sized cyclic buffer
            CyclicBuffer buffer = new CyclicBuffer(mCamera.getBitRate(), mCamera.getFrameRate(),
                    BUFFER_SECONDS);

            // create dedicated encoding and video saving threads
            mEncode = new EncodeThread(mCamera.getMediaFormat(), buffer, mHandler);
            mSaveMovie = new SaveThread(buffer, mHandler);

            // add encoder as camera target
            mEncodeTarget = mCamera.addTarget(mEncode.getInputSurface(), mCamera.getWidth(),
                    mCamera.getHeight());

            // only allow encoding in automatic mode; manual mode starts encoding once the recording
            // button is pressed
            setEncodingEnabled(mConfig.automatic);
        }

        if (mConfig.detect) {
            // C++ initialization
            Lib.recordingStart(mCamera.getWidth(), mCamera.getHeight(), mHandler);
        }

        // refresh GUI
        mStatus = Status.RUNNING;
        mGUI.update();

        // start threads
        if (mEncode != null) mEncode.start();
        if (mSaveMovie != null) mSaveMovie.start();
        mCamera.start();
    }

    /**
     * Perform cleanup after the activity has been paused.
     */
    @Override
    protected void onPause() {
        super.onPause();

        Lib.recordingStop();

        if (mCamera != null) {
            mCamera.getHandler().sendKill();
            try {
                mCamera.join();
            } catch (InterruptedException ie) {
                throw new RuntimeException("Interrupted when closing CameraThread");
            }
            mCamera = null;
        }

        stopSaving();

        if (mSaveMovie != null) {
            mSaveMovie.getHandler().sendKill();
            try {
                mSaveMovie.join();
            } catch (InterruptedException ie) {
                throw new RuntimeException("Interrupted when closing SaveThread");
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

        mStatus = Status.STOPPED;
    }

    /**
     * Called when the encoder output has been moved into the cyclic buffer.
     */
    private void onEncoderFlushed() {
        if (mEncode == null) return;
        mGUI.timeInBuffer = mEncode.getBufferContentsDuration() / 1e6f;
        mGUI.update();
    }

    public void onForceAutomaticRecording(@SuppressWarnings("UnusedParameters") View view) {
        if (mSaveMovie == null) return;
        if (mStatus != Status.RUNNING) return;

        if (mSaveTask != null) {
            mSaveTask.terminate(mSaveMovie);
            mSaveTask = null;
        }

        File outFile = mFileMan.open(FILENAME);
        mSaveTask = new AutomaticRecordingTask(AUTOMATIC_WAIT, AUTOMATIC_LEN, outFile, mSaveMovie);
    }

    public void onStartManualRecording(@SuppressWarnings("UnusedParameters") View view) {
        if (mSaveMovie == null) return;
        if (mStatus != Status.RUNNING) return;
        if (mSaveTask != null) return;
        setEncodingEnabled(true);
        File outFile = mFileMan.open(FILENAME);
        mSaveTask = new ManualRecordingTask(outFile, mSaveMovie);
        mGUI.update();
    }

    public void onStopManualRecording(@SuppressWarnings("UnusedParameters") View view) {
        if (mSaveMovie == null) return;
        mSaveTask.terminate(mSaveMovie);
        setEncodingEnabled(false);
        mSaveTask = null;
        mGUI.update();
    }

    private void onSaveCompleted(File file, boolean success) {
        if (success) {
            mFileMan.newMedia(file);
        }
        mSaveTask = null;
        mGUI.update();
    }

    public void onRecordToggle(View toggle) {
        mConfig.record = ((ToggleButton) toggle).isChecked();
        mConfig.commit();
        recreate();
    }

    public void onDetectToggle(View toggle) {
        mConfig.detect = ((ToggleButton) toggle).isChecked();
        mConfig.apply();

        if (mConfig.detect) {
            Lib.recordingStart(mCamera.getWidth(), mCamera.getHeight(), mHandler);
        } else {
            Lib.recordingStop();
        }
    }

    public void onAutoToggle(View toggle) {
        mConfig.automatic = ((ToggleButton) toggle).isChecked();
        mConfig.apply();
        setEncodingEnabled(mConfig.automatic);
        mGUI.update();
    }

    /**
     * Ceases any saving operation, scheduled or in progress.
     */
    private void stopSaving() {
        if (mSaveTask != null) {
            mSaveTask.terminate(mSaveMovie);
            mSaveTask = null;
        }
    }

    /**
     * Enabled or disables encoding, along with any possibility of recording. In idle state,
     * encoding is disabled to save battery; while recording (both manually or automatically),
     * encoding is enabled.
     */
    private void setEncodingEnabled(boolean enabled) {
        stopSaving();

        if (mEncode != null) {
            mEncode.clearBuffer();
        }

        if (mEncodeTarget != null) {
            mEncodeTarget.setEnabled(enabled);
        }
    }

    private enum Status {
        STOPPED, RUNNING, CAMERA_ERROR, CAMERA_PERMISSION_ERROR, STORAGE_PERMISSION_ERROR
    }

    private static class Timings {
        float q50 = 0;
        float q95 = 0;
        float q99 = 0;
    }

    /**
     * A subclass that receives all relevant messages on an arbitrary thread and reacts to them,
     * typically by forwarding them to the main (GUI) thread.
     */
    private static class Handler extends android.os.Handler implements Lib.Callback,
            EncodeThread.Callback, SaveThread.Callback, CameraThread.Callback {
        private static final int FRAME_TIMINGS = 1;
        private static final int CAMERA_ERROR = 2;
        private static final int ENCODER_FLUSHED = 3;
        private static final int SAVE_COMPLETED = 4;
        private final WeakReference<RecordingActivity> mActivity;

        Handler(RecordingActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void frameTimings(float q50, float q95, float q99) {
            Timings timings = new Timings();
            timings.q50 = q50;
            timings.q95 = q95;
            timings.q99 = q99;
            removeMessages(FRAME_TIMINGS);
            sendMessage(obtainMessage(FRAME_TIMINGS, timings));
        }

        @Override
        public void log(String message) {
            // ignored
        }

        @Override
        public void flushCompleted(EncodeThread thread) {
            if (hasMessages(ENCODER_FLUSHED)) return;
            sendMessage(obtainMessage(ENCODER_FLUSHED));
        }

        @Override
        public void saveCompleted(File file, boolean success) {
            sendMessage(obtainMessage(SAVE_COMPLETED, success ? 1 : 0, 0, file));
        }

        @Override
        public void onCameraRender() {
            // send flush command to encoder thread
            RecordingActivity activity = mActivity.get();
            if (activity == null) return;
            if (activity.mEncode == null) return;
            activity.mEncode.getHandler().sendFlush();
        }

        @Override
        public void onCameraFrame(byte[] dataYUV420SP) {
            Lib.recordingFrame(dataYUV420SP);
        }

        @Override
        public void onCameraError() {
            if (hasMessages(CAMERA_ERROR)) return;
            sendMessage(obtainMessage(CAMERA_ERROR));
        }

        @Override
        public void handleMessage(android.os.Message msg) {
            RecordingActivity activity = mActivity.get();
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
                    activity.onSaveCompleted((File) msg.obj, msg.arg1 == 1);
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
        private SurfaceView mPreview;
        private boolean mPreviewReady = false;
        private TextView mTopText;
        private String mTopTextLast;
        private TextView mBottomText;
        private String mBottomTextLast;
        private Button mManualStoppedButton;
        private Button mManualRunningButton;
        private Button mAutomaticStoppedButton;
        private Button mAutomaticRunningButton;

        /**
         * Prepares all static UI elements.
         */
        void init(Config config) {
            setContentView(R.layout.activity_recording);
            mPreview = (SurfaceView) findViewById(R.id.recording_preview);
            mPreview.getHolder().addCallback(this);
            mBottomText = (TextView) findViewById(R.id.recording_bottom_text);
            mBottomTextLast = mBottomText.getText().toString();
            mTopText = (TextView) findViewById(R.id.recording_top_text);
            mTopTextLast = mTopText.getText().toString();
            mManualStoppedButton = (Button) findViewById(R.id.recording_manual_stopped);
            mManualRunningButton = (Button) findViewById(R.id.recording_manual_running);
            mAutomaticStoppedButton = (Button) findViewById(R.id.recording_automatic_stopped);
            mAutomaticRunningButton = (Button) findViewById(R.id.recording_automatic_running);
            ((ToggleButton) findViewById(R.id.recording_record_toggle)).setChecked(config.record);
            ((ToggleButton) findViewById(R.id.recording_auto_toggle)).setChecked(config.automatic);
            ((ToggleButton) findViewById(R.id.recording_detect_toggle)).setChecked(config.detect);
            findViewById(R.id.recording_auto_toggle).setVisibility(config.record ? View.VISIBLE :
                    View.GONE);
        }

        /**
         * Updates all dynamic UI elements, such as labels and buttons.
         */
        void update() {
            if (mStatus == Status.STOPPED) return;
            updateRecordingButtons();
            updateTopText();
            updateBottomText();
        }

        private void updateBottomText() {
            String text;
            if (mStatus == Status.CAMERA_ERROR) {
                text = getString(R.string.errorOther);
            } else if (mStatus == Status.CAMERA_PERMISSION_ERROR) {
                text = getString(R.string.errorCameraPermission);
            } else if (mStatus == Status.STORAGE_PERMISSION_ERROR) {
                text = getString(R.string.errorStoragePermission);
            } else {
                text = String.format(Locale.US, "%.2f / %.1f / %.0f", q50, q95, q99);
            }

            if (!mBottomTextLast.equals(text)) {
                mBottomText.setText(text);
                mBottomTextLast = text;
            }
        }

        private void updateTopText() {
            String text;
            if (mStatus == Status.RUNNING) {
                text = getString(R.string.videoLength, timeInBuffer);
            } else {
                text = "";
            }

            if (!mTopTextLast.equals(text)) {
                mTopText.setText(text);
                mTopTextLast = text;
            }
        }

        private void updateRecordingButtons() {
            {
                boolean relevant = mConfig.record && !mConfig.automatic;
                boolean stopped = relevant && mSaveTask == null;
                boolean running = relevant && mSaveTask != null;
                mManualStoppedButton.setVisibility(stopped ? View.VISIBLE : View.GONE);
                mManualRunningButton.setVisibility(running ? View.VISIBLE : View.GONE);
            }

            {
                boolean relevant = mConfig.record && mConfig.automatic;
                boolean stopped = relevant && mSaveTask == null;
                boolean running = relevant && mSaveTask != null;
                mAutomaticStoppedButton.setVisibility(stopped ? View.VISIBLE : View.GONE);
                mAutomaticRunningButton.setVisibility(running ? View.VISIBLE : View.GONE);
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mPreviewReady = true;
            RecordingActivity.this.init();
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

        Surface getPreviewSurface() {
            return mPreview.getHolder().getSurface();
        }

        int getPreviewWidth() {
            return mPreview.getWidth();
        }

        int getPreviewHeight() {
            return mPreview.getHeight();
        }
    }
}
