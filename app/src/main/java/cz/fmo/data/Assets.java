package cz.fmo.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;

public class Assets {
    private boolean mLoaded = false;
    private Bitmap mFontTexture;
    private final Object mLock = new Object();

    private Assets() {
    }

    public static Assets getInstance() {
        return SingletonHolder.instance;
    }

    public void load(Context ctx) {
        synchronized (mLock) {
            if (mLoaded) return;
            mLoaded = true;

            try {
                InputStream is = ctx.getAssets().open("font.png");
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inScaled = false;
                mFontTexture = BitmapFactory.decodeStream(is);
            } catch (IOException e) {
                mFontTexture = null;
            }
        }
    }

    private void assetNotReady() {
        throw new RuntimeException("Asset not ready! Call Assets.getInstance().load() first.");
    }

    public Bitmap getFontTexture() {
        synchronized (mLock) {
            if (mFontTexture == null) assetNotReady();
            return mFontTexture;
        }
    }

    private static class SingletonHolder {
        static final Assets instance = new Assets();
    }
}
