package cz.fmo.util;

/**
 * Basic computational geometry calculations.
 */
public class CG {
    public static class Vec {
        public float x;
        public float y;

        public String toString() {
            return "{x: " + Float.toString(x) + ", y: " + Float.toString(y) + "}";
        }
    }

    public static void mov(Vec u, Vec out) {
        out.x = u.x;
        out.y = u.y;
    }

    public static void swap(Vec u, Vec v) {
        float t = u.x;
        u.x = v.x;
        v.x = t;
        t = u.y;
        u.y = v.y;
        v.y = t;
    }

    public static float dot(Vec u, Vec v) {
        return u.x * v.x + u.y * v.y;
    }

    public static void add(Vec u, Vec v, Vec out) {
        out.x = u.x + v.x;
        out.y = u.y + v.y;
    }
    
    public static void sub(Vec u, Vec v, Vec out) {
        out.x = u.x - v.x;
        out.y = u.y - v.y;
    }

    public static void mul(float a, Vec u, Vec out) {
        out.x = a * u.x;
        out.y = a * u.y;
    }

    public static void rot90(Vec u, Vec out) {
        float t = u.x;
        out.x = -u.y;
        out.y = t;
    }

    public static void normalize(Vec u, Vec out) {
        float a = (float)(1. / Math.sqrt(dot(u, u)));
        mul(a, u, out);
    }
}
