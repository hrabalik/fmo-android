package cz.fmo;

public final class Lib {

    static {
        System.loadLibrary("test1");
    }

    public static native String getHelloString();
    public static native void onFrame(FrameCallback cb);

    public interface FrameCallback {
        void frameTimings(float q50, float q95, float q99);
    }
}
