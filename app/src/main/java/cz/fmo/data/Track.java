package cz.fmo.data;

import cz.fmo.Lib;
import cz.fmo.graphics.TriangleStripRenderer;
import cz.fmo.util.CG;
import cz.fmo.util.Color;
import cz.fmo.util.Time;

/**
 * A series of objects detected in consecutive frames that are considered to be the same object
 * captured at different times.
 */
class Track {
    private Lib.Detection mLatest;
    private Color.HSV mColor = new Color.HSV();
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
        mColor.hsv[0] = hue;
        mColor.hsv[1] = 0.8f;
    }

    private void getColor(Color.RGBA out) {
        float sinceDetectionSec = ((float)(System.nanoTime() - mLastDetectionTime)) / 1e9f;
        mColor.hsv[2] = Math.max(0.6f, 1.f - 0.3f * sinceDetectionSec);
        Color.convert(mColor, out);
    }

    void generateCurve(TriangleStripRenderer.Buffers b, TrackSet.GenerateCache c) {
        getColor(c.color);
        float decay = 1 - DECAY_BASE;
        c.color.rgba[3] = DECAY_BASE + decay;

        // first vertex
        Lib.Detection d1 = mLatest;
        Lib.Detection d2 = d1.predecessor;
        if (d2 == null) return;
        c.pos1.x = d1.centerX;
        c.pos1.y = d1.centerY;
        c.pos2.x = d2.centerX;
        c.pos2.y = d2.centerY;
        calculateDirAndNormalFromPos(c);
        shiftPoint(c.pos1, d1.radius, c.norm2, c.temp);
        addPoint(b, c.temp, c.color);
        addPoint(b, c.temp, c.color);
        shiftPoint(c.pos1, -d1.radius, c.norm2, c.temp);
        addPoint(b, c.temp, c.color);
        decay *= DECAY_RATE;
        c.color.rgba[3] = DECAY_BASE + decay;

        // middle vertices
        while (true) {
            d1 = d2;
            d2 = d2.predecessor;
            CG.mov(c.pos2, c.pos1);
            CG.mov(c.dir2, c.dir1);
            CG.mov(c.norm2, c.norm1);

            if (d2 == null) break;

            c.pos2.x = d2.centerX;
            c.pos2.y = d2.centerY;
            calculateDirAndNormalFromPos(c);
            averageOfUnitVectors(c.norm1, c.norm2, c.miter);
            shiftPoint(c.pos1, d1.radius, c.norm2, c.temp);
            addPoint(b, c.temp, c.color);
            shiftPoint(c.pos1, -d1.radius, c.norm2, c.temp);
            addPoint(b, c.temp, c.color);
            decay *= DECAY_RATE;
            c.color.rgba[3] = DECAY_BASE + decay;
        }

        // last vertex
        shiftPoint(c.pos1, d1.radius, c.norm1, c.temp);
        addPoint(b, c.temp, c.color);
        shiftPoint(c.pos1, -d1.radius, c.norm1, c.temp);
        addPoint(b, c.temp, c.color);
        addPoint(b, c.temp, c.color);
    }

    private static void calculateDirAndNormalFromPos(TrackSet.GenerateCache c) {
        CG.sub(c.pos2, c.pos1, c.dir2);
        CG.normalize(c.dir2, c.dir2);
        CG.rot90(c.dir2, c.norm2);
    }

    private static void averageOfUnitVectors(CG.Vec u, CG.Vec v, CG.Vec out) {
        CG.add(u, v, out);
        CG.normalize(out, out);
    }

    private static void shiftPoint(CG.Vec pos, float dist, CG.Vec dir, CG.Vec out) {
        CG.mul(dist, dir, out);
        CG.add(pos, out, out);
    }

    private static void addPoint(TriangleStripRenderer.Buffers b, CG.Vec p, Color.RGBA c) {
        if (b.numVertices == TriangleStripRenderer.MAX_VERTICES) return;
        b.pos.put(p.x);
        b.pos.put(p.y);
        b.color.put(c.rgba);
        b.numVertices++;
    }
}
