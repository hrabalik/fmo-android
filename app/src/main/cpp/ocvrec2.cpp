#include "java_interface.hpp"
#include "java_classes.hpp"
#include <chrono>

namespace {

    template<typename T>
    void makeUseOf(const T &) { }

    /**
     * Discard half of the elements in a partially sorted vector so that the statistics are not
     * affected.
     */
    void decimate(std::vector<int64_t> &vec) {
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

    const int MAX_TIMES = 10000;

    void timeStats(std::vector<int64_t> &vec, int64_t deltaNs, const Callback &cb) {
        vec.push_back(deltaNs);
        if (vec.size() % 100 != 0) return;

        auto iter50 = begin(vec) + ((50 * vec.size()) / 100);
        auto iter95 = begin(vec) + ((95 * vec.size()) / 100);
        auto iter99 = begin(vec) + ((99 * vec.size()) / 100);

        std::nth_element(begin(vec), iter50, end(vec));
        std::nth_element(iter50 + 1, iter95, end(vec));
        std::nth_element(iter95 + 1, iter99, end(vec));

        cb.frameTimings(1e9f / *iter50, 1e9f / *iter95, 1e9f / *iter99);
        if (vec.size() >= MAX_TIMES) decimate(vec);
    }

    struct {
        Reference<Callback> cbRef;
        std::vector<int64_t> timesVec;
    } global;
}

void Java_cz_fmo_Lib_ocvRec2Start(JNIEnv *env, jclass, jint width, jint height, jobject cbObj) {
    global.cbRef = {env, cbObj};
    global.timesVec.clear();
    global.timesVec.reserve(MAX_TIMES);

    makeUseOf(width);
    makeUseOf(height);
}

void Java_cz_fmo_Lib_ocvRec2Stop(JNIEnv *env, jclass) {
    global.cbRef.release(env);
}

void Java_cz_fmo_Lib_ocvRec2Frame(JNIEnv *env, jclass, jlong matPtr, jlong timeNs) {
    static int64_t lastTime = timeNs;
    int64_t time = timeNs;
    auto deltaNs = time - lastTime;
    lastTime = time;

    if (deltaNs > 500000) {
        auto cb = global.cbRef.get(env);
        timeStats(global.timesVec, deltaNs, cb);

        makeUseOf(matPtr);
    }
}
