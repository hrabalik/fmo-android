package cz.fmo.data;

import cz.fmo.Lib;
import cz.fmo.graphics.TriangleStripRenderer;
import cz.fmo.util.Color;

/**
 * A series of objects detected in consecutive frames that are considered to be the same object
 * captured at different times.
 */
class Track {
    private Lib.Detection mLatest;
    private Color.HSV mColorHSV = new Color.HSV();
    private Color.RGBA mColorRGBA = new Color.RGBA();
    private long mLastDetectionTime;
    private static final float DECAY_BASE = 0.33f;
    private static final float DECAY_RATE = 0.25f;

    Lib.Detection getLatest() {
        return mLatest;
    }

    void setLatest(Lib.Detection latest) {
        mLastDetectionTime = System.nanoTime();
        mLatest = latest;
    }

    Track(float hue) {
        mColorHSV.hsv[0] = hue;
        mColorHSV.hsv[1] = 0.8f;
    }

    private void updateColor() {
        float sinceDetectionSec = ((float)(System.nanoTime() - mLastDetectionTime)) / 1e9f;
        mColorHSV.hsv[2] = Math.max(0.6f, 1.f - 0.3f * sinceDetectionSec);
        Color.convert(mColorHSV, mColorRGBA);
    }

    void generateCurve(TriangleStripRenderer.Buffers b) {
        updateColor();
        Lib.generateCurve(mLatest, mColorRGBA.rgba, b);
    }
}
