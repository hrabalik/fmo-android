package cz.fmo;

import android.app.Activity;
import android.widget.TextView;

/**
 * Runs the benchmarks and displays the result on screen.
 */
public final class BenchmarkingActivity extends Activity {
    private final GUI mGUI = new GUI();

    @Override
    protected void onCreate(android.os.Bundle saved) {
        super.onCreate(saved);
        mGUI.init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Lib.benchmarkingStart(mGUI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Lib.benchmarkingStop();
    }

    private class GUI implements Lib.Callback {
        private TextView mLog;

        /**
         * Prepares all static UI elements.
         */
        void init() {
            setContentView(R.layout.activity_benchmarking);
            mLog = (TextView) findViewById(R.id.benchmarking_log);
        }

        @Override
        public void frameTimings(float q50, float q95, float q99) {
            // ignored
        }

        /**
         * Writes a string to the screen.
         */
        @Override
        public void log(String line) {
            mLog.append(line);
        }
    }
}
