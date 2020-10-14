package cz.fmo.events;

import cz.fmo.Lib;
import cz.fmo.data.TrackSet;
import cz.fmo.util.Config;
import helper.DirectionX;
import helper.DirectionY;

public class EventDetector implements Lib.Callback {
    private static final int SIDE_CHANGE_DETECTION_SPEED = 2;
    private static final double PERCENTAGE_OF_NEARLY_OUT_OF_FRAME = 0.1;
    private final TrackSet tracks;
    private final EventDetectionCallback callback;
    private final int[] nearlyOutOfFrameThresholds;
    private int srcWidth;
    private int srcHeight;
    private float lastXDirection;
    private long detectionCount;

    public EventDetector(Config config, int srcWidth, int srcHeight, EventDetectionCallback callback, TrackSet tracks) {
        this.srcHeight = srcHeight;
        this.srcWidth = srcWidth;
        this.callback = callback;
        this.tracks = tracks;
        this.nearlyOutOfFrameThresholds = new int[] {
                (int) (srcWidth*PERCENTAGE_OF_NEARLY_OUT_OF_FRAME),
                (int) (srcWidth*(1-PERCENTAGE_OF_NEARLY_OUT_OF_FRAME)),
                (int) (srcHeight*PERCENTAGE_OF_NEARLY_OUT_OF_FRAME),
                (int) (srcHeight*(1-PERCENTAGE_OF_NEARLY_OUT_OF_FRAME)),
        };
        tracks.setConfig(config);
    }

    @Override
    public void log(String message) {
        // Lib logs will be ignored for now
    }

    @Override
    public void onObjectsDetected(Lib.Detection[] detections, long detectionTime) {
        // Pls only uncomment for debugging, slows down the video -> detection gets worse
        /* System.out.println("detections:");
        for (Lib.Detection detection : detections) {
            System.out.println(detection);
        } */
        detectionCount++;
        tracks.addDetections(detections, this.srcWidth, this.srcHeight, detectionTime); // after this, object direction is updated

        if(!tracks.getTracks().isEmpty()) {
            Lib.Detection latestDetection = tracks.getTracks().get(0).getLatest();
            // TODO: Filter / combine tracks, find bounces, side changes and outOfFrames
            callback.onStrikeFound(tracks);
            if(isNearlyOutOfFrame(latestDetection)) {
                callback.onNearlyOutOfFrame(latestDetection);
            }
            if (detectionCount % SIDE_CHANGE_DETECTION_SPEED == 0 && tracks.getTracks().size() == 1) {
                float curDirectionX = latestDetection.directionX;
                if (lastXDirection != curDirectionX) {
                    callback.onSideChange(curDirectionX < 0);
                }
                lastXDirection = curDirectionX;
            }
        }
    }

    private boolean isNearlyOutOfFrame(Lib.Detection detection) {
        boolean isNearlyOutOfFrame = false;
        if(detection.centerX < nearlyOutOfFrameThresholds[0] && detection.directionX == DirectionX.LEFT ||
                detection.centerX > nearlyOutOfFrameThresholds[1] && detection.directionX == DirectionX.RIGHT ||
                detection.centerY < nearlyOutOfFrameThresholds[2] && detection.directionY == DirectionY.UP ||
                detection.centerY > nearlyOutOfFrameThresholds[3] && detection.directionY == DirectionY.DOWN) {
            isNearlyOutOfFrame = true;
        }
        return isNearlyOutOfFrame;
    }

    public int[] getNearlyOutOfFrameThresholds() {
        return nearlyOutOfFrameThresholds;
    }
}