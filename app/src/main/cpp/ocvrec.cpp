#include "ocvrec.hpp"
#include <thread>
#include <atomic>

namespace {
    struct {
        JavaVM *vm;
        std::atomic<bool> exit;
        Reference<Callback> cbRef;
    } global;
}

void loop();

void ocvRecStop() {
    global.exit = true;
}

void ocvRecStart(JNIEnv *env, jobject cbObj) {
    global.cbRef = {env, cbObj};
    env->GetJavaVM(&global.vm);
    std::thread thread{loop};
    thread.detach();
}

void loop() {
    global.exit = false;
    JNIEnv *env;
    global.vm->AttachCurrentThread(&env, nullptr);
    auto cb = global.cbRef.get(env);
    int frameNum = 0;

    while (!global.exit) {
        cb.frameTimings(1, 2, frameNum++);
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }

    global.cbRef.release(env);
    global.vm->DetachCurrentThread();
}
