package cz.fmo;

final class Lib {

    static {
        System.loadLibrary("fmo-android");
    }

    public static native void detectionStart(int width, int height, Callback cb);

    public static native void detectionFrame(byte[] dataYUV420SP);

    public static native void detectionStop();

    public static native void benchmarkingStart(Callback cb);

    public static native void benchmarkingStop();

    @SuppressWarnings("unused")
    public interface Callback {
        void log(String message);

        void onObjectsDetected(Detection[] detections);
    }

    @SuppressWarnings("unused")
    public static class Detection {
        int id;            // unique identifier
        int predecessorId; // unique identifier of the immediate predecessor
        int centerX;       // midpoint of the object in the input image, X coordinate
        int centerY;       // midpoint of the object in the input image, Y coordinate
        float directionX;  // unit orientation of the object, X coordinate
        float directionY;  // unit orientation of the object, Y coordinate
        float length;      // length of the object in input image pixels
        float radius;      // radius of the object in input image pixels
        float velocity;    // velocity of the object in pixels per frame
    }
}
