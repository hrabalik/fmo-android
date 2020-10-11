package helper;

import java.util.Random;

import cz.fmo.Lib;

/**
 * Helper class which generates some Detections in various directions.
 */
public class DetectionGenerator {
    private static final int START_Y_POS = 100;
    private static final int START_X_POS = 100;
    private static final int SPACE_BETWEEN_OBJECTS = 50;
    private static final int MAX_ID = 99999;

    /**
     * Generates Detections which represent ONE object thrown horizontally.
     * @param toRight specifies if the "object" will move to the right, if false -> goes to left
     * @return 6 detections which form a horizontal line (with some space in between of course).
     */
    public static Lib.Detection[] makeDetectionsInXDirection(boolean toRight) {
        int n = 6;
        int id = new Random().nextInt(MAX_ID);
        int directionFactor = -1;
        if(toRight) {directionFactor = 1;}
        Lib.Detection[] detections = new Lib.Detection[n];
        for (int i = 0; i<n; i++) {
            detections[i] = new Lib.Detection();
            detections[i].centerX = directionFactor*i*SPACE_BETWEEN_OBJECTS+START_X_POS;
            detections[i].centerY = START_Y_POS;
            detections[i].id = i + id;
            detections[i].radius = 3;
            detections[i].velocity = 20f;
            if (i > 0) {
                detections[i].predecessor = detections[i-1];
                detections[i].predecessorId = detections[i-1].id;
            }
        }
        return detections;
    }
}
