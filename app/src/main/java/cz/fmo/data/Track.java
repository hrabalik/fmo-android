package cz.fmo.data;

import java.util.Locale;

import cz.fmo.Lib;
import cz.fmo.graphics.FontRenderer;
import cz.fmo.graphics.TriangleStripRenderer;
import cz.fmo.util.Color;
import cz.fmo.util.Config;

/**
 * A series of objects detected in consecutive frames that are considered to be the same object
 * captured at different times.
 */
public class Track {
    private final Config mConfig;
    private Lib.Detection mLatest;
    private float mLatestDx = 0;
    private float mLatestDy = 0;
    private Color.HSV mColorHSV = new Color.HSV();
    private Color.RGBA mColorRGBA = new Color.RGBA();
    private long mLastDetectionTime;
    private float mMaxVelocity;
    private int mVelocityNumFrames = 0;

    Track(Config config) {
        mConfig = config;
    }

    public Lib.Detection getLatest() {
        return mLatest;
    }

    public Color.RGBA getColor() {
        return mColorRGBA;
    }

    void setLatest(Lib.Detection latest) {
        if (mLatest != null) {
            // calculate speed stats for each segment
            mLatestDx = latest.centerX - mLatest.centerX;
            mLatestDy = latest.centerY - mLatest.centerY;

            latest.directionY = mLatestDy / Math.abs(mLatestDy); // -1 => object is going down | 1 => object going up
            latest.directionX = mLatestDx / Math.abs(mLatestDx); // -1 => object going left | 1 => object going right

            float velocity = latest.velocity;

            // for real-world estimation, apply a formula
            if (mConfig.velocityEstimationMode != Config.VelocityEstimationMode.PX_FR) {
                velocity *= (mConfig.objectRadius / latest.radius) * mConfig.frameRate;
            }

            // convert m/s to other units
            switch (mConfig.velocityEstimationMode) {
                default:
                case PX_FR:
                case M_S:
                    break;
                case KM_H:
                    velocity *= 3.6f;
                    break;
                case MPH:
                    velocity *= 2.23694f;
                    break;
            }

            mMaxVelocity = Math.max(velocity, mMaxVelocity);

            mVelocityNumFrames++;
        }

        mLastDetectionTime = System.nanoTime();
        mLatest = latest;
    }

    public void updateColor() {
        if (mLatestDx == 0 && mLatestDy == 0) return;
        float sinceDetectionSec = ((float) (System.nanoTime() - mLastDetectionTime)) / 1e9f;
        mColorHSV.hsv[0] = (mLatestDx > 0) ? 100.f : 200.f;
        mColorHSV.hsv[1] = Math.min(1.0f, .2f + 0.4f * sinceDetectionSec);
        mColorHSV.hsv[2] = Math.max((mLatestDx > 0) ? 0.6f : 0.8f, 1.f - 0.3f * sinceDetectionSec);
        Color.convert(mColorHSV, mColorRGBA);
    }

    void generateCurve(TriangleStripRenderer.Buffers b) {
        updateColor();
        Lib.generateCurve(mLatest, mColorRGBA.rgba, b);
    }

    void generateLabel(FontRenderer fontRender, float hs, float ws, float left, float top, int i) {
        if (mVelocityNumFrames != 0) {
            String str = String.format(Locale.US, "%5.1f", mMaxVelocity);
            fontRender.addString(str, left + ws, top + (i + 1.5f) * hs, hs, mColorRGBA);
        }
    }
}
