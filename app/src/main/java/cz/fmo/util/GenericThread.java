package cz.fmo.util;

import android.os.Looper;

public abstract class GenericThread<H extends android.os.Handler> extends Thread {
    private final Object mLock = new Object();
    private H mHandler;

    @Override
    public final void run() {
        Looper.prepare();
        synchronized (mLock) {
            //noinspection unchecked
            mHandler = (H) makeHandler();
            mLock.notify();
        }
        Looper.loop();
        synchronized (mLock) {
            mHandler = null;
        }
    }

    public final void waitForHandler() {
        synchronized (mLock) {
            while (mHandler == null) {
                try {
                    mLock.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted");
                }
            }
        }
    }

    public final H getHandler() {
        synchronized (mLock) {
            if (mHandler == null) throw new RuntimeException("No handler");
        }
        return mHandler;
    }

    public final void kill() {
        Looper l = Looper.myLooper();
        if (l == null) return;
        l.quit();
    }

    protected abstract android.os.Handler makeHandler();
}
