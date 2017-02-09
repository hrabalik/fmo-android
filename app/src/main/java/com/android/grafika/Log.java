package com.android.grafika;

@SuppressWarnings("SameParameterValue")
class Log {
    private static final String TAG = "Grafika";

    static void d(String msg) {
        android.util.Log.d(TAG, msg);
    }

    static void e(String msg, Exception e) {
        android.util.Log.e(TAG, msg, e);
    }
    static void i(String msg) {
        android.util.Log.i(TAG, msg);
    }

    static void w(String msg) {
        android.util.Log.w(TAG, msg);
    }
}
