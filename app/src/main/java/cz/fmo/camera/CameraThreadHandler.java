package cz.fmo.camera;

import java.lang.ref.WeakReference;

import cz.fmo.graphics.Renderer;

/**
 * Message handler for CameraThread.
 */
public class CameraThreadHandler extends android.os.Handler implements Renderer.Callback {
    private static final int KILL = 1;
    private static final int RENDERER_FRAME = 2;
    private final WeakReference<CameraThread> mThreadRef;

    CameraThreadHandler(CameraThread thread) {
        mThreadRef = new WeakReference<>(thread);
    }

    @Override
    public void onFrameAvailable() {
        sendMessage(obtainMessage(RENDERER_FRAME));
    }

    /**
     * Send a command to end the execution of the thread as soon as possible.
     */
    public void sendKill() {
        sendMessage(obtainMessage(KILL));
    }

    @Override
    public void handleMessage(android.os.Message msg) {
        CameraThread thread = mThreadRef.get();
        if (thread == null) return;
        switch (msg.what) {
            case KILL:
                thread.kill();
                break;
            case RENDERER_FRAME:
                thread.rendererFrame();
                break;
        }
    }
}
