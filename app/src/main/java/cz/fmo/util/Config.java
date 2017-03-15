package cz.fmo.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

public final class Config {
    private static final String CONFIG_FILENAME = "config";
    private final SharedPreferences mPrefs;
    public boolean record;
    public boolean detect;

    public Config(Context ctx) {
        mPrefs = ctx.getSharedPreferences(CONFIG_FILENAME, 0);
        record = mPrefs.getBoolean("record", true);
        detect = mPrefs.getBoolean("detect", true);
    }

    @SuppressLint("ApplySharedPref")
    public void save() {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean("record", record);
        editor.putBoolean("detect", detect);
        editor.commit();
    }
}
