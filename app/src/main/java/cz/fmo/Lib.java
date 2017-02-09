package cz.fmo;

public final class Lib {

    static {
        System.loadLibrary("fmo-android");
    }

    public static native String getHelloString();

    public static native void recording2Start(int width, int height, Callback cb);

    public static native void recording2Frame(byte[] dataYUV420SP);

    public static native void recording2Stop();

    @SuppressWarnings("unused")
    public interface Callback {
        void frameTimings(float q50, float q95, float q99);

        void cameraError();
    }
}
