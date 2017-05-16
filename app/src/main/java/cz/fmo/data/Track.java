package cz.fmo.data;

import cz.fmo.Lib;
import cz.fmo.graphics.TriangleStripRenderer;
import cz.fmo.util.CG;
import cz.fmo.util.Color;

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

    void generateCurve(TriangleStripRenderer.Buffers b, TrackSet.GenerateCache c) {
        c.color.rgba[0] = 1.f;
        c.color.rgba[1] = 0.f;
        c.color.rgba[2] = 1.f;
        c.color.rgba[3] = 1.f;

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
