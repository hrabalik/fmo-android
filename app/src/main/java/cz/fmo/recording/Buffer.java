package cz.fmo.recording;

import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

/**
 * Holds encoded video data in a circular buffer.
 * <p>
 * There are separate buffers for the data itself and metadata entries. New frames are added to the
 * back.
 */
public class Buffer {
    private final byte[] mData;
    private final BufferInfo[] mMeta;
    private int mHead = 0;
    private int mTail = 0;
    private MediaFormat mFormat = null;

    /**
     * @param bps     expected bits per second
     * @param fps     expected frames per second
     * @param seconds approximate buffer length
     */
    public Buffer(int bps, float fps, float seconds) {
        // approximate the required buffer sizes
        float nFrames = fps * seconds;
        float nBytes = (bps * seconds) * (1 / 8.f);
        float factor = ((nFrames + 2.f) / nFrames); // add extra space for a few frames
        int dataSize = (int) Math.ceil(factor * nBytes);
        int metaSize = (int) Math.ceil(2.f * nFrames);

        // allocate buffers
        mData = new byte[dataSize];
        mMeta = new BufferInfo[metaSize];
        for (int i = 0; i < metaSize; ++i) {
            mMeta[i] = new BufferInfo();
            mMeta[i].set(-1, -1, -1, 0);
        }
    }

    public boolean empty() {
        return mHead == mTail;
    }

    private boolean full() {
        return next(mTail) == mHead;
    }

    public int next(int i) {
        return (i + 1) % mMeta.length;
    }

    public int prev(int i) {
        return (i + mMeta.length - 1) % mMeta.length;
    }

    public int begin() {
        return mHead;
    }

    public int end() {
        return mTail;
    }

    /**
     * @param first start of range, index of the first frame
     * @param last  end of range, index of the frame after the last frame (past-the-end index)
     * @return range midpoint, index of the frame in the middle
     */
    private int midpoint(int first, int last) {
        if (first <= last) {
            return (first + last) / 2;
        } else {
            return ((first + (last + mMeta.length)) / 2) % mMeta.length;
        }
    }

    /**
     * Binary search for the frame that has a timestamp close to the specified one. It is assumed
     * that the timestamps in the range are strictly increasing, with a fixed frame rate.
     *
     * @param first start of range, index of the first frame
     * @param last  end of range, index of the frame after the last frame (past-the-end index)
     * @param time  time to search for, in microseconds
     * @return a frame in the specified range that is the first or second closest in time to the
     * specified timestamp
     */
    public int findByTime(int first, int last, long time) {
        if (first == last) throw new RuntimeException("findByTime called on an empty range");
        int mid = midpoint(first, last);
        if (mid == first) return first;

        if (time < mMeta[mid].presentationTimeUs) {
            return findByTime(first, mid, time);
        } else {
            return findByTime(mid, last, time);
        }
    }

    /**
     * Finds an I-frame near to the specified index, suitable for being placed as the first frame of
     * a video file.
     *
     * @param index a valid index of a frame
     * @return the closest I-frame to the specified index, or index, if no I-frame is found
     */
    public int findIFrame(int index) {
        if (outOfRange(mHead, mTail, index)) throw new RuntimeException("Bad index");
        boolean backFail = false;
        boolean fwdFail = false;
        int backIdx = index;
        int fwdIdx = index;

        while (!backFail || !fwdFail) {
            if (!backFail) {
                if (isIFrame(backIdx)) return backIdx;
                if (backIdx == mHead) backFail = true;
                backIdx = prev(backIdx);
            }
            if (!fwdFail) {
                fwdIdx = next(fwdIdx);
                if (fwdIdx == mTail) fwdFail = true;
                else if (isIFrame(fwdIdx)) return fwdIdx;
            }
        }

        return index;
    }

    private boolean outOfRange(int first, int last, int index) {
        if (first <= last) return index < first || index >= last;
        else return index < first && index >= last;
    }

    private boolean isIFrame(int index) {
        //noinspection deprecation
        return (mMeta[index].flags & android.media.MediaCodec.BUFFER_FLAG_SYNC_FRAME) != 0;
    }

    /**
     * Calculates the presentation time difference between the first and the last frame in range.
     *
     * @param first start of range, index of the first frame
     * @param last  end of range, index of the frame after the last frame (past-the-end index)
     * @return presentation time delta in microseconds
     */
    public long getDuration(int first, int last) {
        if (first == last) return 0;
        return mMeta[prev(last)].presentationTimeUs - mMeta[first].presentationTimeUs;
    }

