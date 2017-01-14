#include <jni.h>
#include <vector>

int fac(std::vector<int> nums) {
    int result = 1;
    for (int n : nums) {
        result *= n;
    }
    return result;
}

namespace {
    const char* const HELLO_STR = "Hello from C++";
    using env_t = JNIEnv*;
    using this_t = jobject;
}

extern "C"
jstring Java_cz_fmo_Main_getHelloString(env_t env, this_t) {
    return env->NewStringUTF(HELLO_STR);
}
