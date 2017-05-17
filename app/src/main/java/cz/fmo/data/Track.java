package cz.fmo.data;

import java.util.Locale;

import cz.fmo.Lib;
import cz.fmo.graphics.FontRenderer;
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
        mColorHSV.hsv[1] = Math.min(1.0f, .2f + 0.4f * sinceDetectionSec);
        mColorHSV.hsv[2] = Math.max(0.6f, 1.f - 0.3f * sinceDetectionSec);
        Color.convert(mColorHSV, mColorRGBA);
    }

    void generateCurve(TriangleStripRenderer.Buffers b) {
        updateColor();
        Lib.generateCurve(mLatest, mColorRGBA.rgba, b);
    }

    void generateLabel(FontRenderer fontRender, float hs, float ws, float top, int i) {
        String str = String.format(Locale.UK, "%5d", mLatest.id);
        fontRender.addString(str, ws, top + (i + 1.5f) * hs, hs, mColorRGBA);
    }
}
