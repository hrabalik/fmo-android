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
        Stats(size_t storageSize, int sortPeriod, int warmUp, T defVal) :
                mStorageSize(storageSize), mSortPeriod(sortPeriod), mWarmUp(warmUp),
                mDefVal(defVal) {
            mVec.reserve(mStorageSize);
            reset();
        }

        void reset() {
            mVec.clear();
            mWarmUpCounter = 0;
            mQuantiles.q50 = mDefVal;
            mQuantiles.q95 = mDefVal;
            mQuantiles.q99 = mDefVal;
        }

        void add(T deltaNs) {
            if (mWarmUpCounter++ < mWarmUp) return;
            mVec.push_back(deltaNs);
            if (mVec.size() % mSortPeriod != 0) return;

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
        }

        Quantiles<T> quantiles() {
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
        const int mWarmUp;
        const T mDefVal;
        std::vector<T> mVec;
        int mWarmUpCounter;
        Quantiles<T> mQuantiles;
    };

    struct TimeStats {
        TimeStats() : mStats(10000, SORT_PERIOD, 10, static_cast<int64_t>(1e9 / 30)) {
            reset();
        }

        void reset() {
            mStats.reset();
            mLastTimeNs = nanoTime();
            mFrameNum = 0;
        }

        void tick(const Callback &callback) {
            auto timeNs = nanoTime();
            auto deltaNs = timeNs - mLastTimeNs;
            mLastTimeNs = timeNs;
            if (deltaNs < MIN_DELTA) return;
            mStats.add(deltaNs);

            if (mFrameNum++ % SORT_PERIOD != 0) return;
            auto q = toFps(mStats.quantiles());
            callback.frameTimings(q.q50, q.q95, q.q99);
        }

    private:
        static Quantiles<float> toFps(Quantiles<int64_t> ns) {
            Quantiles<float> result;
            result.q50 = static_cast<float>(1e9 / ns.q50);
            result.q95 = static_cast<float>(1e9 / ns.q95);
            result.q99 = static_cast<float>(1e9 / ns.q99);
            return result;
        }

        static const int64_t MIN_DELTA = 500000; // 0.5 ms
        static const int SORT_PERIOD = 100;
        int64_t mLastTimeNs;
        Stats<int64_t> mStats;
        int mFrameNum;
    };

    struct {
        Reference<Callback> callbackRef;
        TimeStats timeStats;
    } global;
}

void Java_cz_fmo_Lib_recording2Start(JNIEnv *env, jclass, jint width, jint height, jobject cbObj) {
    global.callbackRef = {env, cbObj};
    global.timeStats.reset();

    makeUseOf(width);
    makeUseOf(height);
}

void Java_cz_fmo_Lib_recording2Stop(JNIEnv *env, jclass) {
    global.callbackRef.release(env);
}

void Java_cz_fmo_Lib_recording2Frame(JNIEnv *env, jclass, jbyteArray dataYUV420SP) {
    auto callback = global.callbackRef.get(env);
    global.timeStats.tick(callback);
    makeUseOf(dataYUV420SP);
}
