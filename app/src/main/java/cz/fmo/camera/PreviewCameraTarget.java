package cz.fmo.camera;

import android.view.Surface;
import cz.fmo.graphics.TriangleStripRenderer;

import java.nio.FloatBuffer;

/**
 * Camera target that can be set up to drop most of the frames, saving on battery. Draws not only
 * the camera frame, but also highlights detected FMO tracks.
 */
public class PreviewCameraTarget extends CameraThread.Target {
    private int mSlowdown = 0;
    private int mCounter = 0;

    public PreviewCameraTarget(Surface surface, int width, int height) {
        super(surface, width, height);
    }

    /**
     * Set up the target to render every n-th frame.
     *
     * @param n every n-th frame will be rendered
     */
    public void setSlowdown(int n) {
        mSlowdown = n;
        mCounter = n;
    }

    @Override
    void render(CameraThread thread) {
        if (++mCounter < mSlowdown) return;
        mCounter = 0;
        super.render(thread);
    }

    @Override
    void renderImpl(CameraThread thread) {
        // draw frame as background
        thread.getCameraFrameRenderer().drawCameraFrame();

        // draw tracks
        renderTracks(thread.getTriangleStripRenderer());
    }

    private void renderTracks(TriangleStripRenderer renderer) {
        // reset
        FloatBuffer posBuf = renderer.getPosBuffer();
        FloatBuffer colorBuf = renderer.getColorBuffer();

        // add a few verts
        posBuf.clear();
        posBuf.put(-1); posBuf.put(-1);
        posBuf.put(1); posBuf.put(-1);
        posBuf.put(-1); posBuf.put(1);
        posBuf.put(1); posBuf.put(1);
        posBuf.flip();
        colorBuf.clear();
        colorBuf.put(1.f); colorBuf.put(0.f); colorBuf.put(0.f); colorBuf.put(1.f);
        colorBuf.put(0.f); colorBuf.put(1.f); colorBuf.put(0.f); colorBuf.put(1.f);
        colorBuf.put(0.f); colorBuf.put(0.f); colorBuf.put(1.f); colorBuf.put(1.f);
        colorBuf.put(1.f); colorBuf.put(1.f); colorBuf.put(1.f); colorBuf.put(0.f);
        colorBuf.flip();
        renderer.setNumVertices(4);

        // render
        renderer.drawTriangleStrip();
    }
}
