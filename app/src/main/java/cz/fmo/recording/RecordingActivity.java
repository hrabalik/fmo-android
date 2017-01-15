package cz.fmo.recording;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.opengl.GLES20;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

import cz.fmo.R;
import cz.fmo.graphics.EGL;
import cz.fmo.graphics.Renderer;
import cz.fmo.util.FileManager;

public class RecordingActivity extends Activity implements SurfaceHolder.Callback {
    private static final float BUFFER_SIZE_SEC = 7.f;
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final int REQUEST_ID = 45117588;
    private static final long REQUEST_TIMEOUT_MS = 100;
    private final Handler mHandler = new Handler(this);
    private final FileManager mFileMan = new FileManager(this);
    private java.io.File mFile;
    private Status mStatus = Status.STOPPED;
    private SaveStatus mSaveStatus = SaveStatus.NOT_SAVING;
    private GUISurfaceStatus mGUISurfaceStatus = GUISurfaceStatus.NOT_READY;
    private EGL mEGL;
    private EGL.Surface mDisplaySurface;
    private EGL.Surface mEncoderSurface;
    private CameraCapture mCapture;
    private Renderer mRenderer;
    private EncodeThread mEncodeThread;
    private SaveMovieThread mSaveMovieThread;
    private long mLastRequestTimeMs = 0;

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

    private void update() {
        boolean enableSaveButton = false;
        String statusString;

        if (mStatus == Status.STOPPED) {
            statusString = getString(R.string.recordingStopped);
        }
        else if (mStatus == Status.DENIED) {
            statusString = getString(R.string.cameraPermissionDenied);
        }
        else if (mSaveStatus == SaveStatus.SAVING) {
            statusString = getString(R.string.savingVideo);
        }
        else {
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
    protected void onResume() {
        super.onResume();
        // remove the not running check once multiple onResume calls stop occuring
        if (mGUISurfaceStatus == GUISurfaceStatus.READY && mStatus != Status.RUNNING) initStep1();
    }

    /**
     * Called by:
     * - onResume(), if GUI preview surface already exists
     * - surfaceCreated(), if GUI preview surface has just been created
     */
    private void initStep1() {
        int response = ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION);
        if (response == PackageManager.PERMISSION_GRANTED) {
            initStep2();
        } else {
            long timeMs = System.currentTimeMillis();
            // remove the timing check once multiple onResume calls stop occuring
            if (timeMs - mLastRequestTimeMs > REQUEST_TIMEOUT_MS) {
                mLastRequestTimeMs = timeMs;
                ActivityCompat.requestPermissions(this, new String[]{CAMERA_PERMISSION}, REQUEST_ID);
            }
            mStatus = Status.DENIED;
            update();
        }
    }

    /**
     * Called by:
     * - initStep1(), if camera permissions already granted
     * - onRequestPermissionsResult(), if camera permissions have just been granted
     */
    private void initStep2() {
        mEGL = new EGL();
        mDisplaySurface = mEGL.makeSurface(getGUISurfaceView().getHolder().getSurface());
        mDisplaySurface.makeCurrent();
        mRenderer = new Renderer(mHandler);
        mCapture = new CameraCapture(mRenderer.getInputTexture());
        CyclicBuffer buf = new CyclicBuffer(mCapture.getBitRate(), mCapture.getFrameRate(),
                BUFFER_SIZE_SEC);
        mEncodeThread = new EncodeThread(mCapture.getMediaFormat(), buf, mHandler);
        mSaveMovieThread = new SaveMovieThread(buf, mHandler);
        mEncodeThread.start();
        mSaveMovieThread.start();
        mEncoderSurface = mEGL.makeSurface(mEncodeThread.getInputSurface());
        mStatus = Status.RUNNING;
        update();
    }

    @Override
    public void onRequestPermissionsResult(int requestID, @NonNull String[] permissionList,
                                           @NonNull int[] grantedList) {
        if (requestID != REQUEST_ID) return;
        if (permissionList.length < 1) return;
        if (!permissionList[0].equals(CAMERA_PERMISSION)) return;
        if (grantedList[0] != PackageManager.PERMISSION_GRANTED) return;
        initStep2();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mStatus = Status.STOPPED;
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

    private void frameAvailable() {
        if (mStatus != Status.RUNNING) return;

        // draw onto mDisplaySurface
        mDisplaySurface.makeCurrent();
        SurfaceView guiSurface = getGUISurfaceView();
        int guiWidth = guiSurface.getWidth();
        int guiHeight = guiSurface.getHeight();
        GLES20.glViewport(0, 0, guiWidth, guiHeight);
        mRenderer.drawRectangle();
        mDisplaySurface.swapBuffers();

        // draw onto mEncoderSurface
        mEncoderSurface.makeCurrent();
        int encWidth = mCapture.getWidth();
        int encHeight = mCapture.getHeight();
        GLES20.glViewport(0, 0, encWidth, encHeight);
        mRenderer.drawRectangle();
        mEncodeThread.getHandler().sendFlush();
        mEncoderSurface.presentationTime(mRenderer.getTimestamp());
        mEncoderSurface.swapBuffers();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        mGUISurfaceStatus = GUISurfaceStatus.READY;
        initStep1();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mGUISurfaceStatus = GUISurfaceStatus.NOT_READY;
    }

    private enum Status {
        STOPPED, RUNNING, DENIED
    }

    private enum SaveStatus {
        NOT_SAVING, SAVING
    }

    private enum GUISurfaceStatus {
        NOT_READY, READY
    }

    private static class Handler extends android.os.Handler implements SaveMovieThread.Callback,
            EncodeThread.Callback, Renderer.Callback {
        private static final int FLUSH_COMPLETED = 1;
        private static final int SAVE_COMPLETED = 2;
        private static final int FRAME_AVAILABLE = 3;
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
        public void onFrameAvailable() {
            sendMessage(obtainMessage(FRAME_AVAILABLE));
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
                    activity.saveCompleted((Boolean)msg.obj);
                    break;
                case FRAME_AVAILABLE:
                    activity.frameAvailable();
                    break;
                default:
                    break;
            }
        }
    }
}
