#include "java_interface.hpp"
#include "java_classes.hpp"
#include "stats.hpp"

namespace {

    template<typename T>
    void makeUseOf(const T &) { }

    struct {
        Reference<Callback> callbackRef;
        fmo::FrameStats frameStats;
        fmo::SectionStats sectionStats;
        std::vector<uint8_t> buffer;
        bool statsUpdated;
    } global;
}

void Java_cz_fmo_Lib_recording2Start(JNIEnv *env, jclass, jint width, jint height, jobject cbObj) {
    global.callbackRef = {env, cbObj};
    global.frameStats.reset(30.f);
    global.sectionStats.reset();
    global.statsUpdated = true;

    makeUseOf(width);
    makeUseOf(height);
}

void Java_cz_fmo_Lib_recording2Stop(JNIEnv *env, jclass) {
    global.callbackRef.release(env);
}

void Java_cz_fmo_Lib_recording2Frame(JNIEnv *env, jclass, jbyteArray dataYUV420SP) {
    if (global.statsUpdated) {
        global.statsUpdated = false;
        auto q = global.frameStats.quantilesHz();
        //auto q = global.sectionStats.quantilesMs();
        auto callback = global.callbackRef.get(env);
        callback.frameTimings(q.q50, q.q95, q.q99);
    }

    global.frameStats.tick();
    global.sectionStats.start();
    jbyte *ptr = env->GetByteArrayElements(dataYUV420SP, nullptr);
    env->ReleaseByteArrayElements(dataYUV420SP, ptr, JNI_ABORT);
    global.statsUpdated = global.sectionStats.stop();
}
