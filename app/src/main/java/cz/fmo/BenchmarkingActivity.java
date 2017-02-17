package cz.fmo;

import android.app.Activity;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * Runs the benchmarks and displays the result on screen.
 */
public final class BenchmarkingActivity extends Activity {
    private final GUI mGUI = new GUI();
    private final Handler mHandler = new Handler(this);

    @Override
    protected void onCreate(android.os.Bundle saved) {
        super.onCreate(saved);
        mGUI.init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Lib.benchmarkingStart(mHandler);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Lib.benchmarkingStop();
    }

    private class GUI {
        private TextView mLog;

        /**
         * Prepares all static UI elements.
         */
        void init() {
            setContentView(R.layout.activity_benchmarking);
            mLog = (TextView) findViewById(R.id.benchmarking_log);
        }

        /**
         * Writes a string to the screen.
         */
        void appendToLog(String line) {
            mLog.append(line);
        }
    }

    private static class Handler extends android.os.Handler implements Lib.Callback {
        static final int LOG = 1;
        private final WeakReference<BenchmarkingActivity> mActivity;

        Handler(BenchmarkingActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void frameTimings(float q50, float q95, float q99) {
            // ignored
        }

        @Override
        public void log(String string) {
            sendMessage(obtainMessage(LOG, string));
        }

        @Override
        public void handleMessage(android.os.Message msg) {
            BenchmarkingActivity activity = mActivity.get();
            if (activity == null) return;

            switch (msg.what) {
                case LOG:
                    activity.mGUI.appendToLog((String) msg.obj);
            }
        }
    }
}
