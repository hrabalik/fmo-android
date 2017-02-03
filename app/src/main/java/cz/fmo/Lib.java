package cz.fmo;

import android.media.Image;

public final class Lib {

    static {
        System.loadLibrary("test1");
    }

    public static native String getHelloString();

    public static native void onFrame(Image image, Callback cb);

    public static native void ocvRecStart(Callback cb);

    public static native void ocvRecStop();

    public static native void ocvRec2Start(int width, int height, Callback cb);

    public static native void ocvRec2Frame(long matPtr, long timeNs);

    public static native void ocvRec2Stop();

    public static native void recording2Start(int width, int height, Callback cb);

    public static native void recording2Frame(byte[] dataYUV420SP);

    public static native void recording2Stop();

    public interface Callback {
        void frameTimings(float q50, float q95, float q99);

        void cameraError();
    }
}
