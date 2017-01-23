package cz.fmo.recording;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.support.annotation.NonNull;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A class encapsulating android.hardware.camera2.
 * <p>
 * On construction, the best camera (according to preferred parameters) is selected. After that, the
 * provided callback is called. Do not call any methods of this class before the callback is
 * triggered.
 * <p>
 * Use the addTarget() method to register output surfaces. Use the start() method to start receiving
 * frames.
 * <p>
 * To stop receiving frames, call the release() method.
 */
class Camera {
    private static final String MIME_TYPE = "video/avc";
    private static final int PREFER_FORMAT = ImageFormat.YUV_420_888;
    private static final int PREFER_WIDTH = 1280; // pixels
    private static final int PREFER_HEIGHT = 720; // pixels
    private static final int PREFER_FRAME_RATE = 30; // frames per second
    private static final int PREFER_BIT_RATE = 6 * 1024 * 1024; // bits per second
    private static final int PREFER_I_FRAME_INTERVAL = 1; // seconds
    private static final int PREFER_AREA = PREFER_WIDTH * PREFER_HEIGHT; // pixels^2
    private static final double PREFER_ASPECT = PREFER_WIDTH / (double) PREFER_HEIGHT;
    private static final long PREFER_FRAME_TIME = (long) (1e9 / PREFER_FRAME_RATE); // nanoseconds
    private final Callback mCb;
    private final SessionCallback mSessCb = new SessionCallback();
    private final CaptureCallback mCapCb = new CaptureCallback();
    private final ArrayList<Surface> mTargets = new ArrayList<>(6);
    private CameraDevice mDevice;
    private CaptureRequest mRequest;
    private CameraCaptureSession mSession;
    private String mCamId;
    private Size mSize;
    private int mFrameRate;
    private int mOrientation;
    private boolean mReleased = false;
    /**
     * Selects a suitable camera, and sets the callback to be called once the camera is ready.
     * Do not call any methods of this class before the callback is triggered.
     */
    Camera(Activity activity, Callback cb) {
        mCb = cb;

        try {
            CameraManager man = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            selectBestCamera(man);
            DeviceCallback devCb = new DeviceCallback();
            man.openCamera(mCamId, devCb, null);
        } catch (CameraAccessException e) {
            error(Error.INACCESSIBLE);
        } catch (SecurityException e) {
            error(Error.PERMISSION_FAIL);
        }
    }

