package cz.fmo.recording;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * Message handler for SaveMovieThread.
 */
class SaveMovieThreadHandler extends android.os.Handler {
    private static final int KILL = 1;
    private static final int SAVE = 2;
    private final WeakReference<SaveMovieThread> mThreadRef;

    SaveMovieThreadHandler(SaveMovieThread thread) {
        mThreadRef = new WeakReference<>(thread);
    }

    /**
     * Send a command to end the execution of the thread as soon as possible.
     */
    void sendKill() {
        sendMessage(obtainMessage(KILL));
    }

    /**
     * Send a command to save a movie.
     *
     * @param file file to save to, should be writable and have a .mp4 extension
     */
    void sendSave(File file) {
        sendMessage(obtainMessage(SAVE, file));
    }

    @Override
    public void handleMessage(android.os.Message msg) {
        SaveMovieThread thread = mThreadRef.get();
        if (thread == null) return;
        switch (msg.what) {
            case KILL:
                thread.kill();
                break;
            case SAVE:
                thread.save((File) msg.obj);
                break;
        }
    }
}
