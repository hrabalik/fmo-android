/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.grafika.gles;

import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.view.Surface;

import com.android.grafika.Log;

/**
 * Common base class for EGL surfaces.
 * <p>
 * There can be multiple surfaces associated with a single context.
 */
public class EglSurfaceBase {
    // EglCore object we're associated with. It may be associated with multiple surfaces.
    private final EglCore mEglCore;

    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;

    protected EglSurfaceBase(EglCore eglCore) {
        mEglCore = eglCore;
    }

    /**
     * Creates a window surface.
     * <p>
     *
     * @param surface May be a Surface or SurfaceTexture.
     */
    public void createWindowSurface(Surface surface) {
        if (mEGLSurface != EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("surface already created");
        }
        mEGLSurface = mEglCore.createWindowSurface(surface);
    }

    ///**
    // * Creates an off-screen surface.
    // */
    //public void createOffscreenSurface(int width, int height) {
    //    if (mEGLSurface != EGL14.EGL_NO_SURFACE) {
    //        throw new IllegalStateException("surface already created");
    //    }
    //    mEGLSurface = mEglCore.createOffscreenSurface(width, height);
    //}

    /**
     * Release the EGL surface.
     */
    public void releaseEglSurface() {
        mEglCore.releaseSurface(mEGLSurface);
        mEGLSurface = EGL14.EGL_NO_SURFACE;
    }

    /**
     * Makes our EGL context and surface current.
     */
    public void makeCurrent() {
        mEglCore.makeCurrent(mEGLSurface);
    }

    /**
     * Calls eglSwapBuffers.  Use this to "publish" the current frame.
     */
    public void swapBuffers() {
        boolean result = mEglCore.swapBuffers(mEGLSurface);
        if (!result) {
            Log.d("WARNING: swapBuffers() failed");
        }
    }

    /**
     * Sends the presentation time stamp to EGL.
     *
     * @param nsecs Timestamp, in nanoseconds.
     */
    public void setPresentationTime(long nsecs) {
        mEglCore.setPresentationTime(mEGLSurface, nsecs);
    }
}
