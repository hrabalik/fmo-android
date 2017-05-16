package cz.fmo.data;

import android.util.SparseArray;

import java.util.ArrayList;

import cz.fmo.Lib;
import cz.fmo.graphics.GL;
import cz.fmo.graphics.TriangleStripRenderer;
import cz.fmo.util.CG;
import cz.fmo.util.Color;

/**
 * Latest detected tracks that are meant to be kept on screen to allow inspection by the user.
 */
public class TrackSet {
    private final int NUM_TRACKS = 8;
    private final Object mLock = new Object();
    private final ArrayList<Track> mTracks = new ArrayList<>();
    private SparseArray<Track> mCurrentTrackMap = new SparseArray<>();
    private SparseArray<Track> mPreviousTrackMap = new SparseArray<>();
    private int mWidth = 1;
    private int mHeight = 1;
    private final GenerateCache mCache = new GenerateCache();

    private TrackSet() {
    }

    public static TrackSet getInstance() {
        return SingletonHolder.instance;
    }

    /**
     * Adds detections to the correct tracks. If there is no predecessor for a given detection, a
     * new track is created.
     */
    public void addDetections(Lib.Detection[] detections, int width, int height) {
        synchronized (mLock) {
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
                    track = new Track();
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

    public void generateCurves(TriangleStripRenderer.Buffers b) {
        synchronized (mLock) {
            GL.setIdentity(b.posMat);
            b.posMat[0x0] = 2.f / mWidth;
            b.posMat[0x5] = -2.f / mHeight;
            b.posMat[0xC] = -1.f;
            b.posMat[0xD] = 1.f;
            b.pos.clear();
            b.color.clear();
            b.numVertices = 0;

            for (Track track : mTracks) {
                track.generateCurve(b, mCache);
            }

            b.pos.flip();
            b.color.flip();
        }
    }

    static class GenerateCache {
        CG.Vec norm1 = new CG.Vec();
        CG.Vec norm2 = new CG.Vec();
        CG.Vec dir1 = new CG.Vec();
        CG.Vec dir2 = new CG.Vec();
        CG.Vec pos1 = new CG.Vec();
        CG.Vec pos2 = new CG.Vec();
        CG.Vec miter = new CG.Vec();
        CG.Vec temp = new CG.Vec();
        Color.RGBA color = new Color.RGBA();
    }

    private static class SingletonHolder {
        static final TrackSet instance = new TrackSet();
    }
}
