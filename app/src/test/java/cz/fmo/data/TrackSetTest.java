package cz.fmo.data;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import cz.fmo.Lib;
import cz.fmo.util.Config;
import helper.DetectionGenerator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TrackSetTest {
    private static final int SOME_WIDTH = 1920;
    private static final int SOME_HEIGHT = 1080;
    private static final int FRAME_RATE = 30;
    private TrackSet spyTrackSet;
    private Config mockConfig;
    private static final TrackSet realTrackSet = TrackSet.getInstance();

    @Before
    public void setUp() throws Exception {
        spyTrackSet = spy(realTrackSet);
        mockConfig = mock(Config.class);
        when(mockConfig.isDisableDetection()).thenReturn(false);
        when(mockConfig.getFrameRate()).thenReturn(30f);
        when(mockConfig.getVelocityEstimationMode()).thenReturn(Config.VelocityEstimationMode.PX_FR);
        when(mockConfig.getObjectRadius()).thenReturn(10f);
    }

    @After
    public void tearDown() throws Exception {
        spyTrackSet = null;
        mockConfig = null;
    }

    @Test
    public void setConfigClearsAllTracks() {
        verify(spyTrackSet, times(0)).clear();
        spyTrackSet.setConfig(mockConfig);
        verify(spyTrackSet, times(1)).clear();
    }

    @Test
    public void oldTracksGetFilteredOutAfterNFrames() {
        int nFrames = 4; // needs to be bigger or equal to FRAMES_UNTIL_OLD_TRACK_REMOVAL in TrackSet.java
        spyTrackSet.setConfig(mockConfig);
        Lib.Detection[] someDetections = DetectionGenerator.makeDetectionsInXDirection(true);
        for(int i = 0; i<someDetections.length; i++) {
            try {
                Thread.sleep(1000/FRAME_RATE);
                spyTrackSet.addDetections(new Lib.Detection[]{someDetections[i]}, SOME_WIDTH, SOME_HEIGHT);
            } catch (InterruptedException ex) {
                fail();
            }
        }
        // assert we have one track, as all generated directions form a track
        assertEquals(1, spyTrackSet.getTracks().size());

         // now wait a couple of frames, the track should get removed on next addDetection invocation
        try {
            Thread.sleep(1000/FRAME_RATE * nFrames);
        } catch (InterruptedException ex) {
            fail();
        }
        // "stub call" addDetection with empty array to trigger removal
        spyTrackSet.addDetections(new Lib.Detection[0], SOME_WIDTH, SOME_HEIGHT);
        assertEquals(0, spyTrackSet.getTracks().size());
    }

    // this test simulates n objects thrown left to right and vice versa
    // with this simulation we can test and verify quite many features of TrackSet and Track
    @Test
    public void producesMultipleTracksOnMultipleObjects() {
        int nObjects = 10; // test for up to 10 objects
        spyTrackSet.setConfig(mockConfig);
        Lib.Detection[][] someDetections = new Lib.Detection[nObjects][];
        // generate some random object detections
        for (int i = 0; i<nObjects; i++) {
            double r = Math.random();
            someDetections[i] = DetectionGenerator.makeDetectionsInXDirection(r > 0.5);
        }

        // now use the detections an add them to trackSet
        for (int i=0; i<someDetections[0].length; i++){
            Lib.Detection[] detectionsInFrame = new Lib.Detection[nObjects];

            for (int j=0; j<nObjects; j++) {
                detectionsInFrame[j] = someDetections[j][i];
            }
            spyTrackSet.addDetections(detectionsInFrame, SOME_WIDTH, SOME_HEIGHT);

            // each frame we should see nObjects tracks as there are nObjects simulated
            assertEquals(nObjects, spyTrackSet.getTracks().size());

            // simulate video sequence by sleeping a frame length
            try {
                Thread.sleep(1000/FRAME_RATE);
            } catch (InterruptedException ex) {
                fail();
            }
        }
    }

    @Test
    public void clear() {
        spyTrackSet.setConfig(mockConfig);
        assertEquals(0, spyTrackSet.getTracks().size());
        Lib.Detection[] someDetections = DetectionGenerator.makeDetectionsInXDirection(Math.random() > 0.5);
        spyTrackSet.addDetections(new Lib.Detection[]{someDetections[0]}, SOME_WIDTH, SOME_HEIGHT);
        assertEquals(1, spyTrackSet.getTracks().size());
        spyTrackSet.clear();
        assertEquals(0, spyTrackSet.getTracks().size());
    }
}