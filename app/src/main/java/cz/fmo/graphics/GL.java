package cz.fmo.graphics;

import android.opengl.GLES20;

/**
 * Utility functions for working with OpenGL.
 */
public class GL {
    private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

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

    /**
     * Transform an array of floats into a read-only Buffer.
     *
     * @param arr array of floats
     * @return a new, read-only, directly allocated buffer with its contents copied from arr
     */
    static java.nio.Buffer makeReadOnlyBuffer(float[] arr) {
        java.nio.ByteBuffer byteBuf = java.nio.ByteBuffer.allocateDirect(4 * arr.length);
        byteBuf.order(java.nio.ByteOrder.nativeOrder());
        java.nio.FloatBuffer floatBuf = byteBuf.asFloatBuffer();
        floatBuf.put(arr);
        byteBuf.clear();
        return byteBuf.asReadOnlyBuffer();
    }

    /**
     * Create a writable buffer of a specified size.
     *
     * @param size maximum number of float value that will fit into the buffer
     */
    static java.nio.FloatBuffer makeWritableBuffer(int size) {
        java.nio.ByteBuffer byteBuf = java.nio.ByteBuffer.allocateDirect(4 * size);
        byteBuf.order(java.nio.ByteOrder.nativeOrder());
        return byteBuf.asFloatBuffer();
    }

    /**
     * @return A new 4x4 identity matrix.
     */
    static float[] makeIdentity() {
        return IDENTITY.clone();
    }

    /**
     * Sets a 4x4 matrix to identity.
     */
    public static void setIdentity(float[] m) {
        System.arraycopy(IDENTITY, 0, m, 0, 16);
    }
}
