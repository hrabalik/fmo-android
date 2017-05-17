package cz.fmo.graphics;

import android.opengl.GLES20;

import java.nio.FloatBuffer;

/**
 * A general-purpose renderer of triangle strips with a solid color or gradient, possibly
 * transparent.
 */
public class TriangleStripRenderer {
    private static final int MAX_VERTICES = 512;
    private static final String VERTEX_SOURCE = "" +
            "uniform mat4 posMat;\n" +
            "attribute vec4 pos;\n" +
            "attribute vec4 color1;\n" +
            "varying vec4 color2;\n" +
            "void main() {\n" +
            "    gl_Position = posMat * pos;\n" +
            "    color2 = color1;\n" +
            "}\n";
    private static final String FRAGMENT_SOURCE = "" +
            "precision mediump float;\n" +
            "varying vec4 color2;\n" +
            "void main() {\n" +
            "    gl_FragColor = color2;\n" +
            "}\n";
    private final int mProgramId;
    private final Shader mVertexShader;
    private final Shader mFragmentShader;
    private final int mLoc_pos;
    private final int mLoc_color1;
    private final int mLoc_posMat;
    private final Buffers mBuffers = new Buffers();
    private boolean mReleased = false;

    public TriangleStripRenderer() throws RuntimeException {
        mProgramId = GLES20.glCreateProgram();
        GL.checkError();
        mVertexShader = new Shader(GLES20.GL_VERTEX_SHADER, VERTEX_SOURCE);
        mFragmentShader = new Shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE);
        GLES20.glAttachShader(mProgramId, mVertexShader.getId());
        GLES20.glAttachShader(mProgramId, mFragmentShader.getId());
        GLES20.glLinkProgram(mProgramId);

        int[] result = {0};
        GLES20.glGetProgramiv(mProgramId, GLES20.GL_LINK_STATUS, result, 0);
        if (result[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(mProgramId);
            release();
            throw new RuntimeException(log);
        }

        mLoc_pos = GLES20.glGetAttribLocation(mProgramId, "pos");
        mLoc_color1 = GLES20.glGetAttribLocation(mProgramId, "color1");
        mLoc_posMat = GLES20.glGetUniformLocation(mProgramId, "posMat");
    }

    public void release() {
        if (mReleased) return;
        mReleased = true;
        if (mVertexShader != null) mVertexShader.release();
        if (mFragmentShader != null) mFragmentShader.release();
        GLES20.glDeleteProgram(mProgramId);
    }

    public Buffers getBuffers() {
        return mBuffers;
    }

    public void drawTriangleStrip() {
        if (mReleased) throw new RuntimeException("Draw after release");
        if (mBuffers.numVertices * 2 != mBuffers.pos.limit()) {
            throw new RuntimeException("Bad position buffer");
        }
        if (mBuffers.numVertices * 4 != mBuffers.color.limit()) {
            throw new RuntimeException("Bad color buffer");
        }

        GLES20.glUseProgram(mProgramId);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glEnableVertexAttribArray(mLoc_pos);
        GLES20.glVertexAttribPointer(mLoc_pos, 2, GLES20.GL_FLOAT, false, 8, mBuffers.pos);
        GLES20.glEnableVertexAttribArray(mLoc_color1);
        GLES20.glVertexAttribPointer(mLoc_color1, 4, GLES20.GL_FLOAT, false, 16, mBuffers.color);
        GLES20.glUniformMatrix4fv(mLoc_posMat, 1, false, mBuffers.posMat, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, mBuffers.numVertices);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(0);
        GL.checkError();
    }

    public static class Buffers {
        public final float[] posMat = GL.makeIdentity();
        public final FloatBuffer pos = GL.makeWritableFloatBuffer(2 * MAX_VERTICES);
        public final FloatBuffer color = GL.makeWritableFloatBuffer(4 * MAX_VERTICES);
        public int numVertices = 0;
    }

}
