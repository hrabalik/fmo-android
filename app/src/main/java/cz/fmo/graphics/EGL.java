package cz.fmo.graphics;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;

/**
 * Provides access to EGL, which is a gateway to Khronos APIs, including OpenGL ES 2.0. An EGL
 * context is created upon construction and deleted using release(). Context configuration makes use
 * of the Android-specific "recordable" flag, which is important when interfacing with video
 * encoders/decoders.
 */
public final class EGL {
    private static final EGLSurface NO_SURFACE = EGL14.EGL_NO_SURFACE;
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

    private final EGLDisplay mDisplay;
    private final EGLContext mContext;
    private final EGLConfig mConfig;
    private boolean mReleased = false;

    public EGL() throws RuntimeException {
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
        if (mReleased) return;
        mReleased = true;
        EGL14.eglMakeCurrent(mDisplay, NO_SURFACE, NO_SURFACE, NO_CONTEXT);
        EGL14.eglDestroyContext(mDisplay, mContext);
        EGL14.eglReleaseThread();
        EGL14.eglTerminate(mDisplay);
    }

    /**
     * Provides a new surface EGL attached to a android.view.Surface. Use its method release() to
     * clean up. The EGL surface does not take ownership of the attached android.view.Surface.
     *
     * @param viewSurface an android.view.Surface to attach
     * @return an object representing the new EGL Surface
     * @throws RuntimeException
     */
    public Surface makeSurface(android.view.Surface viewSurface) throws RuntimeException {
        return new Surface(this, viewSurface);
    }

    /**
     * Checks whether the last EGL call succeeded.
     *
     * @throws RuntimeException
     */
    private void checkError() throws RuntimeException {
        int errCode = EGL14.eglGetError();
        if (errCode == EGL14.EGL_SUCCESS) return;
        throw new RuntimeException("EGL error " + errCode);
    }

    /**
     * Represents a drawable and recordable EGL surface attached to a android.view.Surface. Use
     * release() to destroy the surface.
     */
    public static final class Surface {
        private final EGL mEGL;
        private final EGLSurface mEGLSurface;
        private boolean mReleased = false;

        private Surface(EGL egl, android.view.Surface surface) throws RuntimeException {
            mEGL = egl;

            int[] attr = {END};
            mEGLSurface = EGL14.eglCreateWindowSurface(egl.mDisplay, egl.mConfig, surface, attr, 0);
            egl.checkError();
        }

        public void release() {
            if (mReleased) return;
            mReleased = true;
            EGL14.eglDestroySurface(mEGL.mDisplay, mEGLSurface);
        }

        /**
         * Makes this surface the current surface for both reading and writing.
         */
        public void makeCurrent() {
            EGL14.eglMakeCurrent(mEGL.mDisplay, mEGLSurface, mEGLSurface, mEGL.mContext);
        }

        /**
         * Copies the contents of this surface onto the attached display.
         */
        public void swapBuffers() {
            EGL14.eglSwapBuffers(mEGL.mDisplay, mEGLSurface);
        }

        /**
         * Updates the presentation time of the surface.
         *
         * @param ns time in nanoseconds
         */
        @SuppressWarnings("unused")
        public void presentationTime(long ns) {
            EGLExt.eglPresentationTimeANDROID(mEGL.mDisplay, mEGLSurface, ns);
        }
    }
}
