#ifndef FMO_ANDROID_STATS_HPP
#define FMO_ANDROID_STATS_HPP

#include <cstdint>
#include <vector>

namespace fmo {
    /**
     * Provides current time in the form of the number of nanoseconds relative to a specific a point
     * in time (the origin). The origin does not change during the execution of the program, which
     * makes this function viable for execution time measurements. A system clock with the best
     * precision is used. Monotonicity of the returned times is not guaranteed.
     */
    int64_t nanoTime();

    /**
     * Statistic measurements of a random variable, consisting of the 50% quantile (the median),
     * the 95% quantile, and the 99% quantile.
     */
    template<typename T>
    struct Quantiles {
        Quantiles(T in50, T in95, T in99) : q50(in50), q95(in95), q99(in99) { }
        T q50, q95, q99;
    };

    /**
     * Provides robust statistic measurements of fuzzy quantities, such as execution time. The data
     * type of the measurements is fixed to 64-bit signed integer. Use the add() method to add new
     * samples. The add() method may trigger calculation of quantiles, which are afterwards
     * retrievable using the quantiles() method.
     */
    struct Stats {
        Stats(const Stats &) = delete;

        Stats &operator=(const Stats &) = delete;

        /**
         * @param storageSize The maximum number of samples that will be retained over time. Once
         * the maximum is reached, some of the samples are removed to make space for the new ones.
         * @param sortPeriod The calculation of quantiles is triggered periodically, after the
         * specified number of samples is added.
         * @param warmUpFrames The number of initial samples that will be ignored.
         */
        Stats(size_t storageSize, int sortPeriod, int warmUpFrames);

        /**
         * Restores the object to the initial state. All samples will be removed.
         *
         * @param defVal The value to set all quantiles to.
         */
        void reset(int64_t defVal);

        /**
         * Inserts a sample to an internal vector. Each time sortPeriod (see constructor) samples
         * are added, new quantiles are calculated. This can be detected using the return value of
         * this method.
         *
         * @return True if the quantiles have just been updated. Use the quantiles() method to
         * retrieve them.
         */
        bool add(int64_t val);

        /**
         * @return The statistic measurements (quantiles) as previously calculated by the add()
         * method, or specified using the reset() method, whichever happened last.
         */
        const Quantiles<int64_t> &quantiles() const { return mQuantiles; }

    private:
        /**
         * Discard half of the elements in the underlying sorted or partially sorted vector so that
         * the statistics are not affected.
         */
        void decimate();

        const size_t mStorageSize;
        const int mSortPeriod;
        const int mWarmUpFrames;
        std::vector<int64_t> mVec;
        int mWarmUpCounter;
        Quantiles<int64_t> mQuantiles;
    };

    /**
     * A class that measures frame time and robustly estimates the frame rate in Hertz. Use the
     * tick() method to perform the measurements.
     */
    struct FrameStats {
        FrameStats();

        /**
         * Removes all previously measured values and resets all the quantiles to a given value.
         */
        void reset(float defaultHz);

        /**
         * Performs the frame time measurement. Call this method once per frame. Check the return
         * value to detect whether the quantiles have been updated during the call to this method.
         *
         * @return True if the quantiles have just been updated. Retrieve the quantiles using the
         * quantilesHz() method.
         */
        bool tick();

        /**
         * @return Quantiles, calculated previously by the tick() method, or specified by the
         * reset() method, whichever happened last.
         */
        const Quantiles<float> &quantilesHz() const { return mQuantilesHz; }

    private:
        void updateMyQuantiles();

        Stats mStats;
        int64_t mLastTimeNs;
        Quantiles<float> mQuantilesHz;
    };

    /**
     * A class that robustly estimates execution time of a repeatedly performed code section. Use
     * the start() and stop() methods to mark the beginning and the end of the evaluated code.
     */
    struct SectionStats {
        SectionStats();

        /**
         * Removes all previously measured values and resets all the quantiles to zero.
         */
        void reset();

        /**
         * To be called just before the measured section of code starts.
         */
        void start();

        /**
         * To be called as soon as the measured section of code ends. Check the return value to
         * detect whether the quantiles have been updated during the call to this method.
         *
         * @return True if the quantiles have just been updated. Retrieve the quantiles using the
         * quantilesMs() method.
         */
        bool stop();

        /**
         * @return Quantiles, calculated previously by the stop() method, or specified by the
         * reset() method, whichever happened last.
         */
        const Quantiles<float> &quantilesMs() const { return mQuantilesMs; }

    private:
        void updateMyQuantiles();

        Stats mStats;
        int64_t mStartTimeNs;
        Quantiles<float> mQuantilesMs;
    };
}

#endif //FMO_ANDROID_STATS_HPP
