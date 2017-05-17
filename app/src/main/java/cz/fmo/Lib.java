package cz.fmo;

import cz.fmo.graphics.FontRenderer;
import cz.fmo.graphics.TriangleStripRenderer;

public final class Lib {

    static {
        System.loadLibrary("fmo-android");
    }

    public static native void detectionStart(int width, int height, Callback cb);

    public static native void detectionFrame(byte[] dataYUV420SP);

    public static native void detectionStop();

    public static native void benchmarkingStart(Callback cb);

    public static native void benchmarkingStop();

    public static native void generateCurve(Detection det, float[] rgba,
                                            TriangleStripRenderer.Buffers b);

    public static native void generateString(String str, float x, float y, float h, float[] rgba,
                                             FontRenderer.Buffers b);

    public static native void generateRectangle(float x, float y, float w, float h, float[] rgba,
                                                FontRenderer.Buffers b);

    @SuppressWarnings("unused")
    public interface Callback {
        void log(String message);

        void onObjectsDetected(Detection[] detections);
    }

    @SuppressWarnings("unused")
    public static class Detection {
        public int id;            // unique identifier
        public int predecessorId; // unique identifier of the immediate predecessor
        public int centerX;       // midpoint of the object in the input image, X coordinate
        public int centerY;       // midpoint of the object in the input image, Y coordinate
        public float directionX;  // unit orientation of the object, X coordinate
        public float directionY;  // unit orientation of the object, Y coordinate
        public float length;      // length of the object in input image pixels
        public float radius;      // radius of the object in input image pixels
        public float velocity;    // velocity of the object in pixels per frame

        // Java-specific
        public Detection predecessor;
    }
}
