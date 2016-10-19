package cz.fmo.graphics;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

public class GL {
    /**
     * Checks whether the last GL call succeeded.
     *
     * @throws RuntimeException
     */
    private static void checkError() throws RuntimeException {
        int errCode = GLES20.glGetError();
        if (errCode == GLES20.GL_NO_ERROR) return;
        throw new RuntimeException("GL error " + errCode);
    }

    private static java.nio.Buffer makeBuffer(float[] arr) {
        java.nio.ByteBuffer byteBuf = java.nio.ByteBuffer.allocateDirect(4 * arr.length);
        byteBuf.order(java.nio.ByteOrder.nativeOrder());
        java.nio.FloatBuffer floatBuf = byteBuf.asFloatBuffer();
        floatBuf.put(arr);
        byteBuf.clear();
        return byteBuf;
    }

    private static class Shader {
        private final int mId;
        private boolean mReleased = false;

        private Shader(int type, String source) throws RuntimeException {
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

        private void release() {
            if (mReleased) return;
            mReleased = true;
            GLES20.glDeleteShader(mId);
        }
    }

    public static class Renderer {
        private static final int TEXTURE_TYPE = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
        private static final String VERTEX_SOURCE = "" +
                "uniform mat4 uvMat;\n" +
                "attribute vec4 pos;\n" +
                "attribute vec4 uv1;\n" +
                "varying vec2 uv2;\n" +
                "void main() {\n" +
                "    gl_Position = pos;\n" +
                "    uv2 = (uvMat * uv1).xy;\n" +
                "}\n";
        private static final String FRAGMENT_SOURCE = "" +
                "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "varying vec2 uv2;\n" +
                "uniform samplerExternalOES tex;\n" +
                "void main() {\n" +
                "    gl_FragColor = texture2D(tex, uv2);\n" +
                "}\n";
        private static final float[] RECTANGLE_POS_DATA = {-1, -1, 1, -1, -1, 1, 1, 1};
        private static final float[] RECTANGLE_UV_DATA = {0, 0, 1, 0, 0, 1, 1, 1};
        private static final java.nio.Buffer RECTANGLE_POS = makeBuffer(RECTANGLE_POS_DATA);
        private static final java.nio.Buffer RECTANGLE_UV = makeBuffer(RECTANGLE_UV_DATA);

        private final int mId;
        private final int mTexId;
        private final Shader mVert;
        private final Shader mFrag;
        private final int mLoc_pos;
        private final int mLoc_uv1;
        private final int mLoc_uvMat;
        private boolean mReleased = false;

        public Renderer() throws RuntimeException {
            mId = GLES20.glCreateProgram();
            checkError();
            mVert = new Shader(GLES20.GL_VERTEX_SHADER, VERTEX_SOURCE);
            mFrag = new Shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE);
            GLES20.glAttachShader(mId, mVert.mId);
            GLES20.glAttachShader(mId, mFrag.mId);
            GLES20.glLinkProgram(mId);

            int[] result = {0};
            GLES20.glGetProgramiv(mId, GLES20.GL_LINK_STATUS, result, 0);
            if (result[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(mId);
                release();
                throw new RuntimeException(log);
            }

            mLoc_pos = GLES20.glGetAttribLocation(mId, "pos");
            mLoc_uv1 = GLES20.glGetAttribLocation(mId, "uv1");
            mLoc_uvMat = GLES20.glGetUniformLocation(mId, "uvMat");

            GLES20.glGenTextures(1, result, 0);
            checkError();
            mTexId = result[0];
            GLES20.glBindTexture(TEXTURE_TYPE, mTexId);
            checkError();
            GLES20.glTexParameterf(TEXTURE_TYPE, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameterf(TEXTURE_TYPE, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(TEXTURE_TYPE, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(TEXTURE_TYPE, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            checkError();
        }

        public void release() {
            if (mReleased) return;
            mReleased = true;
            if (mVert != null) mVert.release();
            if (mFrag != null) mFrag.release();
            if (mTexId != 0) {
                int[] textures = {mTexId};
                GLES20.glDeleteTextures(1, textures, 0);
            }
            GLES20.glDeleteProgram(mId);
        }

        public int getTextureId() {
            return mTexId;
        }

        public void drawRectangle(float[] uvMat) {
            if (mReleased) throw new RuntimeException("Draw after release");
            GLES20.glUseProgram(mId);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(TEXTURE_TYPE, mTexId);
            GLES20.glEnableVertexAttribArray(mLoc_pos);
            GLES20.glVertexAttribPointer(mLoc_pos, 2, GLES20.GL_FLOAT, false, 8, RECTANGLE_POS);
            GLES20.glEnableVertexAttribArray(mLoc_uv1);
            GLES20.glVertexAttribPointer(mLoc_uv1, 2, GLES20.GL_FLOAT, false, 8, RECTANGLE_UV);
            GLES20.glUniformMatrix4fv(mLoc_uvMat, 1, false, uvMat, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glUseProgram(0);
            checkError();
        }
    }
}
