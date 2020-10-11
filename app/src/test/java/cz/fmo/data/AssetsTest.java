package cz.fmo.data;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(BitmapFactory.class)
public class AssetsTest {
    private Assets assets;
    private AssetManager mockAssetManager;
    private Context mockContext;
    private Bitmap mockBitmap;

    @Before
    public void setUp() throws Exception {
        mockAssetManager = mock(AssetManager.class);
        mockContext = mock(Context.class);
        mockBitmap = mock(Bitmap.class);
        PowerMockito.mockStatic(BitmapFactory.class);
        assets = Assets.getInstance();
    }

    @After
    public void tearDown() throws Exception {
        assets = null;
        mockAssetManager = null;
        mockContext = null;
        mockBitmap = null;
    }

    @Test
    public void getInstance() {
         Assets newAssets = Assets.getInstance();
         assertEquals(newAssets, assets);
         assertSame(newAssets, assets);
    }

    @Test
    public void loadSomeBitmap() throws IOException {
        // check getting a Bitmap before loading
        try {
            assets.getFontTexture();
        } catch (RuntimeException ex) {
            assertNotNull(ex);
        }

        // check loading a Bitmap
        InputStream someInputStream = new ByteArrayInputStream("sometext".getBytes());
        when(mockAssetManager.open(anyString())).thenReturn(someInputStream);
        when(mockContext.getAssets()).thenReturn(mockAssetManager);
        when(BitmapFactory.decodeStream(someInputStream)).thenReturn(mockBitmap);
        assets.load(mockContext);
        assertNotNull(assets.getFontTexture());
        assertSame(assets.getFontTexture(), mockBitmap);
    }
}