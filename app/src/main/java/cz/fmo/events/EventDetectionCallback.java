package cz.fmo.events;

import cz.fmo.Lib;
import cz.fmo.data.TrackSet;
import cz.fmo.tabletennis.Side;

public interface EventDetectionCallback {
    void onBounce();
    void onSideChange(Side side);
    void onNearlyOutOfFrame(Lib.Detection detection);
    void onStrikeFound(TrackSet tracks);
    void onTableSideChange(Side side);
}
