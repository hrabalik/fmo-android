package android.graphics;

public class Color {
    public static int red(int irgb) {return irgb / 3;}
    public static int blue(int irgb) {return irgb / 2;}
    public static int green(int irgb) {return irgb / 4;}
    public static int HSVToColor(float[] hsv) {
        int n = hsv.length;
        int res = 0;
        for (int i = 0; i<n; i++) {
            res += (int) hsv[i];
        }
        res = res/n;
        return res;
    }
}
