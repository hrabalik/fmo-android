package cz.fmo.recording;

import android.media.MediaCodec.BufferInfo;

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

    /**
     * @param bps     expected bits per second
     * @param fps     expected frames per second
     * @param seconds approximate buffer length
     */
    public Buffer(double bps, double fps, double seconds) {
        // approximate the required buffer sizes
        int nBytes = (int) Math.ceil((bps * seconds) * (1 / 8.));
        int nFrames = (int) Math.ceil(2. * fps * seconds);

        // allocate buffers
        mData = new byte[nBytes];
        mMeta = new BufferInfo[nFrames];
        for (int i = 0; i < nFrames; ++i) {
            mMeta[i] = new BufferInfo();
            mMeta[i].set(-1, -1, -1, 0);
        }
    }

    private boolean empty() {
        return mHead == mTail;
    }

    private boolean full() {
        return next(mTail) == mHead;
    }

    private int next(int i) {
        return (i + 1) % mMeta.length;
    }

    private int prev(int i) {
        return (i + mMeta.length - 1) % mMeta.length;
    }

    private int front() {
        return mHead;
    }

    private int back() {
        return prev(mTail);
    }

    /**
     * Provides a location of the first byte in mData that immediately follows data belonging to
     * back(). If data of the specified size wouldn't fit in the data buffer, location 0 is returned
     * (i.e., index of the first byte of the buffer). It is not tested whether the block overlaps
     * other blocks. The method cannot fail.
     *
     * @param size size of a new block to be placed
     * @return index into mData that immediately follows data belonging to back(), or 0
     */
    private int placeBlock(int size) {
        if (size > mData.length) {
            throw new RuntimeException("frame with " + size + " bytes is too big for the buffer");
        }
        if (empty()) return 0;
        BufferInfo backMeta = mMeta[back()];
        int backEnd = backMeta.offset + backMeta.size;
        if (backEnd + size <= mData.length) return backEnd;
        return 0;
    }

    /**
     * Tests whether a block of the specified size inserted at the specified offset overlaps the
     * block owned by front(). The block offset must be retrieved using the placeBlock() method.
     *
     * @param offset location of the new block
     * @param size   size of the new block
     */
    private boolean blockOverlapsFront(int offset, int size) {
        if (empty()) return false;
        BufferInfo frontMeta = mMeta[front()];
        return offset <= frontMeta.offset && offset + size >= frontMeta.offset;
    }

    /**
     * Removes a single element from the front.
     */
    private void popFront() {
        if (empty()) {
            throw new RuntimeException("popping from an empty buffer");
        }
        mHead = next(mHead);
    }

    /**
     * Adds a single element to the back. If there is not enough space for the new element, one or
     * more elements will be removed from the front.
     *
     * @param source array that contains frame data
     * @param info   frame metadata, including location and length of the data
     */
    public void pushBack(byte[] source, BufferInfo info) {
        // allocate the new block, pop from front if necessary
        int offset = placeBlock(info.size);
        while (blockOverlapsFront(offset, info.size) || full()) popFront();

        // copy data
        System.arraycopy(source, info.offset, mData, offset, info.size);

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
     * @return buffer to be used with the get() method
     */
    public ByteBuffer getBuffer() {
        return ByteBuffer.wrap(mData);
    }

    /**
     * Provides access to an element by filling the given data structures.
     *
     * @param index  element index
     * @param buffer object to be filled, obtained via getBuffer()
     * @param info   object to be filled
     */
    public void get(int index, ByteBuffer buffer, BufferInfo info) {
        if (buffer.array() != mData) {
            throw new RuntimeException("buffer has not been acquired by calling getBuffer()");
        }
        BufferInfo meta = mMeta[index];
        buffer.position(meta.offset);
        buffer.limit(meta.offset + meta.size);
        info.offset = meta.offset;
        info.size = meta.size;
        info.flags = meta.flags;
        info.presentationTimeUs = meta.presentationTimeUs;
    }

    /**
     * Calculates the presentation time difference between the first and the last available frame.
     *
     * @return presentation time delta in microseconds
     */
    public long getDuration() {
        if (empty()) return 0;
        return mMeta[back()].presentationTimeUs - mMeta[front()].presentationTimeUs;
    }
}
