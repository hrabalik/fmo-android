package cz.fmo.events;

import cz.fmo.data.TrackSet;

public interface EventDetectionCallback {
    public void onBounce();
    public void onSideChange(boolean isRightSide);
    public void onBallOutOfFrame(boolean isRightSide);
    public void onStrikeFound(TrackSet tracks);
}
