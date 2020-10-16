package cz.fmo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import helper.DirectionX;
import helper.DirectionY;

import static org.junit.Assert.assertTrue;

public class LibTest {
    private Lib.Detection detection;

    @Before
    public void setUp() {
        detection = new Lib.Detection();
        detection.predecessorId = 0;
        detection.predecessor = null;
        detection.velocity = 20f;
        detection.radius = 5f;
        detection.centerX = 12;
        detection.centerY = 10;
        detection.directionY = DirectionY.DOWN;
        detection.directionX = DirectionX.LEFT;
        detection.id = 12;
    }

    @After
    public void tearDown() {
        detection = null;
    }

    @Test
    public void testToString() {
        String detectionMsg = detection.toString();
        assertTrue(detectionMsg.contains(String.valueOf(detection.id)));
        assertTrue(detectionMsg.contains(String.valueOf((int)detection.velocity)));
        assertTrue(detectionMsg.contains(String.valueOf((int)detection.radius)));
        assertTrue(detectionMsg.contains(String.valueOf(detection.centerX)));
        assertTrue(detectionMsg.contains(String.valueOf(detection.centerY)));
        assertTrue(detectionMsg.contains(String.valueOf((int)detection.directionY)));
        assertTrue(detectionMsg.contains(String.valueOf((int)detection.directionX)));
    }
}