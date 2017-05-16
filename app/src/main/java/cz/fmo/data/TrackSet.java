package cz.fmo.data;

import android.util.SparseArray;

import java.util.ArrayList;

import cz.fmo.Lib;
import cz.fmo.graphics.GL;
import cz.fmo.graphics.TriangleStripRenderer;

/**
 * Latest detected tracks that are meant to be kept on screen to allow inspection by the user.
 */
public class TrackSet {
    private final int NUM_TRACKS = 8;
    private final Object mLock = new Object();
    private final ArrayList<Track> mTracks = new ArrayList<>();
    private SparseArray<Track> mCurrentTrackMap = new SparseArray<>();
    private SparseArray<Track> mPreviousTrackMap = new SparseArray<>();

    private TrackSet() {
    }

    public static TrackSet getInstance() {
        return SingletonHolder.instance;
    }

    /**
     * Adds detections to the correct tracks. If there is no predecessor for a given detection, a
     * new track is created.
     */
    public void addDetections(Lib.Detection[] detections) {
        synchronized (mLock) {
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
        // add a few verts
        GL.setIdentity(b.posMat);
        b.pos.clear();
        b.pos.put(-1);
        b.pos.put(-1);
        b.pos.put(-1);
        b.pos.put(-1);
        b.pos.put(-1);
        b.pos.put(1);
        b.pos.put(1);
        b.pos.put(-1);
        b.pos.put(1);
        b.pos.put(1);
        b.pos.put(1);
        b.pos.put(1);
        b.pos.flip();
        b.color.clear();
        b.color.put(1.f);
        b.color.put(0.f);
        b.color.put(0.f);
        b.color.put(1.f);
        b.color.put(1.f);
        b.color.put(0.f);
        b.color.put(0.f);
        b.color.put(1.f);
        b.color.put(0.f);
        b.color.put(1.f);
        b.color.put(0.f);
        b.color.put(1.f);
        b.color.put(0.f);
        b.color.put(0.f);
        b.color.put(1.f);
        b.color.put(1.f);
        b.color.put(1.f);
        b.color.put(1.f);
        b.color.put(1.f);
        b.color.put(0.f);
        b.color.put(1.f);
        b.color.put(1.f);
        b.color.put(1.f);
        b.color.put(0.f);
        b.color.flip();
        b.numVertices = 6;
    }

    private static class SingletonHolder {
        static final TrackSet instance = new TrackSet();
    }
}
