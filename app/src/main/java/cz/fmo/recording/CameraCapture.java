//package cz.fmo.recording;
//
//import android.graphics.SurfaceTexture;
//import android.hardware.Camera;
//import android.media.MediaCodecInfo.CodecCapabilities;
//import android.media.MediaFormat;
//
///**
// * A class encapsulating android.hardware.Camera.
// * <p>
// * On construction, the best camera (according to preferred parameters) is selected. Your target
// * texture will immediately start receiving frames.
// * <p>
// * To stop receiving frames, call the release() method.
// */
//class CameraCapture {
//    private static final String MIME_TYPE = "video/avc";
//    private static final int PREFER_WIDTH = 1920; // pixels
//    private static final int PREFER_HEIGHT = 1080; // pixels
//    private static final float PREFER_FRAME_RATE = 30.f; // frames per second
//    private static final int PREFER_BIT_RATE = 6 * 1024 * 1024; // bits per second
//    private static final int PREFER_I_FRAME_INTERVAL = 1; // seconds
//
//    private final Camera mCamera;
//    private final Camera.Parameters mParams;
//    private Camera.Size mSize = null;
//    private float mFrameRate = 0;
//    private boolean mStarted = false;
//    private boolean mReleased = false;
//
//    /**
//     * Selects a suitable camera, and sets the callback to be called once the camera is ready.
//     * Do not call any methods before the callback is triggered.
//     */
//    CameraCapture(Callback cb) {
//        int bestCam = selectCamera();
//        mCamera = Camera.open(bestCam);
//        if (mCamera == null) throw new RuntimeException("Failed to open camera");
//        mParams = mCamera.getParameters();
//        configureCamera();
//        cb.onCameraReady();
//    }
//
//    /**
//     * Starts writing frames into the provided target texture.
//     */
//    void start(SurfaceTexture outputTexture) {
//        try {
//            mCamera.setPreviewTexture(outputTexture);
//        } catch (java.io.IOException e) {
//            throw new RuntimeException("setOutputTexture() failed");
//        }
//        mCamera.startPreview();
//        mStarted = true;
//    }
//
//    /**
//     * Stops writing frames.
//     */
//    private void stop() {
//        mCamera.stopPreview();
//        mStarted = false;
//    }
//
//    /**
//     * Stops writing frames into the output texture and releases all resources.
//     */
//    public void release() {
//        if (mReleased) return;
//        mReleased = true;
//
//        if (mStarted) {
//            stop();
//        }
//
//        if (mCamera != null) {
//            mCamera.release();
//        }
//    }
//
//    /**
//     * @return index of some back-facing camera, if available; 0 otherwise
//     */
//    private int selectCamera() {
//        int nCam = Camera.getNumberOfCameras();
//        if (nCam == 0) throw new RuntimeException("No camera");
//        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//
//        for (int i = 0; i < nCam; i++) {
//            Camera.getCameraInfo(i, cameraInfo);
//            if (cameraInfo.orientation == Camera.CameraInfo.CAMERA_FACING_BACK) {
//                // you might want to keep the chosen CameraInfo object, if interested in proper
//                // camera rotation relative to screen
//                return i;
//            }
//        }
//
//        return 0;
//    }
//
//    /**
//     * Modifies and updates camera parameters according to preferences.
//     */
//    private void configureCamera() {
//        configureSize();
//        configureFrameRate();
//        mParams.setRecordingHint(true);
//        mCamera.setParameters(mParams);
//    }
//
//    /**
//     * Modifies camera width and height parameters. Lists all supported sizes and chooses the size
//     * that is closest to PREFER_WIDTH x PREFER_HEIGHT.
//     */
//    private void configureSize() {
//        Camera.Size bestSize = null;
//        int bestScore = Integer.MAX_VALUE;
//
//        for (Camera.Size size : mParams.getSupportedPreviewSizes()) {
//            if (size.width % 16 != 0 || size.height % 16 != 0) continue;
//            int dWidth = Math.abs(size.width - PREFER_WIDTH);
//            int dHeight = Math.abs(size.height - PREFER_HEIGHT);
//            int score = dWidth + dHeight;
//
//            if (score < bestScore) {
//                bestScore = score;
//                bestSize = size;
//            }
//        }
//
//        if (bestSize == null) throw new RuntimeException("No preview size");
//        mParams.setPreviewSize(bestSize.width, bestSize.height);
//        mSize = bestSize;
//    }
//
//    /**
//     * Modifies camera minimum and maximum frames per second parameters. Lists all supported frame
//     * rate ranges and chooses the one closest to PREFER_FRAME_RATE.
//     */
//    private void configureFrameRate() {
//        int[] bestRange = null;
//        int bestScore = Integer.MAX_VALUE;
//        int preferFp1000s = Math.round(PREFER_FRAME_RATE * 1000.f);
//
//        for (int[] range : mParams.getSupportedPreviewFpsRange()) {
//            int dLow = Math.abs(range[0] - preferFp1000s);
//            int dHigh = Math.abs(range[1] - preferFp1000s);
//            int dApart = Math.abs(range[1] - range[0]);
//            int score = dLow + dHigh + 4 * dApart; // strongly prefer fixed frame rate
//
//            if (score < bestScore) {
//                bestScore = score;
//                bestRange = range;
//            }
//        }
//
//        if (bestRange == null) throw new RuntimeException("No FPS range");
//        mParams.setPreviewFpsRange(bestRange[0], bestRange[1]);
//        mFrameRate = (float) (bestRange[0] + bestRange[1]) / (2 * 1000.f);
//    }
//
//    /**
//     * @return a MediaFormat object describing a video format compatible with the camera output
//     */
//    MediaFormat getMediaFormat() {
//        MediaFormat f = MediaFormat.createVideoFormat(MIME_TYPE, mSize.width, mSize.height);
//        f.setInteger(MediaFormat.KEY_BIT_RATE, PREFER_BIT_RATE);
//        f.setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatSurface);
//        f.setFloat(MediaFormat.KEY_FRAME_RATE, mFrameRate);
//        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, PREFER_I_FRAME_INTERVAL);
//        return f;
//    }
//
//    int getWidth() {
//        return mSize.width;
//    }
//
//    int getHeight() {
//        return mSize.height;
//    }
//
//    int getBitRate() {
//        return PREFER_BIT_RATE;
//    }
//
//    float getFrameRate() {
//        return mFrameRate;
//    }
//
//    interface Callback {
//        void onCameraReady();
//    }
//}
