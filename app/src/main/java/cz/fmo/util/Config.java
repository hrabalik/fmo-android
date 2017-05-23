package cz.fmo.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public final class Config {
    private static final String CONFIG_FILENAME = "config";
    public final boolean frontFacing;
    public final boolean highResolution;
    public final RecordMode recordMode;
    public final boolean slowPreview;
    public final boolean disableDetection;

    public Config(Context ctx) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(ctx);
        frontFacing = p.getString("facing", "2").equals("1");
        highResolution = p.getString("resolution", "1").equals("2");
        recordMode = getRecordMode(p.getString("recordMode", "1"));
        slowPreview = p.getBoolean("slowPreview", false);
        disableDetection = p.getBoolean("disableDetection", false);
    }

    private RecordMode getRecordMode(String s) {
        switch (s) {
            case "1":
                return RecordMode.MANUAL;
            case "2":
                return RecordMode.AUTOMATIC;
            default:
                return RecordMode.OFF;
        }
    }

    public enum RecordMode {
        OFF,
        MANUAL,
        AUTOMATIC
    }
}
