#include "ocvrec.hpp"
#include <thread>
#include <atomic>
#include <opencv2/videoio.hpp>

namespace {
    struct {
        JavaVM *vm;
        std::atomic<bool> exit;
        Reference<Callback> cbRef;
    } global;

    struct Env {
        Env(const Env &) = delete;

        Env &operator=(const Env &) = delete;

        Env(const char *threadName) {
            JavaVMAttachArgs args = {JNI_VERSION_1_6, threadName, nullptr};
            jint result = global.vm->AttachCurrentThread(&mPtr, &args);
            FMO_ASSERT(result == JNI_OK, "AttachCurrentThread failed");
        }

        ~Env() { global.vm->DetachCurrentThread(); }

        JNIEnv *get() { return mPtr; }

        JNIEnv &operator->() { return *mPtr; }

    private:
        JNIEnv *mPtr = nullptr;
    };

    template<typename Func>
    struct Finally {
        Finally(Finally &&rhs) : mFunc(rhs.mFunc) { rhs.valid = false; }

        Finally(Func func) : mFunc(func) { }

        ~Finally() { if (valid) mFunc(); }

        // used to get rid of "unused variable" warnings
        void dummy() const { }

    private:
        bool valid = true;
        Func mFunc;
    };

    template<typename Func>
    Finally<Func> finally(Func func) {
        return {func};
    }
}

void ocvRecLoop();

void ocvRecStop() {
    global.exit = true;
}

void ocvRecStart(JNIEnv *env, jobject cbObj) {
    global.cbRef = {env, cbObj};
    env->GetJavaVM(&global.vm);
    std::thread thread{ocvRecLoop};
    thread.detach();
}

void ocvRecLoop() {
    Env env{"ocvRecLoop"};
    global.exit = false;
    auto cb = global.cbRef.get(env.get());
    auto cbCleanup = finally([&env]() { global.cbRef.release(env.get()); });
    cbCleanup.dummy();
    int frameNum = 0;

    // OpenCV camera init
    cv::VideoCapture cap;
    cap.open(0);
    if (!cap.isOpened()) {
        cb.cameraError();
        return;
    }

    while (!global.exit) {
        cb.frameTimings(1, 2, frameNum++);
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
}
