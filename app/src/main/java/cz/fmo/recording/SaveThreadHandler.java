package cz.fmo.recording;

import java.lang.ref.WeakReference;

/**
 * Message handler for SaveThread.
 */
public class SaveThreadHandler extends android.os.Handler {
    private static final int KILL = 1;
    private static final int TASK = 3;
    private final WeakReference<SaveThread> mThreadRef;

    SaveThreadHandler(SaveThread thread) {
        mThreadRef = new WeakReference<>(thread);
    }

    /**
     * Send a command to end the execution of the thread as soon as possible.
     */
    public void sendKill() {
        if (hasMessages(KILL)) return;
        sendMessage(obtainMessage(KILL));
    }

    /**
     * Schedule a task and perform it with a delay. This should not be called explicitly by user
     * code; tasks schedule themselves in their constructors.
     *
     * @param ms the number of milliseconds to wait before performing the task.
     */
    public void sendTask(SaveThread.Task task, long ms) {
        sendMessageDelayed(obtainMessage(TASK, task), ms);
    }

    /**
     * Cancel a previously scheduled task.
     */
    public void cancelTask(SaveThread.Task task) {
        removeMessages(TASK, task);
    }

    @Override
    public void handleMessage(android.os.Message msg) {
        SaveThread thread = mThreadRef.get();
        if (thread == null) return;
        switch (msg.what) {
            case KILL:
                thread.kill();
                break;
            case TASK:
                SaveThread.Task task = (SaveThread.Task) msg.obj;
                task.perform();
                break;
        }
    }
}
