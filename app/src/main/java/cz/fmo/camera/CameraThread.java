package cz.fmo.camera;

import android.media.MediaFormat;
import android.opengl.GLES20;
import android.support.annotation.Nullable;
import android.view.Surface;

import cz.fmo.graphics.CameraFrameRenderer;
import cz.fmo.graphics.EGL;
import cz.fmo.graphics.FontRenderer;
import cz.fmo.graphics.TriangleStripRenderer;
import cz.fmo.util.GenericThread;

/**
 * A separate thread to configure and perform video capture. The captured frames are provided using
 * two distinct mechanisms. Firstly, one can add surfaces via the addTarget() method; these will
 * be drawn on using OpenGL. Secondly, one can implement the onCameraFrame() callback, which will
 * receive the captured frames as a byte array.
 */
public class CameraThread extends GenericThread<CameraThreadHandler> {
    private final Callback mCb;
    private final java.util.ArrayList<Target> mTargets = new java.util.ArrayList<>();
    private EGL mEGL;
    private CameraFrameRenderer mCameraFrameRenderer;
    private TriangleStripRenderer mTriangleStripRenderer;
    private FontRenderer mFontRenderer;
    private CameraCapture mCapture;

    /**
     * The constructor selects and opens a suitable camera. All methods can be called afterwards.
     * Precondition: camera permission must be granted, otherwise the camera initialization will
     * fail.
     */
    public CameraThread(@Nullable Callback cb, int preferWidth, int preferHeight) {
        super("CameraThread");
        mCb = cb;
        mCapture = new CameraCapture(mCb, preferWidth, preferHeight);
    }

    /**
     * Adds a surface that the thread will draw onto using OpenGL. Multiple surfaces can be added.
     * Call this method before the start() method has been called.
     */
    public void addTarget(Target target) {
        mTargets.add(target);
    }

    /**
     * This method is called once the thread is running, but before it starts receiving events.
     * Since we are running in the correct thread, we are safe to establish an EGL context and
     * other entities that depend on having it. Finally, we start the capture.
     */
    @Override
    protected void setup(CameraThreadHandler handler) {
        mEGL = new EGL();

        mTargets.add(new DummyCameraTarget());

        for (Target target : mTargets) {
            target.initEGL(mEGL);
        }

        mCameraFrameRenderer = new CameraFrameRenderer();
        mCameraFrameRenderer.getInputTexture().setOnFrameAvailableListener(handler);
        mTriangleStripRenderer = new TriangleStripRenderer();
        mFontRenderer = new FontRenderer();

        mCapture.start(mCameraFrameRenderer.getInputTexture());
    }

    /**
     * This method is called while the thread is running, but after it had stopped receiving events.
     * Capture is stopped and various cleanup is performed.
     */
    @Override
    protected void teardown() {
        mCapture.stop();

        if (mFontRenderer != null) {
            mFontRenderer.release();
            mFontRenderer = null;
        }

        if (mTriangleStripRenderer != null) {
            mTriangleStripRenderer.release();
            mTriangleStripRenderer = null;
        }

        if (mCameraFrameRenderer != null) {
            mCameraFrameRenderer.release();
            mCameraFrameRenderer = null;
        }

        for (Target target : mTargets) {
            target.release();
        }
        mTargets.clear();

        if (mEGL != null) {
            mEGL.release();
            mEGL = null;
        }

        if (mCapture != null) {
            mCapture.release();
            mCapture = null;
        }
    }

    /**
     * This method is triggered by the SurfaceTexture.onFrameAvailable() event. It is called every
     * frame, as soon as the next image is ready to draw. Each registered surface is drawn on using
     * OpenGL.
     */
    void frameAvailable() {
        if (mCb != null) mCb.onCameraRender();

        for (Target target : mTargets) {
            target.render(this);
        }
    }

    @Override
    protected CameraThreadHandler makeHandler() {
        return new CameraThreadHandler(this);
    }

    public MediaFormat getMediaFormat() {
        return mCapture.getMediaFormat();
    }

    public int getWidth() {
        return mCapture.getWidth();
    }

    public int getHeight() {
        return mCapture.getHeight();
    }

    public int getBitRate() {
        return mCapture.getBitRate();
    }

    public float getFrameRate() {
        return mCapture.getFrameRate();
    }

    CameraFrameRenderer getCameraFrameRenderer() {
        return mCameraFrameRenderer;
    }

    TriangleStripRenderer getTriangleStripRenderer() {
        return mTriangleStripRenderer;
    }

    FontRenderer getFontRenderer() { return mFontRenderer; }

    public interface Callback extends CameraCapture.Callback {
        void onCameraRender();
    }

    /**
     * Encapsulates a surface that is to be drawn to whenever a new camera frame is received.
     */
    public static abstract class Target {
        protected final int mWidth;
        protected final int mHeight;
        private Surface mSurface;
        private EGL.Surface mEglSurface;

        public Target(Surface surface, int width, int height) {
            mSurface = surface;
            mWidth = width;
            mHeight = height;
        }

        /**
         * Additionally changes the surface to render to. Must be used before initEGL() is called.
         */
        protected void setSurface(Surface surface) {
            mSurface = surface;
        }

        /**
         * Create the associated EGL.Surface. For that, the EGL context must be already established
         * on this thread.
         */
        void initEGL(EGL egl) {
            mEglSurface = egl.makeSurface(mSurface);
            mEglSurface.makeCurrent();
        }

        void release() {
            if (mEglSurface != null) {
                mEglSurface.release();
                mEglSurface = null;
            }
        }

        /**
         * Draw onto the target surface using OpenGL (high level).
         */
        void render(CameraThread thread) {
            mEglSurface.makeCurrent();
            GLES20.glViewport(0, 0, mWidth, mHeight);
            renderImpl(thread);
            mEglSurface.swapBuffers();
        }

        /**
         * Draw onto the target surface using OpenGL (low level).
         */
        abstract void renderImpl(CameraThread thread);
    }

}
