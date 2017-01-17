package cz.fmo.recording;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
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
    private static final float PREFER_FRAME_RATE = 30.f; // frames per second
    private static final int PREFER_BIT_RATE = 6 * 1024 * 1024; // bits per second
    private static final int PREFER_I_FRAME_INTERVAL = 1; // seconds

    private final Callback mCb;
    private final CameraManager mManager;
    private final DeviceCallback mDevCb = new DeviceCallback();
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

        mManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameras = mManager.getCameraIdList();
            String bestCam = "";
            for (String id : cameras) {
                CameraCharacteristics chars = mManager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                bestCam = id;
            }
            mManager.openCamera(bestCam, mDevCb, null);
        } catch (CameraAccessException | SecurityException e) {
            error();
        }
    }

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
        f.setFloat(MediaFormat.KEY_FRAME_RATE, getFrameRate());
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

    float getFrameRate() {
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
