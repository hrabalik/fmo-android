package cz.fmo.data;

import cz.fmo.Lib;

/**
 * A series of objects detected in consecutive frames that are considered to be the same object
 * captured at different times.
 */
class Track {
    private Lib.Detection mLatest;

    Lib.Detection getLatest() {
        return mLatest;
    }

    void setLatest(Lib.Detection latest) {
        mLatest = latest;
    }
}
