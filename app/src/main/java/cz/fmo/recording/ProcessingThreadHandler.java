package cz.fmo.recording;

import android.media.ImageReader;

import java.lang.ref.WeakReference;

/**
 * Message handler for ProcessingThread.
 */
class ProcessingThreadHandler extends android.os.Handler implements
        ImageReader.OnImageAvailableListener {
    private static final int KILL = 1;
    private static final int FRAME = 2;
    private final WeakReference<ProcessingThread> mThreadRef;

    ProcessingThreadHandler(ProcessingThread thread) {
        mThreadRef = new WeakReference<>(thread);
    }

    /**
     * Send a command to end the execution of the thread as soon as possible.
     */
    void sendKill() {
        sendMessage(obtainMessage(KILL));
    }

    /**
     * Send a command to process an image. A callback from ImageReader.
     */
    @Override
    public void onImageAvailable(ImageReader reader) {
        sendMessage(obtainMessage(FRAME));
    }

    @Override
    public void handleMessage(android.os.Message msg) {
        ProcessingThread thread = mThreadRef.get();
        if (thread == null) return;
        switch (msg.what) {
            case KILL:
                thread.kill();
                break;
            case FRAME:
                thread.frame();
                break;
        }
    }
}
