package com.android.grafika;

public class Log {
    private static final String TAG = "Grafika";

    public static void d(String msg) {
        android.util.Log.d(TAG, msg);
    }
    public static void e(String msg) {
        android.util.Log.e(TAG, msg);
    }
    public static void i(String msg) {
        android.util.Log.i(TAG, msg);
    }
    public static void v(String msg) {
        android.util.Log.v(TAG, msg);
    }
    public static void w(String msg) {
        android.util.Log.w(TAG, msg);
    }
    public static void w(String msg, Exception e) {
        android.util.Log.w(TAG, msg, e);
    }
}
