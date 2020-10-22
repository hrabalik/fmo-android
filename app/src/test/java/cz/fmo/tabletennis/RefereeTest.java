package cz.fmo.tabletennis;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import cz.fmo.Lib;
import cz.fmo.data.TrackSet;
import cz.fmo.util.Config;
import helper.DetectionGenerator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RefereeTest {
    private Referee referee;
    private GameCallback gameCallback;
    private static final int FRAME_RATE = 30;
    private static final int SOME_WIDTH = 1920;
    private static final int SOME_HEIGHT = 1080;
    private TrackSet realTrackSet = TrackSet.getInstance();
    private Config mockConfig;
    private static final Side STARTING_SIDE = Side.LEFT;

    @Before
    public void setUp() throws Exception {
        mockConfig = mock(Config.class);
        when(mockConfig.isDisableDetection()).thenReturn(false);
        when(mockConfig.getFrameRate()).thenReturn(30f);
        when(mockConfig.getVelocityEstimationMode()).thenReturn(Config.VelocityEstimationMode.PX_FR);
        when(mockConfig.getObjectRadius()).thenReturn(10f);
        realTrackSet.setConfig(mockConfig);
        referee = new Referee(STARTING_SIDE);
        gameCallback = mock(GameCallback.class);
        referee.setGame(gameCallback);
    }

    @After
    public void tearDown() throws Exception {
        referee = null;
        gameCallback = null;
    }


    @Test
    // left side is serving, ball then bounces twice on the right table side -> point for left
    public void testDoubleBounce() {
        simulateServe();
        referee.onBounce();
        referee.onTableSideChange(Side.RIGHT);
        referee.onBounce();
        referee.onBounce();
        verify(gameCallback, times(1)).onPoint(STARTING_SIDE);
    }

    @Test
    // left side is serving, ball bounces twice on his/her side -> fault by left
    public void onServingFault() {
        simulateServe();
        referee.onBounce();
        referee.onBounce();
        verify(gameCallback, times(1)).onPoint(Side.RIGHT);
    }

    @Test
    // the returnee hits the ball on his/her table side -> fault by right
    public void onReturnFault() {
        simulateServe();
        referee.onBounce();
        referee.onTableSideChange(Side.RIGHT);
        referee.onSideChange(Side.RIGHT);
        referee.onBounce();
        verify(gameCallback, times(1)).onPoint(Side.LEFT);
        verify(gameCallback, times(0)).onPoint(Side.RIGHT);
    }

    private void simulateServe() {
        long detectionTime = System.nanoTime();
        int delay = 1000/FRAME_RATE;
        Lib.Detection[] someDetections = DetectionGenerator.makeDetectionsInXDirectionOnTable(true);
        for (Lib.Detection someDetection : someDetections) {
            detectionTime = detectionTime + delay;
            realTrackSet.addDetections(new Lib.Detection[]{someDetection}, SOME_WIDTH, SOME_HEIGHT, detectionTime);
            referee.onStrikeFound(realTrackSet);
        }
    }
}