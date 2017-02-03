#ifndef FMO_ANDROID_STATS_HPP
#define FMO_ANDROID_STATS_HPP

#include <cstdint>
#include <vector>

namespace fmo {
    int64_t nanoTime();

    template<typename T>
    struct Quantiles {
        T q50, q95, q99;
    };

    struct Stats {
        Stats(const Stats &) = delete;

        Stats &operator=(const Stats &) = delete;

        Stats(size_t storageSize, int sortPeriod, int warmUpFrames);

        void reset(int64_t defVal);

        bool add(int64_t val);

        const Quantiles<int64_t> &quantiles() const { return mQuantiles; }

    private:
        /**
         * Discard half of the elements in the partially sorted vector so that the statistics are
         * not affected.
         */
        void decimate();

        const size_t mStorageSize;
        const int mSortPeriod;
        const int mWarmUpFrames;
        std::vector<int64_t> mVec;
        int mWarmUpCounter;
        Quantiles<int64_t> mQuantiles;
    };

    struct FrameStats {
        FrameStats();

        void reset(float defaultFps);

        bool tick();

        const Quantiles<float> &quantilesFps() const { return mQuantilesFps; }

    private:
        void updateMyQuantiles();

        int64_t mLastTimeNs;
        Stats mStats;
        Quantiles<float> mQuantilesFps;
    };
}

#endif //FMO_ANDROID_STATS_HPP
