package cz.fmo.recording;

import java.lang.ref.WeakReference;

/**
 * Message handler for EncodeThread.
 */
public class EncodeThreadHandler extends android.os.Handler {
    private static final int KILL = 1;
    private static final int FLUSH = 2;
    private final WeakReference<EncodeThread> mThreadRef;

    EncodeThreadHandler(EncodeThread thread) {
        mThreadRef = new WeakReference<>(thread);
    }

    /**
     * Send a command to end the execution of the thread as soon as possible.
     */
    public void sendKill() {
        sendMessage(obtainMessage(KILL));
    }

    /**
     * Send a command to flush all currently available encoder output and save it into the buffer.
     */
    public void sendFlush() {
        sendMessage(obtainMessage(FLUSH));
    }

    @Override
    public void handleMessage(android.os.Message msg) {
        EncodeThread thread = mThreadRef.get();
        if (thread == null) return;
        switch (msg.what) {
            case KILL:
                thread.kill();
                break;
            case FLUSH:
                thread.flush();
                break;
        }
    }
}
