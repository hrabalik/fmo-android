#include "java_interface.hpp"
#include "java_classes.hpp"
#include <chrono>

namespace {

    template<typename T>
    void makeUseOf(const T &) { }

    int64_t nanoTime() {
        using clock_t = std::chrono::high_resolution_clock;
        using time_point = clock_t::time_point;
        static time_point origin = clock_t::now();
        auto duration = clock_t::now() - origin;
        return std::chrono::duration_cast<std::chrono::nanoseconds>(duration).count();
    }

    template<typename T>
    struct Quantiles {
        T q50, q95, q99;
    };

    template<typename T>
    struct Stats {
        Stats(const Stats &) = delete;

        Stats &operator=(const Stats &) = delete;

        Stats(size_t storageSize, int sortPeriod, int warmUpFrames) :
                mStorageSize(storageSize), mSortPeriod(sortPeriod), mWarmUpFrames(warmUpFrames) {
            mVec.reserve(mStorageSize);
        }

        void reset(T defVal) {
            mVec.clear();
            mWarmUpCounter = 0;
            mQuantiles.q50 = mQuantiles.q95 = mQuantiles.q99 = defVal;
        }

        bool add(T val) {
            if (mWarmUpCounter++ < mWarmUpFrames) return false;
            mVec.push_back(val);
            if (mVec.size() % mSortPeriod != 0) return false;

            auto iter50 = begin(mVec) + ((50 * mVec.size()) / 100);
            auto iter95 = begin(mVec) + ((95 * mVec.size()) / 100);
            auto iter99 = begin(mVec) + ((99 * mVec.size()) / 100);

            std::nth_element(begin(mVec), iter50, end(mVec));
            std::nth_element(iter50 + 1, iter95, end(mVec));
            std::nth_element(iter95 + 1, iter99, end(mVec));

            mQuantiles.q50 = *iter50;
            mQuantiles.q95 = *iter95;
            mQuantiles.q99 = *iter99;

            if (mVec.size() >= mStorageSize) decimate(mVec);
            return true;
        }

        const Quantiles<T> &quantiles() const {
            return mQuantiles;
        }

    private:
        /**
         * Discard half of the elements in a partially sorted vector so that the statistics are not
         * affected.
         */
        static void decimate(std::vector<T> &vec) {
            auto halfSize = vec.size() / 2;
            auto iter1 = begin(vec);
            auto iter2 = iter1 + halfSize;
            auto iterEnd = end(vec);

            while (iter2 != iterEnd) {
                iter1++;
                iter2++;
                if (iter2 == iterEnd) break;
                *iter1 = *iter2;
                iter1++;
                iter2++;
            }

            vec.resize(halfSize);
        }

        const size_t mStorageSize;
        const int mSortPeriod;
        const int mWarmUpFrames;
        std::vector<T> mVec;
        int mWarmUpCounter;
        Quantiles<T> mQuantiles;
    };

    struct FrameStats {
        FrameStats() : mStats(STORAGE_SIZE, SORT_PERIOD, WARM_UP_FRAMES) { }

        void reset(float defaultFps) {
            mStats.reset(toNs(defaultFps));
            mLastTimeNs = nanoTime();
            updateMyQuantiles();
        }

        bool tick() {
            auto timeNs = nanoTime();
            auto deltaNs = timeNs - mLastTimeNs;
            mLastTimeNs = timeNs;
            if (deltaNs < MIN_DELTA) return false;
            auto updated = mStats.add(deltaNs);
            if (updated) updateMyQuantiles();
            return updated;
        }

        const Quantiles<float> &quantilesFps() const { return mQuantilesFps; }

    private:
        void updateMyQuantiles() {
            auto &quantiles = mStats.quantiles();
            mQuantilesFps.q50 = toFps(quantiles.q50);
            mQuantilesFps.q95 = toFps(quantiles.q95);
            mQuantilesFps.q99 = toFps(quantiles.q99);
        }

        static int64_t toNs(float fps) { return static_cast<uint64_t>(1e9 / fps); }

        static float toFps(int64_t ns) { return static_cast<float>(1e9 / ns); }

        static const size_t STORAGE_SIZE = 1000;
        static const int SORT_PERIOD = 100;
        static const int WARM_UP_FRAMES = 10;
        static const int64_t MIN_DELTA = 500000; // 500K ns, 0.5 ms
        int64_t mLastTimeNs;
        Stats<int64_t> mStats;
        Quantiles<float> mQuantilesFps;
    };

    struct {
        Reference<Callback> callbackRef;
        FrameStats frameStats;
    } global;
}

void Java_cz_fmo_Lib_recording2Start(JNIEnv *env, jclass, jint width, jint height, jobject cbObj) {
    global.callbackRef = {env, cbObj};
    global.frameStats.reset(30.f);

    makeUseOf(width);
    makeUseOf(height);
}

void Java_cz_fmo_Lib_recording2Stop(JNIEnv *env, jclass) {
    global.callbackRef.release(env);
}

void Java_cz_fmo_Lib_recording2Frame(JNIEnv *env, jclass, jbyteArray dataYUV420SP) {
    if (global.frameStats.tick()) {
        auto fps = global.frameStats.quantilesFps();
        auto callback = global.callbackRef.get(env);
        callback.frameTimings(fps.q50, fps.q95, fps.q99);
    }
    makeUseOf(dataYUV420SP);
}
