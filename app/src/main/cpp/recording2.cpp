#include "java_interface.hpp"
#include "java_classes.hpp"
#include "stats.hpp"

namespace {

    template<typename T>
    void makeUseOf(const T &) { }

    struct {
        Reference<Callback> callbackRef;
        fmo::FrameStats frameStats;
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
