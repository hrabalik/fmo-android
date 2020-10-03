package cz.fmo.camera;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.view.Surface;

/**
 * An extra target whose point is just to consume the camera frames. Without it, the camera stream
 * would freeze whenever no target is listening.
 */
class DummyCameraTarget extends CameraThread.Target {
    private int mTexId;
    private SurfaceTexture mTex;

    DummyCameraTarget() {
        super(null, 1, 1);
        {
            int[] result = {0};
            GLES20.glGenTextures(1, result, 0);
            mTexId = result[0];
            int target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
            GLES20.glBindTexture(target, mTexId);
            GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            mTex = new SurfaceTexture(mTexId);
        }
        setSurface(new Surface(mTex));
    }

    @Override
    public void release() {
        super.release();

        if (mTex != null) {
            int[] array = {mTexId};
            GLES20.glDeleteTextures(1, array, 0);
            mTex.release();
            mTex = null;
        }
    }

    @Override
    public void renderImpl(CameraThread thread) {
        thread.getCameraFrameRenderer().drawCameraFrame();
    }
}
