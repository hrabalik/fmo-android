package cz.fmo.recording;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import java.util.List;

/**
 * A class encapsulating android.hardware.camera2.
 * <p>
 * On construction, the best camera (according to preferred parameters) is selected. After that, the
 * provided callback is called. Do not call any methods of this class before the callback is
 * triggered.
 * <p>
 * Use the start() method to start receiving frames into a given target surface texture.
 * <p>
 * To stop receiving frames, call the release() method.
 */
class CameraCapture2 {
    private static final String MIME_TYPE = "video/avc";
    private static final int PREFER_WIDTH = 1280; // pixels
    private static final int PREFER_HEIGHT = 720; // pixels
    private static final int PREFER_FRAME_RATE = 30; // frames per second
    private static final int PREFER_BIT_RATE = 6 * 1024 * 1024; // bits per second
    private static final int PREFER_I_FRAME_INTERVAL = 1; // seconds

    private final Callback mCb;
    private final SessionCallback mSessCb = new SessionCallback();
    private final CaptureCallback mCapCb = new CaptureCallback();
    private CameraDevice mDevice;
    private CaptureRequest mRequest;
    private CameraCaptureSession mSession;
    private boolean mReleased = false;

    /**
     * Selects a suitable camera, and sets the callback to be called once the camera is ready.
     * Do not call any methods of this class before the callback is triggered.
     */
    CameraCapture2(Activity activity, Callback cb) {
        mCb = cb;

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            error();
        }

        try {
            CameraManager man = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            String bestCam = selectBestCamera(man);
            DeviceCallback devCb = new DeviceCallback();
            man.openCamera(bestCam, devCb, null);
        } catch (CameraAccessException | SecurityException e) {
            error();
        }
    }

    private String selectBestCamera(CameraManager manager) throws CameraAccessException {
        int bestScore = Integer.MAX_VALUE;
        String bestCamera = null;
        Range<Integer> bestFpsRange = null;
        Size bestSize = null;

        for (String camera : manager.getCameraIdList()) {
            // fetch camera characteristics and configuration map
            CameraCharacteristics chars = manager.getCameraCharacteristics(camera);
            StreamConfigurationMap map;
            map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) continue;

            Size[] sizes1 = map.getOutputSizes(android.media.MediaCodec.class);
            Size[] sizes2 = map.getOutputSizes(android.media.ImageReader.class);
            Size[] sizes3 = map.getOutputSizes(android.view.SurfaceHolder.class);
            Size[] sizes4 = map.getOutputSizes(android.graphics.SurfaceTexture.class);

            /*
            int facingFactor = getFacingFactor(chars.get(CameraCharacteristics.LENS_FACING));


            for (Range<Integer> fpsRange : map.getHighSpeedVideoFpsRanges()) {
                int fpsScore = getFpsScore(fpsRange);

                for (Size size : map.getHighSpeedVideoSizesFor(fpsRange)) {
                    int aspectFactor = getAspectFactor(size);
                    int sizeScore = getSizeScore(size);

                    int score = facingFactor * aspectFactor * (fpsScore + sizeScore);

                    if (score < bestScore) {
                        bestScore = score;
                        bestCamera = camera;
                        bestFpsRange = fpsRange;
                        bestSize = size;
                    }
                }
            }*/

            int score = 0;
            if (score < bestScore) {
                bestScore = score;
                bestCamera = camera;
            }
        }

        if (bestCamera == null) {
            throw new RuntimeException("No suitable camera found!");
        }
        return bestCamera;
    }

    /*private int getFacingFactor(Integer facing) {
        if (facing == null) {
            return 10;
        }
        else if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            return 3;
        }
        else {
            return 4;
        }
    }*/

    /*private int getAspectFactor(Size size) {
        if (size.getHeight() < size.getWidth()) {
            return 4;
        }
        else {
            return 5;
        }
    }*/

    /*private int getFpsScore(Range<Integer> range) {
        int offset = Math.abs(range.getLower() - PREFER_FRAME_RATE);
        int delta = Math.abs(range.getUpper() - range.getLower());
        return 30000 * offset + 60000 * delta;
    }*/

    /*private int getSizeScore(Size size) {
        int numPx = size.getWidth() * size.getHeight();
        int idealPx = PREFER_WIDTH * PREFER_HEIGHT;
        return Math.abs(numPx - idealPx);
    }*/

    private void error() {
        release();
        mCb.onCameraError();
    }

    /**
     * Starts writing frames into the provided target surfaces.
     */
    void start(List<Surface> outputSurfaces) {
        try {
            CaptureRequest.Builder b = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            for (Surface surface : outputSurfaces) {
                b.addTarget(surface);
            }
            mRequest = b.build();

            mDevice.createCaptureSession(outputSurfaces, mSessCb, null);
        } catch (CameraAccessException e) {
            error();
        }
    }

    private void sendCaptureRequest() {
        try {
            mSession.setRepeatingRequest(mRequest, mCapCb, null);
        } catch (CameraAccessException e) {
            error();
        }
    }

    /**
     * Stops writing frames into the output surfaces and releases all resources.
     */
    public void release() {
        if (mReleased) return;
        mReleased = true;

        if (mSession != null) {
            mSession.close();
            mSession = null;
        }

        if (mDevice != null) {
            mDevice.close();
            mDevice = null;
        }
    }

    /**
     * @return a MediaFormat object describing a video format compatible with the camera output
     */
    MediaFormat getMediaFormat() {
        MediaFormat f = MediaFormat.createVideoFormat(MIME_TYPE, getWidth(), getHeight());
        f.setInteger(MediaFormat.KEY_BIT_RATE, getBitRate());
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        f.setInteger(MediaFormat.KEY_FRAME_RATE, getFrameRate());
        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, PREFER_I_FRAME_INTERVAL);
        return f;
    }

    int getWidth() {
        return PREFER_WIDTH; // TODO get from camera info
    }

    int getHeight() {
        return PREFER_HEIGHT; // TODO get from camera info
    }

    int getBitRate() {
        return PREFER_BIT_RATE;
    }

    int getFrameRate() {
        return PREFER_FRAME_RATE; // TODO get from camera info
    }

    interface Callback {
        void onCameraError();

        void onCameraOpened();

        void onCameraFrame();
    }

    private class DeviceCallback extends CameraDevice.StateCallback {
        @Override
        public void onOpened(@NonNull CameraDevice device) {
            mDevice = device;
            mCb.onCameraOpened();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice device) {
            error();
        }

        @Override
        public void onError(@NonNull CameraDevice device, int error) {
            error();
        }
    }

    private class SessionCallback extends CameraCaptureSession.StateCallback {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mSession = session;
            sendCaptureRequest();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            error();
        }
    }

    private class CaptureCallback extends CameraCaptureSession.CaptureCallback {
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            mCb.onCameraFrame(); // TODO send metadata
        }
    }
}
