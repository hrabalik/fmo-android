package cz.fmo.recording;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.opengl.GLES20;
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
import java.util.ArrayList;
import java.util.List;

import cz.fmo.R;
import cz.fmo.graphics.EGL;
import cz.fmo.graphics.Renderer;
import cz.fmo.util.FileManager;

public class RecordingActivity extends Activity implements SurfaceHolder.Callback {
    private static final float BUFFER_SIZE_SEC = 7.f;
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private final Handler mHandler = new Handler(this);
    private final FileManager mFileMan = new FileManager(this);
    private java.io.File mFile;
    private Status mStatus = Status.STOPPED;
    private SaveStatus mSaveStatus = SaveStatus.NOT_SAVING;
    private GUISurfaceStatus mGUISurfaceStatus = GUISurfaceStatus.NOT_READY;
    private EGL mEGL;
    private EGL.Surface mDisplaySurface;
    private EGL.Surface mEncoderSurface;
    private CameraCapture2 mCapture2;
    private Renderer mRenderer;
    private EncodeThread mEncodeThread;
    private SaveMovieThread mSaveMovieThread;

    @Override
    protected void onCreate(android.os.Bundle savedBundle) {
        super.onCreate(savedBundle);
        mFile = mFileMan.open("continuous-capture.mp4");
        setContentView(R.layout.activity_recording);
        getGUISurfaceView().getHolder().addCallback(this);
        update();
    }

