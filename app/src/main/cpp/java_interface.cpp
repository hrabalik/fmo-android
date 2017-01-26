#include <jni.h>
#include <vector>
#include <opencv2/core.hpp>
#include "java_interface.hpp"
#include "java_classes.hpp"

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
}

jstring Java_cz_fmo_Lib_getHelloString(JNIEnv *env, jclass) {
    if (fac({2, 3, 4}) < 0) {
        ocvShim();
    }
    return env->NewStringUTF(HELLO_STR);
}

void Java_cz_fmo_Lib_onFrame(JNIEnv *env, jclass, jobject imageObj, jobject cbObj) {
    Image image{env, imageObj};
    auto size = image.getSize();
    auto plane = image.getPlane(0);
    Callback cb{env, cbObj};
    cb.frameTimings(plane.pixelStride, plane.rowStride, image.getTimestamp());
}
