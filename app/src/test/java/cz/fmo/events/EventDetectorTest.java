package cz.fmo.events;

import com.android.grafika.Log;

import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import cz.fmo.Lib;
import cz.fmo.data.Track;
import cz.fmo.data.TrackSet;
import cz.fmo.util.Config;
import helper.DetectionGenerator;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EventDetectorTest {
    private Config mockConfig;
    private EventDetectionCallback mockCallback;
    private TrackSet mockTracks;
    private static final TrackSet realTrackSet = TrackSet.getInstance();
    private static final int SOME_WIDTH = 1920;
    private static final int SOME_HEIGHT = 1080;
    private static final int FRAME_RATE = 30;

    @Before
    public void prepare() {
        Awaitility.setDefaultPollDelay(10, TimeUnit.MILLISECONDS);
        mockConfig = mock(Config.class);
        mockTracks = mock(TrackSet.class);
        mockCallback = mock(EventDetectionCallback.class);
        when(mockConfig.isDisableDetection()).thenReturn(false);
        when(mockConfig.getFrameRate()).thenReturn(30f);
        when(mockConfig.getVelocityEstimationMode()).thenReturn(Config.VelocityEstimationMode.PX_FR);
        when(mockConfig.getObjectRadius()).thenReturn(10f);
    }

    @Test
    public void testUpdateTrackSetConfigOnConstruction() {
        EventDetector ev = new EventDetector(mockConfig, SOME_WIDTH, SOME_HEIGHT,mockCallback, mockTracks);
        verify(mockTracks, atLeastOnce()).setConfig(mockConfig);
    }

    @Test
    public void testUpdateTrackSetOnObjectsDetected() {
        EventDetector ev = new EventDetector(mockConfig, SOME_WIDTH, SOME_HEIGHT,mockCallback, mockTracks);
        Lib.Detection[] someDetections = new Lib.Detection[0];
        ev.onObjectsDetected(someDetections);
        verify(mockTracks, times(1)).addDetections(someDetections, SOME_WIDTH, SOME_HEIGHT);
    }

    @Test
    public void testOnStrikeFound() {
        EventDetector ev = new EventDetector(mockConfig, SOME_WIDTH, SOME_HEIGHT, mockCallback, realTrackSet);
        Lib.Detection[] strikeDetections = DetectionGenerator.makeDetectionsInXDirection(true);
        invokeOnObjectDetectedWithDelay(strikeDetections, ev);

        // assert that we have now one track with all detections in it
        verify(mockCallback, times(strikeDetections.length)).onStrikeFound(realTrackSet);
        assertEquals(1, realTrackSet.getTracks().size());
        Track track = realTrackSet.getTracks().get(0);
        Lib.Detection detection = track.getLatest();
        int count = strikeDetections.length-1;
        while(detection != null) {
            assertEquals(detection.id, strikeDetections[count].id);
            count--;
            detection = detection.predecessor;
        }
    }

    @Test
    public void testOnSideChange() {
        EventDetector ev = new EventDetector(mockConfig, SOME_WIDTH, SOME_HEIGHT, mockCallback, TrackSet.getInstance());
        Lib.Detection[] strikeDetectionsRight = DetectionGenerator.makeDetectionsInXDirection(true);
        Lib.Detection[] strikeDetectionsLeft = DetectionGenerator.makeDetectionsInXDirection(false);
        // object is getting shot from left to right side
        invokeOnObjectDetectedWithDelay(strikeDetectionsRight, ev);
        verify(mockCallback, times(0)).onSideChange(true);
        verify(mockCallback, times(1)).onSideChange(false);
        // object is getting shot from right to left side
        invokeOnObjectDetectedWithDelay(strikeDetectionsLeft, ev);
        verify(mockCallback, times(1)).onSideChange(true);
        verify(mockCallback, times(1)).onSideChange(false);
        // object again getting shot from right to left side (same direction "edge" case) - no more side change call
        invokeOnObjectDetectedWithDelay(strikeDetectionsLeft, ev);
        verify(mockCallback, times(1)).onSideChange(true);
        verify(mockCallback, times(1)).onSideChange(false);
    }



    private void invokeOnObjectDetectedWithDelay(Lib.Detection[] allDetections, EventDetector ev) {
        int delay = 1000/FRAME_RATE;
        for (Lib.Detection detection : allDetections) {
            ev.onObjectsDetected(new Lib.Detection[]{detection});
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                Log.e(ie.getMessage(), ie);
            }
        }
    }
}