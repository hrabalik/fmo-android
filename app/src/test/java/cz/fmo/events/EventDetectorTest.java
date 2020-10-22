package cz.fmo.events;

import org.junit.Before;
import org.junit.Test;

import cz.fmo.Lib;
import cz.fmo.data.Track;
import cz.fmo.data.TrackSet;
import cz.fmo.tabletennis.Side;
import cz.fmo.tabletennis.Table;
import cz.fmo.util.Config;
import helper.DetectionGenerator;
import helper.TableGenerator;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EventDetectorTest {
    private Config mockConfig;
    private Table table;
    private EventDetectionCallback mockCallback;
    private TrackSet mockTracks;
    private static final TrackSet realTrackSet = TrackSet.getInstance();
    private static final int SOME_WIDTH = 1920;
    private static final int SOME_HEIGHT = 1080;
    private static final int FRAME_RATE = 30;

    @Before
    public void prepare() {
        table = TableGenerator.makeTableFromGarageRecording();
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
        EventDetector ev = new EventDetector(mockConfig, SOME_WIDTH, SOME_HEIGHT,mockCallback, mockTracks, table);
        verify(mockTracks, atLeastOnce()).setConfig(mockConfig);
    }

    @Test
    public void testUpdateTrackSetOnObjectsDetected() {
        EventDetector ev = new EventDetector(mockConfig, SOME_WIDTH, SOME_HEIGHT,mockCallback, mockTracks, table);
        Lib.Detection[] someDetections = new Lib.Detection[0];
        long detectionTime = System.nanoTime();
        ev.onObjectsDetected(someDetections, detectionTime);
        verify(mockTracks, times(1)).addDetections(someDetections, SOME_WIDTH, SOME_HEIGHT, detectionTime);
    }

    @Test
    public void testOnStrikeFound() {
        EventDetector ev = new EventDetector(mockConfig, SOME_WIDTH, SOME_HEIGHT, mockCallback, realTrackSet, table);
        Lib.Detection[] strikeDetections = DetectionGenerator.makeDetectionsInXDirectionOnTable(true);
        invokeOnObjectDetectedWithDelay(strikeDetections, ev, 0);

        // assert that we have now one track with all detections in it
        verify(mockCallback, atLeastOnce()).onStrikeFound(realTrackSet);
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
        EventDetector ev = new EventDetector(mockConfig, SOME_WIDTH, SOME_HEIGHT, mockCallback, TrackSet.getInstance(), table);
        Lib.Detection[] strikeDetectionsRight = DetectionGenerator.makeDetectionsInXDirectionOnTable(true);
        Lib.Detection[] strikeDetectionsLeft = DetectionGenerator.makeDetectionsInXDirectionOnTable(false);
        // object is getting shot from left to right side
        invokeOnObjectDetectedWithDelay(strikeDetectionsRight, ev, 0);
        verify(mockCallback, times(0)).onSideChange(Side.RIGHT);
        verify(mockCallback, times(1)).onSideChange(Side.LEFT);
        // object is getting shot from right to left side
        invokeOnObjectDetectedWithDelay(strikeDetectionsLeft, ev, strikeDetectionsRight.length);
        verify(mockCallback, times(1)).onSideChange(Side.RIGHT);
        verify(mockCallback, times(1)).onSideChange(Side.LEFT);
        // object again getting shot from right to left side (same direction "edge" case) - no more side change call
        invokeOnObjectDetectedWithDelay(strikeDetectionsLeft, ev, strikeDetectionsLeft.length+strikeDetectionsRight.length);
        verify(mockCallback, times(1)).onSideChange(Side.RIGHT);
        verify(mockCallback, times(1)).onSideChange(Side.LEFT);
    }

    @Test
    public void testOnNearlyOutOfFrame() {
        TrackSet ts = TrackSet.getInstance();
        EventDetector ev = new EventDetector(mockConfig, SOME_WIDTH, SOME_HEIGHT, mockCallback, ts, table);
        Lib.Detection[] nearlyOutOfFrame;
        Lib.Detection predecessor = new Lib.Detection();
        predecessor.centerX = SOME_WIDTH / 2;
        predecessor.centerY = SOME_WIDTH / 2;
        for(int i = 0; i < 10; i++) {
            nearlyOutOfFrame = DetectionGenerator.makeNearlyOutOfFrameDetections(ev.getNearlyOutOfFrameThresholds(), SOME_WIDTH, SOME_HEIGHT);
            for(Lib.Detection detection : nearlyOutOfFrame) {
                detection.predecessor = predecessor;
                invokeOnObjectDetectedWithDelay(new Lib.Detection[]{predecessor}, ev, i);
                invokeOnObjectDetectedWithDelay(new Lib.Detection[]{detection}, ev, i);
                verify(mockCallback, times(1)).onNearlyOutOfFrame(detection);
                ev = new EventDetector(mockConfig, SOME_WIDTH, SOME_HEIGHT, mockCallback, ts, table);
            }
        }
        Lib.Detection[] detectionsInFrame = DetectionGenerator.makeDetectionsInXDirectionOnTable(true);
        invokeOnObjectDetectedWithDelay(detectionsInFrame, ev, 0);
        for (Lib.Detection detection : detectionsInFrame) {
            verify(mockCallback, times(0)).onNearlyOutOfFrame(detection);
        }
    }

    @Test
    public void testNearlyOutOfFrameWithoutPredecessor() {
        TrackSet ts = TrackSet.getInstance();
        EventDetector ev = new EventDetector(mockConfig, SOME_WIDTH, SOME_HEIGHT, mockCallback, ts, table);
        Lib.Detection[] nearlyOutOfFrame;
        for(int i = 0; i < 10; i++) {
            nearlyOutOfFrame = DetectionGenerator.makeNearlyOutOfFrameDetections(ev.getNearlyOutOfFrameThresholds(), SOME_WIDTH, SOME_HEIGHT);
            for(Lib.Detection detection : nearlyOutOfFrame) {
                invokeOnObjectDetectedWithDelay(new Lib.Detection[]{detection}, ev, i);
                verify(mockCallback, times(0)).onNearlyOutOfFrame(detection);
                ev = new EventDetector(mockConfig, SOME_WIDTH, SOME_HEIGHT, mockCallback, ts, table);
            }
        }
    }

    private void invokeOnObjectDetectedWithDelay(Lib.Detection[] allDetections, EventDetector ev, int nStartFramesDelay) {
        int delay = 1000/FRAME_RATE * 1000 * 1000;
        long detectionTime = System.nanoTime() + nStartFramesDelay * delay;
        for (Lib.Detection detection : allDetections) {
            detectionTime = detectionTime + delay;
            ev.onObjectsDetected(new Lib.Detection[]{detection}, detectionTime);
        }
    }
}