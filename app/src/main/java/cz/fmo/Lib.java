package cz.fmo;

public final class Lib {

    static {
        System.loadLibrary("test1");
    }

    public static native String getHelloString();
}
