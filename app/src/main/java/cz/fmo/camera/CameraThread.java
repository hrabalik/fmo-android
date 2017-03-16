package cz.fmo.camera;

import android.media.MediaFormat;
import android.opengl.GLES20;
import android.support.annotation.Nullable;
import android.view.Surface;

import cz.fmo.graphics.EGL;
import cz.fmo.graphics.Renderer;
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
    private Renderer mRenderer;
    private CameraCapture mCapture;

    /**
     * The constructor selects and opens a suitable camera. All methods can be called afterwards.
     * Precondition: camera permission must be granted, otherwise the camera initialization will
     * fail.
     */
    public CameraThread(@Nullable Callback cb) {
        super("CameraThread");
        mCb = cb;
        mCapture = new CameraCapture(mCb);
    }

    /**
     * Adds a surface that the thread will draw onto using OpenGL. Multiple surfaces can be added.
     * Call this method before the start() method has been called.
     */
    public Target addTarget(Surface surface, int width, int height) {
        Target target = new Target(surface, width, height);
        mTargets.add(target);
        return target;
    }

    /**
     * This method is called once the thread is running, but before it starts receiving events.
     * Since we're running in the correct thread, we are safe to establish an EGL context and
     * other entities that depend on having it. Finally, we start the capture.
     */
    @Override
    protected void setup(CameraThreadHandler handler) {
        mEGL = new EGL();

        for (Target target : mTargets) {
            target.initEGL(mEGL);
        }

        mRenderer = new Renderer(handler);

        mCapture.start(mRenderer.getInputTexture());
    }

    /**
     * This method is called while the thread is running, but after it had stopped receiving events.
     * Capture is stopped and various cleanup is performed.
     */
    @Override
    protected void teardown() {
        mCapture.stop();

        if (mRenderer != null) {
            mRenderer.release();
            mRenderer = null;
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
     * This method is triggered by the Renderer.Callback.onFrameAvailable() event. It is called
     * every frame, as soon as the renderer is ready to draw. Each registered surface is drawn on
     * using OpenGL.
     */
    void rendererFrame() {
        if (mCb != null) mCb.onCameraRender();

        for (Target target : mTargets) {
            target.render(mRenderer);
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

    public interface Callback extends CameraCapture.Callback {
        void onCameraRender();
    }

    /**
     * Internal output surface representation, containing references to the original surface and the
     * associated EGL.Surface.
     */
    public static class Target {
        private final Surface mSurface;
        private final int mWidth;
        private final int mHeight;
        private EGL.Surface mEglSurface;
        private boolean mEnabled = true;

        Target(Surface surface, int width, int height) {
            mSurface = surface;
            mWidth = width;
            mHeight = height;
        }

        /**
         * Allows to disable rendering to this target. To obtain an instance, store the return value
         * of addTarget().
         */
        public void setEnabled(boolean enabled) {
            mEnabled = enabled;
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
         * Draw the frame onto the target surface using OpenGL.
         */
        void render(Renderer renderer) {
            if (!mEnabled) return;
            mEglSurface.makeCurrent();
            GLES20.glViewport(0, 0, mWidth, mHeight);
            renderer.drawRectangle();
            mEglSurface.swapBuffers();
        }
    }
}
