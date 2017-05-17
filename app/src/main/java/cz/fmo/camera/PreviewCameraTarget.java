package cz.fmo.camera;

import android.view.Surface;

import cz.fmo.data.TrackSet;
import cz.fmo.graphics.FontRenderer;
import cz.fmo.graphics.TriangleStripRenderer;
import cz.fmo.util.Color;

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
        TriangleStripRenderer tsRender = thread.getTriangleStripRenderer();
        TrackSet.getInstance().generateCurves(tsRender.getBuffers());
        tsRender.drawTriangleStrip();

        // draw fonts
        FontRenderer fontRender = thread.getFontRenderer();
        fontRender.clear();
        Color.RGBA color = new Color.RGBA();
        color.rgba[0] = 1.f;
        color.rgba[1] = 1.f;
        color.rgba[2] = 1.f;
        color.rgba[3] = 1.f;
        fontRender.add("Hello, world!", 200.f, 200.f, 40.f, color);
        fontRender.drawText(mWidth, mHeight);
    }
}
