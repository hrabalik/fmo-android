#include <jni.h>
#include <vector>
#include <opencv2/core.hpp>
#include "interface.hpp"

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

/**
 * Models java.lang.Object
 */
class Object {
public:
    Object(const Object &) = default;

    Object &operator=(const Object &) = default;

    virtual ~Object() = default;

    Object(JNIEnv *env, jobject obj) :
            mEnv(env),
            mObj(obj),
            mClass(env->GetObjectClass(obj)) { }

protected:
    JNIEnv *const mEnv;
    const jobject mObj;
    const jclass mClass;
};

/**
 * Models android.media.Image
 */
struct Image : public Object {
    using Object::Object;

    struct Size {
        int width, height;
    };

    /**
     * Models android.media.Image$Plane
     */
    struct Plane : public Object {
        Plane(JNIEnv *env, jobject obj) : Object(env, obj) {
            auto methodPixelStride = mEnv->GetMethodID(mClass, "getPixelStride", "()I");
            auto methodRowStride = mEnv->GetMethodID(mClass, "getRowStride", "()I");
            auto methodBuffer = mEnv->GetMethodID(mClass, "getBuffer", "()Ljava/nio/ByteBuffer;");
            pixelStride = mEnv->CallIntMethod(mObj, methodPixelStride);
            rowStride = mEnv->CallIntMethod(mObj, methodRowStride);
            auto buffer = mEnv->CallObjectMethod(mObj, methodBuffer);
            data = (uint8_t *) mEnv->GetDirectBufferAddress(buffer);
        }

        int pixelStride;
        int rowStride;
        uint8_t *data;
    };

    int64_t getTimestamp() {
        auto method = mEnv->GetMethodID(mClass, "getTimestamp", "()J");
        return mEnv->CallLongMethod(mObj, method);
    }

    Size getSize() {
        auto methodW = mEnv->GetMethodID(mClass, "getWidth", "()I");
        auto methodH = mEnv->GetMethodID(mClass, "getHeight", "()I");
        Size result;
        result.width = mEnv->CallIntMethod(mObj, methodW);
        result.height = mEnv->CallIntMethod(mObj, methodH);
        return result;
    }

    Plane getPlane(int32_t index) {
        auto method = mEnv->GetMethodID(mClass, "getPlanes", "()[Landroid/media/Image$Plane;");
        auto array = (jobjectArray) mEnv->CallObjectMethod(mObj, method);
        auto object = mEnv->GetObjectArrayElement(array, index);
        return {mEnv, object};
    }
};

/**
 * Models cz.fmo.Lib$FrameCallback
 */
struct Callback : public Object {
    using Object::Object;

    void frameTimings(float q50, float q95, float q99) {
        auto method = mEnv->GetMethodID(mClass, "frameTimings", "(FFF)V");
        mEnv->CallVoidMethod(mObj, method, q50, q95, q99);
    }
};

void Java_cz_fmo_Lib_onFrame(JNIEnv *env, jclass, jobject imageObj, jobject cbObj) {
    Image image{env, imageObj};
    auto size = image.getSize();
    auto plane = image.getPlane(0);
    Callback cb{env, cbObj};
    cb.frameTimings(plane.pixelStride, plane.rowStride, image.getTimestamp());
}
