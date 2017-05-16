package cz.fmo.util;

/**
 * Color utilities.
 */
public class Color {
    public static void convert(HSV hsv, RGBA rgba) {
        int irgb = android.graphics.Color.HSVToColor(hsv.hsv);
        rgba.rgba[0] = ((float) (android.graphics.Color.red(irgb))) / 255.f;
        rgba.rgba[1] = ((float) (android.graphics.Color.green(irgb))) / 255.f;
        rgba.rgba[2] = ((float) (android.graphics.Color.blue(irgb))) / 255.f;
        rgba.rgba[3] = 1.f;
    }

    public static class RGBA {
        public float rgba[] = new float[4];
    }

    public static class HSV {
        public float hsv[] = new float[3];
    }
}
