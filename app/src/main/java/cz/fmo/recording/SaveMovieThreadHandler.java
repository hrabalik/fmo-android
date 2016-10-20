package cz.fmo.recording;

import java.io.File;
import java.lang.ref.WeakReference;

class SaveMovieThreadHandler extends android.os.Handler {
    private static final int KILL = 1;
    private static final int WORK = 2;
    private final WeakReference<SaveMovieThread> mThreadRef;

    public SaveMovieThreadHandler(SaveMovieThread thread) {
        mThreadRef = new WeakReference<>(thread);
    }

    public void sendKill() {
        sendMessage(obtainMessage(KILL));
    }

    public void sendWork(File file) {
        sendMessage(obtainMessage(WORK, file));
    }

    @Override
    public void handleMessage(android.os.Message msg) {
        SaveMovieThread thread = mThreadRef.get();
        if (thread == null) return;
        switch (msg.what) {
            case KILL:
                thread.kill();
                break;
            case WORK:
                thread.work((File) msg.obj);
                break;
        }
    }
}
