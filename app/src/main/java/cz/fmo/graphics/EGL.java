package cz.fmo.graphics;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;

public final class EGL {
    private static final EGLSurface NO_SURFACE = EGL14.EGL_NO_SURFACE;
    private static final EGLDisplay NO_DISPLAY = EGL14.EGL_NO_DISPLAY;
    private static final EGLContext NO_CONTEXT = EGL14.EGL_NO_CONTEXT;
    private static final int END = EGL14.EGL_NONE;
    private static final int[] CONFIG_ATTR = {
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            0x3142, 1, // EGL_RECORDABLE_ANDROID
            END
    };

    private EGLDisplay mDisplay = NO_DISPLAY;
    private EGLContext mContext = NO_CONTEXT;
    private EGLConfig mConfig = null;

    public EGL() {
        mDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        EGL14.eglInitialize(mDisplay, new int[2], 0, new int[2], 1);
        EGLConfig[] chosen = new EGLConfig[1];
        int[] nConf = {0};
        EGL14.eglChooseConfig(mDisplay, CONFIG_ATTR, 0, chosen, 0, chosen.length, nConf, 0);
        mConfig = chosen[0];
        int[] attr = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, END};
        mContext = EGL14.eglCreateContext(mDisplay, mConfig, NO_CONTEXT, attr, 0);
        checkError();
    }

    public void release() {
        if (mDisplay == NO_DISPLAY) return;
        EGL14.eglMakeCurrent(mDisplay, NO_SURFACE, NO_SURFACE, NO_CONTEXT);
        EGL14.eglDestroyContext(mDisplay, mContext);
        EGL14.eglReleaseThread();
        EGL14.eglTerminate(mDisplay);
        mDisplay = NO_DISPLAY;
        mContext = NO_CONTEXT;
        mConfig = null;
    }

    public void checkError() throws RuntimeException {
        int errCode = EGL14.eglGetError();
        if (errCode == EGL14.EGL_SUCCESS) return;
        throw new RuntimeException("EGL error");
    }

    public static final class Surface {
        private final EGL mEGL;
        private final EGLSurface mEGLSurface;
        private final android.view.Surface mSurface;
        private final boolean mIsOwner;
        private boolean mReleased = false;

        Surface(EGL egl, android.view.Surface surface, boolean isOwner) {
            mEGL = egl;
            mSurface = surface;
            mIsOwner = isOwner;

            int[] attr = {END};
            mEGLSurface = EGL14.eglCreateWindowSurface(egl.mDisplay, egl.mConfig, surface, attr, 0);
            egl.checkError();
        }

        public void release() {
            if (mReleased) return;
            mReleased = true;
            EGL14.eglDestroySurface(mEGL.mDisplay, mEGLSurface);
            if (mIsOwner) mSurface.release();
        }

        public void makeCurrent() {
            EGL14.eglMakeCurrent(mEGL.mDisplay, mEGLSurface, mEGLSurface, mEGL.mContext);
        }

        public void swapBuffers() {
            EGL14.eglSwapBuffers(mEGL.mDisplay, mEGLSurface);
        }

        public void presentationTime(long ns) {
            EGLExt.eglPresentationTimeANDROID(mEGL.mDisplay, mEGLSurface, ns);
        }
    }
}
