package cz.fmo.graphics;

import android.opengl.GLES20;

public class GL {
    /**
     * Checks whether the last GL call succeeded.
     *
     * @throws RuntimeException
     */
    static void checkError() throws RuntimeException {
        int errCode = GLES20.glGetError();
        if (errCode == GLES20.GL_NO_ERROR) return;
        throw new RuntimeException("GL error " + errCode);
    }

    static java.nio.Buffer makeBuffer(float[] arr) {
        java.nio.ByteBuffer byteBuf = java.nio.ByteBuffer.allocateDirect(4 * arr.length);
        byteBuf.order(java.nio.ByteOrder.nativeOrder());
        java.nio.FloatBuffer floatBuf = byteBuf.asFloatBuffer();
        floatBuf.put(arr);
        byteBuf.clear();
        return byteBuf;
    }

    static class Shader {
        private final int mId;
        private boolean mReleased = false;

        Shader(int type, String source) throws RuntimeException {
            mId = GLES20.glCreateShader(type);
            checkError();
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

}
