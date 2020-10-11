package cz.fmo.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Config {
    private final boolean frontFacing;
    private final boolean highResolution;
    private final RecordMode recordMode;
    private final boolean slowPreview;
    private final boolean gray;
    private final int procRes;
    private final VelocityEstimationMode velocityEstimationMode;
    private final float objectRadius;
    private final float frameRate;
    private final boolean disableDetection;

    public Config(Context ctx) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(ctx);
        frontFacing = getFrontFacing(p);
        highResolution = p.getString("resolution", "1").equals("2");
        recordMode = getRecordMode(p);
        slowPreview = p.getBoolean("slowPreview", false);
        gray = getGray(p);
        procRes = (int) getFloatFromString(p, "procRes", "300");
        velocityEstimationMode = getVelocityEstimationMode(p);
        objectRadius = getObjectRadius(p);
        frameRate = getFloatFromString(p, "frameRate", "30.00");
        disableDetection = p.getBoolean("disableDetection", false);
    }

    private boolean getFrontFacing(SharedPreferences p) {
        return p.getString("cameraFacing", "rear").equals("front");
    }

    private RecordMode getRecordMode(SharedPreferences p) {
        String s = p.getString("recordMode", "1");
        switch (s) {
            case "1":
                return RecordMode.MANUAL;
            case "2":
                return RecordMode.AUTOMATIC;
            default:
                return RecordMode.OFF;
        }
    }

    private boolean getGray(SharedPreferences p) {
        return p.getString("colorSpace", "yuv").equals("gray");
    }

    private VelocityEstimationMode getVelocityEstimationMode(SharedPreferences p) {
        String s = p.getString("velocityEstimationMode", "pxfr");
        switch (s) {
            case "ms":
                return VelocityEstimationMode.M_S;
            case "kmh":
                return VelocityEstimationMode.KM_H;
            case "mph":
                return VelocityEstimationMode.MPH;
            default:
                return VelocityEstimationMode.PX_FR;
        }
    }

    private float getFloatFromString(SharedPreferences p, String param, String defaultValue) {
        String frameRate = p.getString(param, defaultValue);

        try {
            return Float.parseFloat(frameRate);
        } catch (NumberFormatException e) {
            return Float.parseFloat(defaultValue);
        }
    }

    private float getObjectRadius(SharedPreferences p) {
        float diameter = getFloatFromString(p, "objectDiameterPicker", "0");

        if (diameter == 0) {
            diameter = getFloatFromString(p, "objectDiameterCustom", "1.00");
        }

        return diameter;
    }

    public float getFrameRate() {
        return frameRate;
    }

    public VelocityEstimationMode getVelocityEstimationMode() {
        return velocityEstimationMode;
    }

    public int getProcRes() {
        return procRes;
    }

    public boolean isFrontFacing() {
        return frontFacing;
    }

    public boolean isHighResolution() {
        return highResolution;
    }

    public RecordMode getRecordMode() {
        return recordMode;
    }

    public boolean isSlowPreview() {
        return slowPreview;
    }

    public boolean isGray() {
        return gray;
    }

    public float getObjectRadius() {
        return objectRadius;
    }

    public boolean isDisableDetection() {
        return disableDetection;
    }

    public enum RecordMode {
        OFF,
        MANUAL,
        AUTOMATIC
    }

    public enum VelocityEstimationMode {
        PX_FR,
        M_S,
        KM_H,
        MPH,
    }
}
