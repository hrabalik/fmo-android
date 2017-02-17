package cz.fmo;

public final class Lib {

    static {
        System.loadLibrary("fmo-android");
    }

    public static native String getHelloString();

    public static native void recordingStart(int width, int height, Callback cb);

    public static native void recordingFrame(byte[] dataYUV420SP);

    public static native void recordingStop();

    public static native void benchmarkingStart(Callback cb);

    public static native void benchmarkingStop();

    @SuppressWarnings("unused")
    public interface Callback {
        void frameTimings(float q50, float q95, float q99);

        void log(String message);
    }
}
