package cz.fmo.util;

import android.content.Context;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Provides access to files located in the directory writable by the application.
 */
public final class FileManager {
    private final Context mCtx;

    /**
     * @param context for activities, you should generally pass "this" here
     */
    public FileManager(Context context) {
        mCtx = context;
    }

    private File dir() {
        return mCtx.getFilesDir();
    }

    public File open(String name) {
        return new File(dir(), name);
    }

    public String[] listMP4() {
        final Pattern p = Pattern.compile(".*\\.mp4");
        String[] out = dir().list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return p.matcher(name).matches();
            }
        });
        Arrays.sort(out);
        return out;
    }
}
