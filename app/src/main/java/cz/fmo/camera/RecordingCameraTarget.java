package cz.fmo.camera;

import android.view.Surface;

/**
 * A simple camera target that can be disabled at any time. Draws only the frame provided by the
 * camera.
 */
public class RecordingCameraTarget extends CameraThread.Target {
    private boolean mEnabled = true;

    public RecordingCameraTarget(Surface surface, int width, int height) {
        super(surface, width, height);
    }

    /**
     * Allows to disable rendering to this target.
     */
    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    @Override
    void render(CameraThread thread) {
        if (!mEnabled) return;
        super.render(thread);
    }

    @Override
    void renderImpl(CameraThread thread) {
        thread.getCameraFrameRenderer().drawCameraFrame();
    }
}
