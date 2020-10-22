package com.android.grafika;

import android.Manifest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import cz.fmo.R;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class PlayMovieSurfaceActivityTest {
    private PlayMovieSurfaceActivity playMovieSurfaceActivity;
    private SurfaceView surfaceViewMovie;
    private SurfaceView surfaceViewTracks;
    private SurfaceView surfaceViewTable;
    private Spinner movieSelectSpinner;
    private Button playStopButton;
    private TextView txtSide;
    private TextView txtBounce;

    @Rule
    public ActivityTestRule<PlayMovieSurfaceActivity> pmsActivityRule = new ActivityTestRule<PlayMovieSurfaceActivity>(PlayMovieSurfaceActivity.class);
    @Rule
    public GrantPermissionRule grantPermissionRuleCamera = GrantPermissionRule.grant(Manifest.permission.CAMERA);
    @Rule
    public GrantPermissionRule grantPermissionRuleStorage = GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE);

    @Before
    public void setUp() throws Exception {
        playMovieSurfaceActivity = pmsActivityRule.getActivity();
        surfaceViewMovie = playMovieSurfaceActivity.findViewById(R.id.playMovie_surface);
        surfaceViewTracks = playMovieSurfaceActivity.findViewById(R.id.playMovie_surfaceTracks);
        surfaceViewTable = playMovieSurfaceActivity.findViewById(R.id.playMovie_surfaceTable);
        movieSelectSpinner = playMovieSurfaceActivity.findViewById(R.id.playMovieFile_spinner);
        playStopButton = playMovieSurfaceActivity.findViewById(R.id.play_stop_button);
        txtSide = playMovieSurfaceActivity.findViewById(R.id.txtSide);
        txtBounce = playMovieSurfaceActivity.findViewById(R.id.txtBounce);
    }

    @After
    public void tearDown() throws Exception {
        playMovieSurfaceActivity.finish();
        playMovieSurfaceActivity = null;
    }

    @Test
    public void findAllViewsInActivity() {
        assertNotNull(surfaceViewMovie);
        assertNotNull(surfaceViewTracks);
        assertNotNull(surfaceViewTable);
        assertNotNull(movieSelectSpinner);
        assertNotNull(playStopButton);
        assertNotNull(txtSide);
        assertNotNull(txtBounce);
        assertEquals(playMovieSurfaceActivity.getResources().getString(R.string.play_button_text), playStopButton.getText());
        assertEquals("0", txtBounce.getText());
    }

    @Test
    // plays a video for a couple of seconds (with bounces in it), and then checks if there was a bounce
    public void testPlayMovieAndFindBounces() {
        movieSelectSpinner.setSelection(0, true);
        playMovieSurfaceActivity.onItemSelected(movieSelectSpinner, null, 0, R.id.playMovieFile_spinner);
        implAssertBounce(txtBounce);
        implAssertSideChange(txtSide);
        Runnable clickPlayButton = new ClickPlayButton(playStopButton);
        playMovieSurfaceActivity.runOnUiThread(clickPlayButton);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException ex) {
            fail();
        }
        assertEquals(playMovieSurfaceActivity.getResources().getString(R.string.stop_button_text),playStopButton.getText());
        playMovieSurfaceActivity.runOnUiThread(clickPlayButton);
    }

    public void implAssertBounce(TextView txtBounce) {
        txtBounce.addTextChangedListener(new TextWatcher() {
            int amountOfBounces = 0;

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // no implementation for test needed
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                assertNotEquals("0", String.valueOf(charSequence));
                amountOfBounces++;
                assertEquals(String.valueOf(amountOfBounces), String.valueOf(charSequence));
                assertEquals(playMovieSurfaceActivity.getResources().getString(R.string.stop_button_text),playStopButton.getText());
            }

            @Override
            public void afterTextChanged(Editable editable) {
                // no implementation for test needed
            }
        });
    }

    public void implAssertSideChange(TextView txtSide) {
        txtSide.addTextChangedListener(new TextWatcher() {
            private String lastSide = "";

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // no implementation for test needed
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                assertNotEquals(lastSide, String.valueOf(charSequence));
                lastSide = String.valueOf(charSequence);
            }

            @Override
            public void afterTextChanged(Editable editable) {
                // no implementation for test needed
            }
        });
    }
}

class ClickPlayButton implements Runnable {
    private Button button;
    ClickPlayButton(Button button) {
        this.button = button;
    }
    @Override
    public void run() {
        button.performClick();
    }
}