    /**
     * Selects a suitable camera based on resolution, aspect ratio, direction of facing, and the
     * ability to produce data at the preferred frame rate.
     *
     * @param manager A CameraManager instance retrieved from activity.getSystemService.
     * @throws CameraAccessException When retrieving camera information inexplicably fails.
     */
    private void selectBestCamera(CameraManager manager) throws CameraAccessException {
        int bestScore = Integer.MAX_VALUE;
        String bestCamera = null;
        Size bestSize = null;
        long bestFrameTimeNs = 1;
        int bestOrientation = 0;

        for (String camera : manager.getCameraIdList()) {
            // fetch camera characteristics and configuration map
            CameraCharacteristics chars = manager.getCameraCharacteristics(camera);
            StreamConfigurationMap map;
            map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) continue;
            // find sizes that work for all the desired kinds of output
            Size[] sizes1 = map.getOutputSizes(MediaCodec.class);
            Size[] sizes2 = map.getOutputSizes(PREFER_FORMAT);
            Size[] sizes3 = map.getOutputSizes(SurfaceHolder.class);
            java.util.Set<Size> sizes = new java.util.HashSet<>();
            sizes.addAll(Arrays.asList(sizes1));
            sizes.retainAll(Arrays.asList(sizes2));
            sizes.retainAll(Arrays.asList(sizes3));

            // select a size that is the closest (in area and aspect ratio) to the preferred one,
            // also consider the frame time
            int bestSizeScore = Integer.MAX_VALUE;
            long bestFrameTimeNsInner = 1;
            Size bestSizeInner = null;
            for (Size size : sizes) {
                // consider area in pixels^2
                int area = size.getWidth() * size.getHeight();
                int areaPenalty = Math.abs(area - PREFER_AREA);

                // consider aspect ratio
                double aspect = size.getWidth() / (double) size.getHeight();
                int aspectPenalty = (int) (PREFER_WIDTH * Math.abs(aspect - PREFER_ASPECT));

                // consider frame lag in tens of nanoseconds
                long time1Ns = map.getOutputMinFrameDuration(MediaCodec.class, size);
                long time2Ns = map.getOutputMinFrameDuration(PREFER_FORMAT, size);
                long time3Ns = map.getOutputMinFrameDuration(SurfaceHolder.class, size);
                long stall1Ns = map.getOutputStallDuration(MediaCodec.class, size);
                long stall2Ns = map.getOutputStallDuration(PREFER_FORMAT, size);
                long stall3Ns = map.getOutputStallDuration(SurfaceHolder.class, size);
                long timeNs = Math.max(time1Ns, Math.max(time2Ns, time3Ns));
                long stallNs = Math.max(stall1Ns, Math.max(stall2Ns, stall3Ns));
                long frameTimeNs = Math.max(timeNs + stallNs, PREFER_FRAME_TIME);
                int frameTimePenalty = (int) ((PREFER_FRAME_TIME - frameTimeNs) / 10);

                // merge into a single score value, pick the size with the best one
                int sizeScore = aspectPenalty + areaPenalty + frameTimePenalty;
                if (sizeScore < bestSizeScore) {
                    bestSizeScore = sizeScore;
                    bestSizeInner = size;
                    bestFrameTimeNsInner = frameTimeNs;
                }
            }
            if (bestSizeInner == null) continue;

            // consider the camera facing
            int facingScore = 0;
            Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
            if (facing == null || facing != CameraCharacteristics.LENS_FACING_FRONT) {
                facingScore = (int) 1e5;
            }

            // merge into a single score value, pick the size with the best one
            int score = bestSizeScore + facingScore;
            if (score < bestScore) {
                bestScore = score;
                bestCamera = camera;
                bestSize = bestSizeInner;
                bestFrameTimeNs = bestFrameTimeNsInner;

                // query camera orientation
                Integer orientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
                bestOrientation = (orientation == null) ? 0 : orientation;
            }
        }

        if (bestCamera == null) {
            mCamId = null;
            mSize = null;
            mFrameRate = 0;
            mOrientation = 0;
            throw new RuntimeException("No suitable camera found!");
        }

        mCamId = bestCamera;
        mSize = bestSize;
        mFrameRate = (int) Math.round(1e9 / bestFrameTimeNs);
        mOrientation = bestOrientation;
    }

    /**
     * Release all resources and report an error.
     */
    private void error(Error error) {
        release();
        mCb.onCameraError(error);
    }

    /**
     * Registers an output surface. The surface will be used as one of the targets for video
     * capture once start() is called.
     */
    void addTarget(Surface surface) {
        mTargets.add(surface);
    }

    /**
     * Starts writing frames into the target surfaces that have previously been registered with the
     * addTarget() method.
     */
    void start() {
        try {
            CaptureRequest.Builder b = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            for (Surface surface : mTargets) {
                b.addTarget(surface);
            }
            mRequest = b.build();

            mDevice.createCaptureSession(mTargets, mSessCb, null);
        } catch (CameraAccessException e) {
            error(Error.DISCONNECTED);
        }
    }

    /**
     * Called by SessionCallback.onConfigured().
     */
    private void sendCaptureRequest() {
        try {
            mSession.setRepeatingRequest(mRequest, mCapCb, null);
        } catch (CameraAccessException e) {
            error(Error.DISCONNECTED);
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

        mTargets.clear();
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
        return mSize.getWidth();
    }

    int getHeight() {
        return mSize.getHeight();
    }

    int getBitRate() {
        return PREFER_BIT_RATE;
    }

    int getFrameRate() {
        return mFrameRate;
    }

    int getFormat() {
        return PREFER_FORMAT;
    }

    int getOrientation() {
        return mOrientation;
    }

    enum Error {
        PERMISSION_FAIL, CONFIGURE_FAIL, INACCESSIBLE, DISCONNECTED
    }

    interface Callback {
        void onCameraError(Error error);

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
            error(Error.DISCONNECTED);
        }

        @Override
        public void onError(@NonNull CameraDevice device, int error) {
            error(Error.DISCONNECTED);
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
            error(Error.CONFIGURE_FAIL);
        }
    }

    private class CaptureCallback extends CameraCaptureSession.CaptureCallback {
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            mCb.onCameraFrame();
        }
    }
}
