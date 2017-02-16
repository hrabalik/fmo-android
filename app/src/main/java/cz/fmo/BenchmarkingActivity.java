package cz.fmo;

import android.app.Activity;
import android.widget.TextView;

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
        mGUI.log("resumed");
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGUI.log("paused");
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
         * Writes an extra line to log.
         */
        void log(String line) {
            mLog.append(line);
            mLog.append("\n");
        }
    }
}
