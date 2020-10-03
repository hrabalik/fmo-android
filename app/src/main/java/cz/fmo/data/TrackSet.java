package cz.fmo.data;

import android.util.SparseArray;

import java.util.ArrayList;

import cz.fmo.Lib;
import cz.fmo.graphics.FontRenderer;
import cz.fmo.graphics.GL;
import cz.fmo.graphics.TriangleStripRenderer;
import cz.fmo.util.Color;
import cz.fmo.util.Config;

/**
 * Latest detected tracks that are meant to be kept on screen to allow inspection by the user.
 */
public class TrackSet {
    private static final int NUM_TRACKS = 2;
    private final Object mLock = new Object();
    private final ArrayList<Track> mTracks = new ArrayList<>();
    private Config mConfig = null;
    private SparseArray<Track> mCurrentTrackMap = new SparseArray<>();
    private SparseArray<Track> mPreviousTrackMap = new SparseArray<>();
    private int mWidth = 1;  // width of the source image (not necessarily the screen width)
    private int mHeight = 1; // height of the source image (not necessarily the screen height)
    private int mTrackCounter = 0;

    private TrackSet() {
    }

    public static TrackSet getInstance() {
        return SingletonHolder.instance;
    }

    public void setConfig(Config config) {
        synchronized (mLock) {
            mConfig = config;
            clear();
        }
    }

    /**
     * Adds detections to the correct tracks. If there is no predecessor for a given detection, a
     * new track is created.
     *
     * @param width  width of the source image (not the screen)
     * @param height height of the source image (not the screen)
     */
    public void addDetections(Lib.Detection[] detections, int width, int height) {
        synchronized (mLock) {
            if (mConfig == null) return;
            mWidth = width;
            mHeight = height;

            // swap the maps
            {
                SparseArray<Track> temp = mCurrentTrackMap;
                mCurrentTrackMap = mPreviousTrackMap;
                mPreviousTrackMap = temp;
            }

            mCurrentTrackMap.clear();
            for (Lib.Detection detection : detections) {
                if (detection.id < 0) {
                    throw new RuntimeException("ID of a detection not specified");
                }

                // get the track of the predecessor
                Track track = mPreviousTrackMap.get(detection.predecessorId);
                if (track == null) {
                    // no predecessor/track not found: make a new track
                    mTrackCounter++;
                    track = new Track(mConfig);
                    // shift to erase the oldest track
                    if (mTracks.size() == NUM_TRACKS) {
                        mTracks.remove(0);
                    }
                    // add the track to the list
                    mTracks.add(track);
                }

                detection.predecessor = track.getLatest();
                track.setLatest(detection);
                mCurrentTrackMap.put(detection.id, track);
            }
        }
    }

    public ArrayList<Track> getTracks() {return mTracks;}

    public void generateTracksAndLabels(TriangleStripRenderer tsRender, FontRenderer fontRender,
                                        int imageHeight) {
        synchronized (mLock) {
            if (mConfig == null) return;
            generateCurves(tsRender.getBuffers());
            fontRender.clear();
            generateLabels(fontRender, imageHeight);
        }
    }

    private void generateCurves(TriangleStripRenderer.Buffers b) {
        GL.setIdentity(b.posMat);
        b.posMat[0x0] = 2.f / mWidth;
        b.posMat[0x5] = -2.f / mHeight;
        b.posMat[0xC] = -1.f;
        b.posMat[0xD] = 1.f;
        b.pos.clear();
        b.color.clear();
        b.numVertices = 0;

        for (Track track : mTracks) {
            track.generateCurve(b);
        }

        b.pos.limit(b.numVertices * 2);
        b.color.limit(b.numVertices * 4);
    }

    private void generateLabels(FontRenderer fontRender, int imageHeight) {
        if (mTracks.isEmpty()) {
            // don't show anything if there's no tracks
            return;
        }

        Color.RGBA color = new Color.RGBA();
        float hs = ((float) imageHeight) / 18.f;
        float ws = hs * FontRenderer.CHAR_STEP_X;
        int items = mTracks.size();
        float top = 1.f * hs;
        float left = 1.f * hs;

        // draw box and header
        color.rgba[0] = 0.350f;
        color.rgba[1] = 0.350f;
        color.rgba[2] = 0.350f;
        color.rgba[3] = 1.000f;
        fontRender.addRectangle(left, top, 7 * ws, (items + 1) * hs, color);
        color.rgba[0] = 0.745f;
        color.rgba[1] = 0.745f;
        color.rgba[2] = 0.745f;
        color.rgba[3] = 1.000f;

        // pick a label based on mode
        String label;
        switch(mConfig.velocityEstimationMode) {
            default:
            case PX_FR:
                label = "px/fr";
                break;
            case M_S:
                label = "  m/s";
                break;
            case KM_H:
                label = " km/h";
                break;
            case MPH:
                label = "  mph";
                break;
        }

        // write the label
        fontRender.addString(label, left + ws, top + 0.5f * hs, hs, color);

        // draw speeds
        for (int i = 0; i < items; i++) {
            mTracks.get(i).generateLabel(fontRender, hs, ws, left, top, i);
        }
    }

    public void clear() {
        synchronized (mLock) {
            mTracks.clear();
            mPreviousTrackMap.clear();
            mCurrentTrackMap.clear();
        }
    }

    private static class SingletonHolder {
        static final TrackSet instance = new TrackSet();
    }
}
