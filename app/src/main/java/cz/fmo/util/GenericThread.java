package cz.fmo.util;

import android.os.Looper;

/**
 * Universal base class for a thread with an Android event loop. Every thread is required to provide
 * its own message handler class, which serves as a gateway through which this thread receives
 * messages from other threads. The handler class must be a subclass of android.os.Handler.
 *
 * @param <H> message handler for the derived class
 */
public abstract class GenericThread<H extends android.os.Handler> extends Thread {
    private final Object mLock = new Object();
    private H mHandler = null;

    /**
     * The main method of the thread. The statement Looper.Loop() blocks until the kill() method is
     * called.
     */
    @Override
    public final void run() {
        synchronized (mLock) {
            Looper.prepare();
            mHandler = makeHandler();
            setup(mHandler);
            mLock.notify();
        }
        Looper.loop();
        synchronized (mLock) {
            mHandler = null;
            teardown();
        }
    }

    /**
     * Start the thread, blocking until the message handler is available.
     */
    @Override
    public synchronized void start() {
        super.start();
        waitForHandler();
    }

    /**
     * Blocks until the handler is available.
     */
    private void waitForHandler() {
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

    /**
     * @return the handler for this instance
     * @throws RuntimeException in case the handler doesn't exist (use waitForHandler() first)
     */
    public final H getHandler() throws RuntimeException {
        synchronized (mLock) {
            if (mHandler == null) throw new RuntimeException("No handler");
        }
        return mHandler;
    }

    /**
     * Stops the execution of the event loop, terminating the thread.
     */
    public final void kill() {
        Looper l = Looper.myLooper();
        if (l == null) return;
        l.quit();
    }

    /**
     * @return a new handler instance bound to "this"
     */
    protected abstract H makeHandler();

    /**
     * Optional extension point. This method is called before the event loop is started. The method
     * runs in the same thread as the event loop.
     */
    protected void setup(H handler) {
    }

    /**
     * Optional extension point. This method is called after the event loop is stopped. The method
     * runs in the same thread as the event loop.
     */
    protected void teardown() {
    }
}
