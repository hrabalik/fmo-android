package cz.fmo.recording2;

import android.media.MediaFormat;
import android.opengl.GLES20;
import android.view.Surface;

import cz.fmo.bftp.CameraCapture;
import cz.fmo.graphics.EGL;
import cz.fmo.graphics.Renderer;
import cz.fmo.util.GenericThread;

class CameraThread extends GenericThread<CameraThreadHandler> {
    private final Callback mCb;
    private final java.util.ArrayList<Target> mTargets = new java.util.ArrayList<>();
    private EGL mEGL;
    private Renderer mRenderer;
    private CameraCapture mCapture;

    CameraThread(Callback cb) {
        super("CameraThread");
        mCb = cb;
        mCapture = new CameraCapture(mCb);
    }

    void addTarget(Surface surface, int width, int height) {
        mTargets.add(new Target(surface, width, height));
    }

    @Override
    protected CameraThreadHandler makeHandler() {
        return new CameraThreadHandler(this);
    }

    @Override
    protected void setup(CameraThreadHandler handler) {
        mEGL = new EGL();

        for (Target target : mTargets) {
            target.initEGL(mEGL);
        }

        mRenderer = new Renderer(handler);

        mCapture.start(mRenderer.getInputTexture());
    }

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

    void rendererFrame() {
        mCb.onCameraRender();

        for (Target target : mTargets) {
            target.render(mRenderer);
        }
    }

    MediaFormat getMediaFormat() {
        return mCapture.getMediaFormat();
    }

    int getWidth() {
        return mCapture.getWidth();
    }

    int getHeight() {
        return mCapture.getHeight();
    }

    int getBitRate() {
        return mCapture.getBitRate();
    }

    float getFrameRate() {
        return mCapture.getFrameRate();
    }

    interface Callback extends CameraCapture.Callback {
        void onCameraRender();
    }

    private static class Target {
        private final Surface mSurface;
        private final int mWidth;
        private final int mHeight;
        private EGL.Surface mEglSurface;

        Target(Surface surface, int width, int height) {
            mSurface = surface;
            mWidth = width;
            mHeight = height;
        }

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

        void render(Renderer renderer) {
            mEglSurface.makeCurrent();
            GLES20.glViewport(0, 0, mWidth, mHeight);
            renderer.drawRectangle();
            mEglSurface.swapBuffers();
        }
    }
}
