package cz.fmo.util;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Provides access to files located in the directory writable by the application.
 */
public final class FileManager {
    private final Context mContext;

    /**
     * @param context for activities, you should generally pass "this" here
     */
    public FileManager(Context context) {
        mContext = context;
    }

    private File dir() {
        String state = Environment.getExternalStorageState();

        if (!state.equals(Environment.MEDIA_MOUNTED)) {
            return fallbackDir();
        }

        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);

        if (!path.exists()) {
            if (!path.mkdirs()) {
                return fallbackDir();
            }
        }

        return path;
    }

    private File fallbackDir() {
        return mContext.getFilesDir();
    }

    /**
     * @return File object representing a new or existing file in the primary output directory.
     */
    public File open(String name) {
        return new File(dir(), name);
    }


    /**
     * Updates the system catalog so that the new media file shows in compatible apps.
     */
    public void newMedia(File file) {
        MediaScannerConnection.scanFile(mContext, new String[]{file.getAbsolutePath()}, null, null);
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
