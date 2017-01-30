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

    public interface Callback {
        void frameTimings(float q50, float q95, float q99);

        void cameraError();
    }
}
