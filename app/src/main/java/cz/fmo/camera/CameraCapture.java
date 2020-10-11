package cz.fmo.camera;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaFormat;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import cz.fmo.util.Config;

/**
 * A class encapsulating android.hardware.Camera.
 * <p>
 * On construction, the best camera (according to preferred parameters) is selected. Use the start()
 * to start receiving frames into the provided SurfaceTexture. Additionally, raw data can be
 * received using the onCameraFrame() method of the callback.
 * <p>
 * To stop receiving frames, call the release() method.
 */
@SuppressWarnings("deprecation")
class CameraCapture implements Camera.PreviewCallback {
    private static final String MIME_TYPE = "video/avc";
    private static final float PREFER_FRAME_RATE = 30.f; // frames per second
    private static final int PREFER_BIT_RATE = 6 * 1024 * 1024; // bits per second
    private static final int PREFER_I_FRAME_INTERVAL = 1; // seconds
    private static final int IMAGE_FORMAT = ImageFormat.NV21;
    private static final int BITS_PER_PIXEL = ImageFormat.getBitsPerPixel(IMAGE_FORMAT);
    private static final String PREVIEW_VIDEO_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + "/Camera/1701.mp4";
    private final Callback mCb;
    private final int mPreferWidth;
    private final int mPreferHeight;
    private final boolean mPreferFrontFacing;
    private Camera mCamera;
    private Camera.Size mSize = null;
    private float mFrameRate = 0;
    private boolean mStarted = false;
    private boolean mReleased = false;

    /**
     * Selects a suitable camera and opens it. The provided callback is used to report errors and
     * provide image data.
     */
    CameraCapture(@Nullable Callback cb, Config config) {
        mCb = cb;
        mPreferWidth = config.isHighResolution() ? 1920 : 1280;
        mPreferHeight = config.isHighResolution() ? 1080 : 720;
        mPreferFrontFacing = config.isFrontFacing();
        int bestCam = selectCamera();

        if (bestCam < 0) {
            if (mCb != null) mCb.onCameraError();
            return;
        }

        mCamera = Camera.open(bestCam);

        if (mCamera == null) {
            if (mCb != null) mCb.onCameraError();
            return;
        }

        configureCamera();
    }

    /**
     * Modifies and updates camera parameters according to preferences.
     */
    private void configureCamera() {
        Camera.Parameters params = mCamera.getParameters();

        configureSize(params);
        configureFrameRate(params);
        configureOther(params);

        mCamera.setParameters(params);
    }

    /**
     * Modifies camera width and height parameters. Lists all supported sizes and chooses the size
     * that is closest to PREFER_WIDTH x PREFER_HEIGHT.
     */
    private void configureSize(Camera.Parameters params) {
        Camera.Size bestSize = null;
        int bestScore = Integer.MAX_VALUE;

        for (Camera.Size size : params.getSupportedPreviewSizes()) {
            int dWidth = Math.abs(size.width - mPreferWidth);
            int dHeight = Math.abs(size.height - mPreferHeight);
            int score = dWidth + dHeight;

            if (score < bestScore) {
                bestScore = score;
                bestSize = size;
            }
        }

        if (bestSize != null) {
            params.setPreviewSize(bestSize.width, bestSize.height);
            mSize = bestSize;
        }
    }

    /**
     * Modifies camera minimum and maximum frames per second parameters. Lists all supported frame
     * rate ranges and chooses the one closest to PREFER_FRAME_RATE.
     */
    private void configureFrameRate(Camera.Parameters params) {
        int[] bestRange = null;
        int bestScore = Integer.MAX_VALUE;
        int preferFp1000s = Math.round(PREFER_FRAME_RATE * 1000.f);

        for (int[] range : params.getSupportedPreviewFpsRange()) {
            int dLow = Math.abs(range[0] - preferFp1000s);
            int dHigh = Math.abs(range[1] - preferFp1000s);
            int dApart = Math.abs(range[1] - range[0]);
            int score = dLow + dHigh + 4 * dApart; // strongly prefer fixed frame rate

            if (score < bestScore) {
                bestScore = score;
                bestRange = range;
            }
        }

        if (bestRange != null) {
            params.setPreviewFpsRange(bestRange[0], bestRange[1]);
            mFrameRate = (float) (bestRange[0] + bestRange[1]) / (2 * 1000.f);
        }
    }

    /**
     * Does camera configuration other than size or frame rate, including setting up the format and
     * focus mode.
     */
    private void configureOther(Camera.Parameters params) {
        params.setRecordingHint(true);
        params.setPreviewFormat(IMAGE_FORMAT);

        // focus mode: set to "continuous-video" if available
        for (String mode : params.getSupportedFocusModes()) {
            if (mode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                params.setFocusMode(mode);
            }
        }
    }

    /**
     * Starts writing frames into the provided target texture and sending raw data via the callback.
     */
    public void start(@NonNull SurfaceTexture outputTexture) {
        if (mCamera == null) return;
        try {
            mCamera.setPreviewTexture(outputTexture);
            if (mCb != null) {
                for (int i = 0; i < 4; i++) {
                    byte[] buffer = new byte[(mSize.width * mSize.height * BITS_PER_PIXEL) / 8];
                    mCamera.addCallbackBuffer(buffer);
                }
                mCamera.setPreviewCallbackWithBuffer(this);
            }
        } catch (java.io.IOException e) {
            if (mCb != null) mCb.onCameraError();
            mCamera = null;
            return;
        }
        mCamera.startPreview();
        mStarted = true;
    }

    /**
     * Receives frame from the camera as raw, YUV 4:2:0 single plane data.
     */
    @Override
    public void onPreviewFrame(byte[] dataYUV420SP, Camera camera) {
        mCb.onCameraFrame(dataYUV420SP);
        mCamera.addCallbackBuffer(dataYUV420SP);
    }

    /**
     * Stops writing frames.
     */
    public void stop() {
        if (mCamera == null) return;
        mCamera.setPreviewCallback(null);
        mCamera.stopPreview();
        mStarted = false;
    }

    /**
     * Stops writing frames into the output texture and releases all resources.
     */
    public void release() {
        if (mReleased) return;
        mReleased = true;

        if (mStarted) {
            stop();
        }

        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * @return index of some back-facing camera, if available; 0 otherwise
     */
    private int selectCamera() {
        int nCam = Camera.getNumberOfCameras();
        int preferredFacing = mPreferFrontFacing ? Camera.CameraInfo.CAMERA_FACING_FRONT :
                Camera.CameraInfo.CAMERA_FACING_BACK;
        if (nCam == 0) return -1;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();

        for (int i = 0; i < nCam; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == preferredFacing) {
                // you might want to keep the chosen CameraInfo object, if interested in proper
                // camera rotation relative to screen
                return i;
            }
        }

        return 0;
    }

    /**
     * @return a MediaFormat object describing a video format compatible with the camera output
     */
    MediaFormat getMediaFormat() {
        MediaFormat f = MediaFormat.createVideoFormat(MIME_TYPE, mSize.width, mSize.height);
        f.setInteger(MediaFormat.KEY_BIT_RATE, PREFER_BIT_RATE);
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatSurface);
        f.setFloat(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, PREFER_I_FRAME_INTERVAL);
        return f;
    }

    int getWidth() {
        return mSize.width;
    }

    int getHeight() {
        return mSize.height;
    }

    int getBitRate() {
        return PREFER_BIT_RATE;
    }

    float getFrameRate() {
        return mFrameRate;
    }

    public interface Callback {
        void onCameraFrame(byte[] dataYUV420SP);

        void onCameraError();
    }
}
