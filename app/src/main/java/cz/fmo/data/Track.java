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
    private float mLatestDx = 0;
    private float mLatestDy = 0;
    private Color.HSV mColorHSV = new Color.HSV();
    private Color.RGBA mColorRGBA = new Color.RGBA();
    private long mLastDetectionTime;
    private float mVelocityDistanceMax = 0;
    private float mVelocityDistanceSum = 0;
    private int mVelocityNumFrames = 0;

    Lib.Detection getLatest() {
        return mLatest;
    }

    void setLatest(Lib.Detection latest) {
        if (mLatest != null) {
            // calculate speed stats for each segment
            mLatestDx = latest.centerX - mLatest.centerX;
            mLatestDy = latest.centerY - mLatest.centerY;
            float distance = (float) Math.sqrt(mLatestDx * mLatestDx + mLatestDy * mLatestDy);
            mVelocityDistanceMax = Math.max(mVelocityDistanceMax, distance);
            mVelocityDistanceSum += distance;
            mVelocityNumFrames++;
        }

        mLastDetectionTime = System.nanoTime();
        mLatest = latest;
    }

    private void updateColor() {
        if (mLatestDx == 0 && mLatestDy == 0) return;
        float sinceDetectionSec = ((float)(System.nanoTime() - mLastDetectionTime)) / 1e9f;
        mColorHSV.hsv[0] = (mLatestDx > 0) ? 100.f : 200.f;
        mColorHSV.hsv[1] = Math.min(1.0f, .2f + 0.4f * sinceDetectionSec);
        mColorHSV.hsv[2] = Math.max(0.6f, 1.f - 0.3f * sinceDetectionSec);
        Color.convert(mColorHSV, mColorRGBA);
    }

    void generateCurve(TriangleStripRenderer.Buffers b) {
        updateColor();
        Lib.generateCurve(mLatest, mColorRGBA.rgba, b);
    }

    void generateLabel(FontRenderer fontRender, float hs, float ws, float top, int i) {
        if (mVelocityNumFrames != 0) {
            float velocity = mVelocityDistanceMax;
            String str = String.format(Locale.US, "%3.1f", velocity);
            fontRender.addString(str, ws, top + (i + 1.5f) * hs, hs, mColorRGBA);
        }
    }
}
