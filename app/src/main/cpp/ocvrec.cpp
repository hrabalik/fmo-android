#include "ocvrec.hpp"
#include <thread>
#include <atomic>

namespace {
    struct {
        std::atomic<bool> exit;
    } global;
}

void ocvRecStop() {
    global.exit = true;
}

void ocvRecLoop(JNIEnv *env, jobject cbObj) {
    global.exit = false;
    Callback cb{env, cbObj};
    int frameNum = 0;

    while (!global.exit) {
        cb.frameTimings(1, 2, frameNum++);
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
}
