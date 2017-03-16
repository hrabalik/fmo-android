package cz.fmo.util;

public final class Time {
    public static long toUs(float sec) {
        return (long) (sec * 1e6f);
    }

    public static long toMs(float sec) {
        return (long) (sec * 1e3f);
    }

    public static int toFrames(float sec) {
        return (int) (sec * 30.f);
    }
}
