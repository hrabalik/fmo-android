package com.android.grafika;

@SuppressWarnings("SameParameterValue")
public class Log {
    private Log(){}
    private static final String TAG = "Grafika";

    public static void d(String msg) {
        android.util.Log.d(TAG, msg);
    }

    public static void e(String msg, Exception e) {
        android.util.Log.e(TAG, msg, e);
    }
    public static void i(String msg) {
        android.util.Log.i(TAG, msg);
    }

    public static void w(String msg) {
        android.util.Log.w(TAG, msg);
    }
}
