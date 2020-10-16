package cz.fmo.events;

import cz.fmo.Lib;
import cz.fmo.data.TrackSet;

public interface EventDetectionCallback {
    public void onBounce();
    public void onSideChange(boolean isRightSide);
    public void onNearlyOutOfFrame(Lib.Detection detection);
    public void onStrikeFound(TrackSet tracks);
}
