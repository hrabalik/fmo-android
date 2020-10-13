package cz.fmo.camera;

import android.graphics.SurfaceTexture;

import java.lang.ref.WeakReference;

/**
 * Message handler for CameraThread.
 */
public class CameraThreadHandler extends android.os.Handler implements SurfaceTexture.OnFrameAvailableListener {
    private static final int KILL = 1;
    private static final int FRAME = 2;
    private final WeakReference<CameraThread> mThreadRef;

    CameraThreadHandler(CameraThread thread) {
        mThreadRef = new WeakReference<>(thread);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture texture) {
        if (hasMessages(FRAME)) return;
        sendMessage(obtainMessage(FRAME));
    }

    /**
     * Send a command to end the execution of the thread as soon as possible.
     */
    public void sendKill() {
        if (hasMessages(KILL)) return;
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
            case FRAME:
                thread.frameAvailable();
                break;
            default:
                break;
        }
    }
}