    private SurfaceView getGUISurfaceView() {
        return (SurfaceView) findViewById(R.id.preview_surface);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        mGUISurfaceStatus = GUISurfaceStatus.READY;
        init();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mGUISurfaceStatus = GUISurfaceStatus.NOT_READY;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isPermissionDenied()) {
            ActivityCompat.requestPermissions(this, new String[]{CAMERA_PERMISSION}, 0);
        }
    }

    private boolean isPermissionDenied() {
        int permissionStatus = ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION);
        return permissionStatus != PackageManager.PERMISSION_GRANTED;
    }

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
     * Called by:
     * - onResume()
     * - surfaceCreated(), when GUI preview surface has just been created
     * - onRequestPermissionsResult(), when camera permissions have just been granted
     */
    private void init() {
        if (mStatus != Status.STOPPED && mStatus != Status.ERROR) return;
        if (mGUISurfaceStatus != GUISurfaceStatus.READY) return;

        if (isPermissionDenied()) {
            mStatus = Status.ERROR;
            update();
            return;
        }

        mCapture2 = new CameraCapture2(this, mHandler);
        mStatus = Status.CAMERA_INIT;
        update();
    }

    private void cameraError() {
        mStatus = Status.ERROR; // TODO distinguish denied permissions and other camera errors
        update();
    }

    private void cameraOpened() {
        initStep2();
    }

    /**
     * Called by:
     * - cameraOpened(), when the camera has just become ready
     */
    private void initStep2() {
        if (mStatus != Status.CAMERA_INIT) return;

        CyclicBuffer buf = new CyclicBuffer(mCapture2.getBitRate(), mCapture2.getFrameRate(),
                BUFFER_SIZE_SEC);
        mEncodeThread = new EncodeThread(mCapture2.getMediaFormat(), buf, mHandler);
        mSaveMovieThread = new SaveMovieThread(buf, mHandler);
        mEncodeThread.start();
        mSaveMovieThread.start();

        List<Surface> targets = new ArrayList<>();
        targets.add(getGUISurfaceView().getHolder().getSurface());
        targets.add(mEncodeThread.getInputSurface());
        mCapture2.start(targets);

        /*mEGL = new EGL();
        mDisplaySurface = mEGL.makeSurface(getGUISurfaceView().getHolder().getSurface());
        mDisplaySurface.makeCurrent();
        CyclicBuffer buf = new CyclicBuffer(mCapture.getBitRate(), mCapture.getFrameRate(),
                BUFFER_SIZE_SEC);
        mEncodeThread = new EncodeThread(mCapture.getMediaFormat(), buf, mHandler);
        mSaveMovieThread = new SaveMovieThread(buf, mHandler);
        mEncodeThread.start();
        mSaveMovieThread.start();
        mEncoderSurface = mEGL.makeSurface(mEncodeThread.getInputSurface());
        mRenderer = new Renderer(mHandler);
        mCapture.start(mRenderer.getInputTexture());*/

        mStatus = Status.RUNNING;
        update();
    }

    /**
     * Updates all dynamic UI elements, such as labels and buttons.
     */
    private void update() {
        boolean enableSaveButton = false;
        String statusString;

        if (mStatus == Status.STOPPED) {
            statusString = getString(R.string.recordingStopped);
        } else if (mStatus == Status.ERROR) {
            statusString = getString(R.string.cameraPermissionDenied);
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

        TextView status = (TextView) findViewById(R.id.status_text);
        status.setText(statusString);
        Button saveButton = (Button) findViewById(R.id.save_button);
        saveButton.setEnabled(enableSaveButton);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mStatus = Status.STOPPED;
        if (mCapture2 != null) {
            mCapture2.release();
            mCapture2 = null;
        }
        if (mEncoderSurface != null) {
            mEncoderSurface.release();
            mEncoderSurface = null;
        }
        if (mEncodeThread != null) {
            mEncodeThread.getHandler().sendKill();
            try {
                mEncodeThread.join();
            } catch (InterruptedException ie) {
                throw new RuntimeException("Interrupted");
            }
            mEncodeThread = null;
        }
        if (mSaveMovieThread != null) {
            mSaveMovieThread.getHandler().sendKill();
            try {
                mSaveMovieThread.join();
            } catch (InterruptedException ie) {
                throw new RuntimeException("Interrupted");
            }
            mSaveMovieThread = null;
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
    }

    public void clickSave(@SuppressWarnings("UnusedParameters") android.view.View unused) {
        if (mStatus != Status.RUNNING) return;
        if (mSaveStatus != SaveStatus.NOT_SAVING) return;
        mSaveStatus = SaveStatus.SAVING;
        mSaveMovieThread.getHandler().sendSave(mFile);
        update();
    }

    private void saveCompleted(boolean success) {
        mSaveStatus = SaveStatus.NOT_SAVING;
        if (!success) {
            android.widget.Toast.makeText(this, "Failed to save video", Toast.LENGTH_SHORT).show();
        }
        update();
    }

    private void flushCompleted() {
        update();
    }

    private void cameraFrame() {
        if (mStatus != Status.RUNNING) return;
        mEncodeThread.getHandler().sendFlush();
        // TODO
    }

    //private void frameAvailable() {
    //    if (mStatus != Status.RUNNING) return;
    //
    //    // draw onto mDisplaySurface
    //    mDisplaySurface.makeCurrent();
    //    SurfaceView guiSurface = getGUISurfaceView();
    //    int guiWidth = guiSurface.getWidth();
    //    int guiHeight = guiSurface.getHeight();
    //    GLES20.glViewport(0, 0, guiWidth, guiHeight);
    //    mRenderer.drawRectangle();
    //    mDisplaySurface.swapBuffers();
    //
    //    // draw onto mEncoderSurface
    //    mEncoderSurface.makeCurrent();
    //    int encWidth = mCapture2.getWidth();
    //    int encHeight = mCapture2.getHeight();
    //    GLES20.glViewport(0, 0, encWidth, encHeight);
    //    mRenderer.drawRectangle();
    //    mEncodeThread.getHandler().sendFlush();
    //    mEncoderSurface.presentationTime(mRenderer.getTimestamp());
    //    mEncoderSurface.swapBuffers();
    //}

    private enum Status {
        STOPPED, RUNNING, ERROR, CAMERA_INIT
    }

    private enum SaveStatus {
        NOT_SAVING, SAVING
    }

    private enum GUISurfaceStatus {
        NOT_READY, READY
    }

    private static class Handler extends android.os.Handler implements SaveMovieThread.Callback,
            EncodeThread.Callback, CameraCapture2.Callback {
        private static final int FLUSH_COMPLETED = 1;
        private static final int SAVE_COMPLETED = 2;
        private static final int FRAME_AVAILABLE = 3;
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

        //@Override
        //public void onFrameAvailable() {
        //    sendMessage(obtainMessage(FRAME_AVAILABLE));
        //}

        @Override
        public void onCameraError() {
            sendMessage(obtainMessage(CAMERA_ERROR));
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
                //case FRAME_AVAILABLE:
                //    activity.frameAvailable();
                //    break;
                case CAMERA_ERROR:
                    activity.cameraError();
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
}
