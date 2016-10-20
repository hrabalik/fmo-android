package cz.fmo.recording;

import java.lang.ref.WeakReference;

/**
 * Message handler for EncodeThread.
 */
public class EncodeThreadHandler extends android.os.Handler {
    private static final int KILL = 1;
    private static final int ENCODE = 2;
    private final WeakReference<EncodeThread> mThreadRef;

    public EncodeThreadHandler(EncodeThread thread) {
        mThreadRef = new WeakReference<>(thread);
    }

    /**
     * Send a command to end the execution of the thread as soon as possible.
     */
    public void sendKill() {
        sendMessage(obtainMessage(KILL));
    }

    /**
     * Send a command to encode all currently available input data and save it to the buffer.
     */
    public void sendEncode() {
        sendMessage(obtainMessage(ENCODE));
    }

    @Override
    public void handleMessage(android.os.Message msg) {
        EncodeThread thread = mThreadRef.get();
        if (thread == null) return;
        switch (msg.what) {
            case KILL:
                thread.kill();
                break;
            case ENCODE:
                thread.encode();
                break;
        }
    }
}
