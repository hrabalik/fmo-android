package cz.fmo.graphics;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import cz.fmo.data.Assets;
import cz.fmo.util.Color;

/**
 * Special-purpose renderer for drawing text.
 */
public class FontRenderer {
    private static final int MAX_CHARACTERS = 128;
    private static final int TEXTURE_TYPE = GLES20.GL_TEXTURE_2D;
    private static final String VERTEX_SOURCE = "" +
            "uniform mat4 posMat;\n" +
            "attribute vec4 pos;\n" +
            "attribute vec4 uv1;\n" +
            "attribute vec4 color1;\n" +
            "varying vec2 uv2;\n" +
            "varying vec4 color2;\n" +
            "void main() {\n" +
            "    gl_Position = posMat * pos;\n" +
            "    uv2 = uv1.xy;\n" +
            "    color2 = color1;\n" +
            "}\n";
    private static final String FRAGMENT_SOURCE = "" +
            "precision mediump float;\n" +
            "varying vec2 uv2;\n" +
            "varying vec4 color2;\n" +
            "uniform sampler2D tex;\n" +
            "void main() {\n" +
            "    vec4 color3 = color2;\n" +
            "    color3.a = texture2D(tex, uv2).r;\n" +
            "    gl_FragColor = color3;\n" +
            "}\n";
    private final int mProgramId;
    private final int mTextureId;
    private final Shader mVertexShader;
    private final Shader mFragmentShader;
    private final int mLoc_pos;
    private final int mLoc_uv1;
    private final int mLoc_color1;
    private final int mLoc_posMat;
    private final int mLoc_tex;
    private final Buffers mBuffers = new Buffers();
    private boolean mReleased = false;

    public FontRenderer() throws RuntimeException {
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
        mLoc_uv1 = GLES20.glGetAttribLocation(mProgramId, "uv1");
        mLoc_color1 = GLES20.glGetAttribLocation(mProgramId, "color1");
        mLoc_posMat = GLES20.glGetUniformLocation(mProgramId, "posMat");
        mLoc_tex = GLES20.glGetUniformLocation(mProgramId, "tex");

        GLES20.glGenTextures(1, result, 0);
        GL.checkError();
        mTextureId = result[0];
        GLES20.glBindTexture(TEXTURE_TYPE, mTextureId);
        GL.checkError();
        GLES20.glTexParameterf(TEXTURE_TYPE, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(TEXTURE_TYPE, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(TEXTURE_TYPE, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameterf(TEXTURE_TYPE, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GL.checkError();
        Bitmap texBitmap = Assets.getInstance().getFontTexture();
        GLUtils.texImage2D(TEXTURE_TYPE, 0, texBitmap, 0);
        GL.checkError();
    }

    public void release() {
        if (mReleased) return;
        mReleased = true;
        if (mVertexShader != null) mVertexShader.release();
        if (mFragmentShader != null) mFragmentShader.release();
        GLES20.glDeleteProgram(mProgramId);
    }

    /**
     * Removes any previously added text.
     */
    public void clear() {
        mBuffers.pos.clear();
        mBuffers.uv.clear();
        mBuffers.color.clear();
        mBuffers.idx.clear();
        mBuffers.numVertices = 0;
    }

    /**
     * Adds text for rendering.
     *
     * @param str   string to render, please use ASCII characters only
     * @param x     X coordinate of the anchor point
     * @param y     Y coordinate of the anchor point
     * @param h     text height
     * @param color text color, alpha channel will be ignored
     */
    public void add(String str, float x, float y, float h, Color.RGBA color) {
        int len = str.length();
        float w = 0.639f * h;
        float ws = 0.8f * w;
        float topPos = y - 0.5f * h;
        float bottomPos = y + 0.5f * h;

        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            float leftPos = x + ((float) i) * ws;
            float rightPos = leftPos + w;
            int ui = c % 16;
            int vi = c / 16;
            float leftUv = ((float) ui) / 16.f;
            float rightUv = ((float) (ui + 1)) / 16.f;
            float topUv = (((float) vi) / 8.f);
            float bottomUv = (((float) (vi + 1)) / 8.f);
            addVertex(leftPos, topPos, leftUv, topUv, color);
            addVertex(leftPos, topPos, leftUv, topUv, color);
            addVertex(leftPos, bottomPos, leftUv, bottomUv, color);
            addVertex(rightPos, topPos, rightUv, topUv, color);
            addVertex(rightPos, bottomPos, rightUv, bottomUv, color);
            addVertex(rightPos, bottomPos, rightUv, bottomUv, color);
        }
    }

    private void addVertex(float x, float y, float u, float v, Color.RGBA color) {
        // no check for max vertices
        mBuffers.pos.put(x);
        mBuffers.pos.put(y);
        mBuffers.uv.put(u);
        mBuffers.uv.put(v);
        mBuffers.color.put(color.rgba[0]);
        mBuffers.color.put(color.rgba[1]);
        mBuffers.color.put(color.rgba[2]);
        mBuffers.color.put(color.rgba[3]);
        mBuffers.numVertices++;
    }

    /**
     * Draws all previously added text on screen.
     *
     * @param width  screen width
     * @param height screen height
     */
    public void drawText(float width, float height) {
        if (mReleased) throw new RuntimeException("Draw after release");

        mBuffers.posMat[0x0] = 2.f / width;
        mBuffers.posMat[0x5] = -2.f / height;
        mBuffers.posMat[0xC] = -1.f;
        mBuffers.posMat[0xD] = 1.f;

        mBuffers.pos.flip();
        mBuffers.uv.flip();
        mBuffers.color.flip();
        mBuffers.idx.flip();

        if (mBuffers.numVertices * 2 != mBuffers.pos.limit()) {
            throw new RuntimeException("Bad position buffer");
        }
        if (mBuffers.numVertices * 2 != mBuffers.uv.limit()) {
            throw new RuntimeException("Bad UV buffer");
        }
        if (mBuffers.numVertices * 4 != mBuffers.color.limit()) {
            throw new RuntimeException("Bad color buffer");
        }

        GLES20.glUseProgram(mProgramId);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glEnableVertexAttribArray(mLoc_pos);
        GLES20.glVertexAttribPointer(mLoc_pos, 2, GLES20.GL_FLOAT, false, 8, mBuffers.pos);
        GLES20.glEnableVertexAttribArray(mLoc_uv1);
        GLES20.glVertexAttribPointer(mLoc_uv1, 2, GLES20.GL_FLOAT, false, 8, mBuffers.uv);
        GLES20.glEnableVertexAttribArray(mLoc_color1);
        GLES20.glVertexAttribPointer(mLoc_color1, 4, GLES20.GL_FLOAT, false, 16, mBuffers.color);
        GLES20.glUniformMatrix4fv(mLoc_posMat, 1, false, mBuffers.posMat, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(TEXTURE_TYPE, mTextureId);
        GLES20.glUniform1i(mLoc_tex, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, mBuffers.numVertices);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(0);
        GL.checkError();
    }

    public static class Buffers {
        public final float[] posMat = GL.makeIdentity();
        public final FloatBuffer pos = GL.makeWritableFloatBuffer(12 * MAX_CHARACTERS);
        public final FloatBuffer uv = GL.makeWritableFloatBuffer(12 * MAX_CHARACTERS);
        public final FloatBuffer color = GL.makeWritableFloatBuffer(24 * MAX_CHARACTERS);
        public final IntBuffer idx = GL.makeWritableIntBuffer(6 * MAX_CHARACTERS);
        public int numVertices = 0;
    }
}
