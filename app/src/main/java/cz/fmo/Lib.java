package cz.fmo;

public final class Lib {

    static {
        System.loadLibrary("fmo-android");
    }

    public static native void detectionStart(int width, int height, Callback cb);

    public static native void detectionFrame(byte[] dataYUV420SP);

    public static native void detectionStop();

    public static native void benchmarkingStart(Callback cb);

    public static native void benchmarkingStop();

    @SuppressWarnings("unused")
    public interface Callback {
        void log(String message);
    }
}
