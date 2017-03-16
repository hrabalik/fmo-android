package cz.fmo.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

public final class Config {
    private static final String CONFIG_FILENAME = "config";
    private final SharedPreferences mPrefs;
    public boolean record = true;
    public boolean detect = true;
    public boolean automatic = false;

    public Config(Context ctx) {
        mPrefs = ctx.getSharedPreferences(CONFIG_FILENAME, 0);
        record = mPrefs.getBoolean("record", record);
        detect = mPrefs.getBoolean("detect", detect);
        automatic = mPrefs.getBoolean("automatic", automatic);
    }

    @SuppressLint("ApplySharedPref")
    public void commit() {
        SharedPreferences.Editor editor = mPrefs.edit();
        save(editor);
        editor.commit();
    }

    public void apply() {
        SharedPreferences.Editor editor = mPrefs.edit();
        save(editor);
        editor.apply();
    }

    private void save(SharedPreferences.Editor editor) {
        editor.putBoolean("record", record);
        editor.putBoolean("detect", detect);
        editor.putBoolean("automatic", automatic);
    }
}
