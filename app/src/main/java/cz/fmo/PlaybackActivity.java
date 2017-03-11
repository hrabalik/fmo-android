package cz.fmo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;

public class PlaybackActivity extends Activity {
    static final int INTENT_REQUEST_VIDEO_FILE = 0;
    private final GUI mGUI = new GUI();

    @Override
    protected void onCreate(android.os.Bundle saved) {
        super.onCreate(saved);
        mGUI.init();
    }

    public void onClickPickVideo(@SuppressWarnings("UnusedParameters") View view) {
        pickVideo();
    }

    public void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, INTENT_REQUEST_VIDEO_FILE);
        } else {
            Toast.makeText(this, "no support for picking a video", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == INTENT_REQUEST_VIDEO_FILE && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            Toast.makeText(this, uri.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private class GUI {
        void init() {
            setContentView(R.layout.activity_playback);
        }
    }
}