    /**
     * Provides a location of the first byte in mData that immediately follows data belonging to
     * the last frame. If data of the specified size wouldn't fit in the data buffer, location 0
     * is returned (i.e., index of the first byte of the buffer). It is not tested whether the block
     * overlaps other blocks. The method cannot fail.
     *
     * @param size size of a new block to be placed
     * @return index into mData that immediately follows data belonging to the last frame, or 0
     */
    private int placeBlock(int size) {
        if (size > mData.length) {
            throw new RuntimeException("frame with " + size + " bytes is too big for the buffer");
        }
        if (empty()) return 0;
        BufferInfo backMeta = mMeta[prev(mTail)];
        int backEnd = backMeta.offset + backMeta.size;
        if (backEnd + size <= mData.length) return backEnd;
        return 0;
    }

    /**
     * Tests whether a block of the specified size inserted at the specified offset overlaps the
     * block owned by the first frame. The block offset must be retrieved using the placeBlock()
     * method.
     *
     * @param offset location of the new block
     * @param size   size of the new block
     */
    private boolean blockOverlapsFront(int offset, int size) {
        if (empty()) return false;
        BufferInfo frontMeta = mMeta[mHead];
        return offset <= frontMeta.offset && offset + size >= frontMeta.offset;
    }

    /**
     * Removes a single frame from the front.
     */
    private void popFront() {
        if (empty()) {
            throw new RuntimeException("popping from an empty buffer");
        }
        mHead = next(mHead);
    }

    /**
     * Adds a single frame to the back. If there is not enough space for the new frame, one or
     * more frames will be removed from the front.
     *
     * @param source array that contains frame data, its position and limit will be reset
     * @param info   frame metadata, including location (offset) and length (size) of the data in
     *               source
     */
    public void pushBack(ByteBuffer source, BufferInfo info) {
        // allocate the new block, pop from front if necessary
        int offset = placeBlock(info.size);
        while (blockOverlapsFront(offset, info.size) || full()) popFront();

        // copy data
        source.clear();
        source.position(info.offset);
        source.limit(info.offset + info.size);
        source.get(mData, offset, info.size);
        source.clear();

        // the index of the new value is the old value of mTail (mTail is always one ahead)
        int index = mTail;
        mTail = next(mTail);

        // copy metadata
        BufferInfo meta = mMeta[index];
        meta.offset = offset;
        meta.size = info.size;
        meta.flags = info.flags;
        meta.presentationTimeUs = info.presentationTimeUs;
    }

    /**
     * Creates a new ByteBuffer that has live access to the data buffer owned by this object. Use
     * the returned object as the parameter of the get() method. Call this method outside
     * performance-critical code.
     *
     * @return cache to be used with the get() method
     */
    public ByteBuffer getCache() {
        return ByteBuffer.wrap(mData);
    }

    /**
     * Provides access to a frame by filling the given data structures.
     *
     * @param index frame index
     * @param cache object to be filled, obtained via getCache()
     * @param info  object to be filled
     */
    public void get(int index, ByteBuffer cache, BufferInfo info) {
        if (outOfRange(mHead, mTail, index)) throw new RuntimeException("Bad index");
        if (cache.array() != mData) throw new RuntimeException("Bad buffer");
        BufferInfo meta = mMeta[index];
        cache.clear();
        cache.position(meta.offset);
        cache.limit(meta.offset + meta.size);
        info.offset = meta.offset;
        info.size = meta.size;
        info.flags = meta.flags;
        info.presentationTimeUs = meta.presentationTimeUs;
    }

    /**
     * @param index frame index
     * @return timestamp of frame at index, in microseconds
     */
    public long getTime(int index) {
        if (outOfRange(mHead, mTail, index)) throw new RuntimeException("Bad index");
        return mMeta[index].presentationTimeUs;
    }

    /**
     * @return the format of stored frames, specified previously via setFormat(). If setFormat() was
     * not called, the return value is null.
     */
    public MediaFormat getFormat() {
        return mFormat;
    }

    /**
     * Stores the format of the encoded video, as produced by e.g. MediaCodec.getOutputFormat().
     * This information is not relevant to storing the data in a buffer, but it may be important
     * for the consumer of the stored data (e.g. MediaMuxer).
     *
     * @param format format of stored frames
     */
    public void setFormat(MediaFormat format) {
        mFormat = format;
    }
}
