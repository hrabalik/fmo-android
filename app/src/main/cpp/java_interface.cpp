#include <jni.h>
#include <vector>
#include <algorithm>
#include <opencv2/core.hpp>
#include "java_interface.hpp"
#include "java_classes.hpp"
#include "ocvrec.hpp"

int fac(std::vector<int> nums) {
    int result = 1;
    for (int n : nums) {
        result *= n;
    }
    return result;
}

void ocvShim() {
    cv::Mat m;
    cv::flip(m, m, 0);
}

namespace {
    const char *const HELLO_STR = "Hello from C++";
    using env_t = JNIEnv *;
    using this_t = jobject;

    void timeStats(int64_t deltaNs, const Callback &cb) {
        static std::vector<int64_t> vec(1000);
        vec.push_back(deltaNs);
        if (vec.size() % 100 != 0) return;
        std::sort(begin(vec), end(vec));
        auto avg = std::accumulate(begin(vec), end(vec), 0ll) / vec.size();
        auto iter95 = begin(vec) + ((95 * vec.size()) / 100);
        auto iter99 = begin(vec) + ((99 * vec.size()) / 100);
        cb.frameTimings(1e9f / avg, 1e9f / *iter95, 1e9f / *iter99);
        if (vec.size() >= 18000) vec.clear();
    }
}

jstring Java_cz_fmo_Lib_getHelloString(JNIEnv *env, jclass) {
    if (fac({2, 3, 4}) < 0) {
        ocvShim();
    }
    return env->NewStringUTF(HELLO_STR);
}

void Java_cz_fmo_Lib_onFrame(JNIEnv *env, jclass, jobject imageObj, jobject cbObj) {
    Image image{env, imageObj};
    Callback cb{env, cbObj};
    int64_t timeNs = image.getTimestamp();
    static int64_t prevTimeNs = 0;

    if (prevTimeNs != 0) {
        timeStats(timeNs - prevTimeNs, cb);
    }

    prevTimeNs = timeNs;

    //auto size = image.getSize();
    //auto plane = image.getPlane(0);
    //Callback cb{env, cbObj};
    //cb.frameTimings(plane.pixelStride, plane.rowStride, image.getTimestamp());
}

void Java_cz_fmo_Lib_ocvRecStart(JNIEnv *env, jclass, jobject cbObj) {
    ocvRecStart(env, cbObj);
}

void Java_cz_fmo_Lib_ocvRecStop(JNIEnv *, jclass) {
    ocvRecStop();
}
