package cz.fmo.events;

import cz.fmo.Lib;
import cz.fmo.data.TrackSet;
import cz.fmo.util.Config;

public class EventDetector implements Lib.Callback {
    private static final int SIDE_CHANGE_DETECTION_SPEED = 2;
    private final TrackSet tracks;
    private final EventDetectionCallback callback;
    private int srcWidth;
    private int srcHeight;
    private float lastXDirection;
    private long countFrame;

    public EventDetector(Config config, int srcWidth, int srcHeight, EventDetectionCallback callback, TrackSet tracks) {
        this.srcHeight = srcHeight;
        this.srcWidth = srcWidth;
        this.callback = callback;
        this.tracks = tracks;
        tracks.setConfig(config);
    }

    @Override
    public void log(String message) {
        // Lib logs will be ignored for now
    }

    @Override
    public void onObjectsDetected(Lib.Detection[] detections) {
        // Pls only uncomment for debugging, slows down the video -> detection gets worse
        /* System.out.println("detections:");
        for (Lib.Detection detection : detections) {
            System.out.println(detection);
        } */
        countFrame++;
        tracks.addDetections(detections, this.srcWidth, this.srcHeight); // after this, object direction is updated
        // TODO: Filter / combine tracks, find bounces, side changes and outOfFrames
        callback.onStrikeFound(tracks);
        if (countFrame % SIDE_CHANGE_DETECTION_SPEED == 0 && tracks.getTracks().size() == 1) {
            float curDirectionX = tracks.getTracks().get(0).getLatest().directionX;
            if (lastXDirection != curDirectionX) {
                callback.onSideChange(curDirectionX < 0);
            }
            lastXDirection = curDirectionX;
        }
    }
}