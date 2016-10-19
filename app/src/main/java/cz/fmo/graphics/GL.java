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

}
