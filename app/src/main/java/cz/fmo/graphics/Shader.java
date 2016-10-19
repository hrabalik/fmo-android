package cz.fmo.graphics;

import android.opengl.GLES20;

class Shader {
    private final int mId;
    private boolean mReleased = false;

    Shader(int type, String source) throws RuntimeException {
        mId = GLES20.glCreateShader(type);
        GL.checkError();
        GLES20.glShaderSource(mId, source);
        GLES20.glCompileShader(mId);
        int[] result = {0};
        GLES20.glGetShaderiv(mId, GLES20.GL_COMPILE_STATUS, result, 0);
        if (result[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(mId);
            release();
            throw new RuntimeException(log);
        }
    }

    void release() {
        if (mReleased) return;
        mReleased = true;
        GLES20.glDeleteShader(mId);
    }

    int getId() {
        return mId;
    }
}
