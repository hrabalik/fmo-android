package cz.fmo.events;

import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;

import cz.fmo.Lib;
import cz.fmo.data.TrackSet;
import cz.fmo.util.Config;

public class EventDetectorTest {
    private Config mockConfig;
    private EventDetectionCallback mockCallback;
    private TrackSet mockTracks;
    private static final int SOME_WIDTH = 1920;
    private static final int SOME_HEIGHT = 1080;

    @Before
    public void prepare() {
        mockConfig = mock(Config.class);
        mockTracks = mock(TrackSet.class);
        mockCallback = mock(EventDetectionCallback.class);
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
}