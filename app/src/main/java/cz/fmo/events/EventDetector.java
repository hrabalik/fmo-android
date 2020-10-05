package cz.fmo.events;

import cz.fmo.Lib;
import cz.fmo.data.TrackSet;
import cz.fmo.util.Config;

public class EventDetector implements Lib.Callback {
    private final Config config;
    private final TrackSet tracks;
    private final EventDetectionCallback callback;
    private int srcWidth;
    private int srcHeight;

    public EventDetector(Config config, int srcWidth, int srcHeight, EventDetectionCallback callback, TrackSet tracks) {
        this.config = config;
        this.srcHeight = srcHeight;
        this.srcWidth = srcWidth;
        this.callback = callback;
        this.tracks = tracks;
        tracks.setConfig(config);
    }

    @Override
    public void log(String message) {

    }

    @Override
    public void onObjectsDetected(Lib.Detection[] detections) {
        // Pls only uncomment for debugging, slows down the video -> detection gets worse
        /* System.out.println("detections:");
        for (Lib.Detection detection : detections) {
            System.out.println(detection);
        } */
        tracks.addDetections(detections, this.srcWidth, this.srcHeight); // after this, object direction is updated
        // TODO: Filter / combine tracks, find bounces, side changes and outOfFrames
        callback.onStrikeFound(tracks);
    }
}