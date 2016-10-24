package cz.fmo;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaFormat;

public class CameraCapture {
    private static final String MIME_TYPE = "video/avc";
    private static final int PREFER_WIDTH = 1920; // pixels
    private static final int PREFER_HEIGHT = 1080; // pixels
    private static final float PREFER_FRAME_RATE = 30.f; // frames per second
    private static final int PREFER_BIT_RATE = 6 * 1024 * 1024; // bits per second
    private static final int PREFER_I_FRAME_INTERVAL = 1; // seconds

    private final Camera mCamera;
    private final Camera.CameraInfo mInfo = new Camera.CameraInfo();
    private final Camera.Parameters mParams;
    private Camera.Size mSize = null;
    private float mFrameRate = 0;
    private boolean mReleased = false;

    public CameraCapture() {
        int bestCam = selectCamera();
        mCamera = Camera.open(bestCam);
        if (mCamera == null) throw new RuntimeException("Failed to open camera");
        Camera.getCameraInfo(bestCam, mInfo);
        mParams = mCamera.getParameters();
        configureCamera();
    }

    private int selectCamera() {
        int nCam = Camera.getNumberOfCameras();
        if (nCam == 0) throw new RuntimeException("No camera");
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();

        for (int i = 0; i < nCam; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.orientation == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return i;
            }
        }

        return 0;
    }

    private void configureCamera() {
        configureSize();
        configureFrameRate();
        mParams.setRecordingHint(true);
        mCamera.setParameters(mParams);
    }

    private void configureSize() {
        Camera.Size bestSize = null;
        int bestScore = Integer.MAX_VALUE;

        for (Camera.Size size : mParams.getSupportedPreviewSizes()) {
            if (size.width % 16 != 0 || size.height % 16 != 0) continue;
            int dWidth = Math.abs(size.width - PREFER_WIDTH);
            int dHeight = Math.abs(size.height - PREFER_HEIGHT);
            int score = dWidth + dHeight;

            if (score < bestScore) {
                bestScore = score;
                bestSize = size;
            }
        }

        if (bestSize == null) throw new RuntimeException("No preview size");
        mParams.setPreviewSize(bestSize.width, bestSize.height);
        mSize = bestSize;
    }

    private void configureFrameRate() {
        int[] bestRange = null;
        int bestScore = Integer.MAX_VALUE;
        int preferFp1000s = Math.round(PREFER_FRAME_RATE * 1000.f);

        for (int[] range : mParams.getSupportedPreviewFpsRange()) {
            int dLow = Math.abs(range[0] - preferFp1000s);
            int dHigh = Math.abs(range[1] - preferFp1000s);
            int dApart = Math.abs(range[1] - range[0]);
            int score = dLow + dHigh + 4 * dApart; // strongly prefer fixed frame rate

            if (score < bestScore) {
                bestScore = score;
                bestRange = range;
            }
        }

        if (bestRange == null) throw new RuntimeException("No FPS range");
        mParams.setPreviewFpsRange(bestRange[0], bestRange[1]);
        mFrameRate = (float) (bestRange[0] + bestRange[1]) / (2 * 1000.f);
    }

    public void release() {
        if (mReleased) return;
        mReleased = true;

        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
        }
    }

    public void start() {
        mCamera.startPreview();
    }

    public void setTexture(SurfaceTexture texture) {
        try {
            mCamera.setPreviewTexture(texture);
        } catch (java.io.IOException e) {
            throw new RuntimeException("setTexture() failed");
        }
    }

    public int getWidth() {
        return mSize.width;
    }

    public int getHeight() {
        return mSize.height;
    }

    public int getBitRate() {
        return PREFER_BIT_RATE;
    }

    public float getFrameRate() {
        return mFrameRate;
    }

    public String getMIMEType() {
        return MIME_TYPE;
    }

    public MediaFormat getMediaFormat() {
        MediaFormat f = MediaFormat.createVideoFormat(MIME_TYPE, mSize.width, mSize.height);
        f.setInteger(MediaFormat.KEY_BIT_RATE, PREFER_BIT_RATE);
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatSurface);
        f.setFloat(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, PREFER_I_FRAME_INTERVAL);
        return f;
    }
}
