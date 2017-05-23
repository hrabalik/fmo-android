package cz.fmo;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(android.os.Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_main);
    }

    public void runPlayMovieSurfaceActivity(@SuppressWarnings("UnusedParameters") View view) {
        startActivity(new Intent(this, com.android.grafika.PlayMovieSurfaceActivity.class));
    }

    public void runBenchmarkingActivity(@SuppressWarnings("UnusedParameters") View view) {
        startActivity(new Intent(this, BenchmarkingActivity.class));
    }

    public void runRecordingActivity(@SuppressWarnings("UnusedParameters") View view) {
        startActivity(new Intent(this, RecordingActivity.class));
    }

    public void runPlaybackActivity(@SuppressWarnings("UnusedParameters") View view) {
        startActivity(new Intent(this, PlaybackActivity.class));
    }

    public void runMenuActivity(@SuppressWarnings("UnusedParameters") View view) {
        startActivity(new Intent(this, MenuActivity.class));
    }
}
