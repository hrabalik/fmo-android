#ifndef FMO_ANDROID_JAVA_CLASSES_HPP
#define FMO_ANDROID_JAVA_CLASSES_HPP

#include <jni.h>
#include <cstdint>
#include <algorithm>
#include "assert.hpp"

/**
 * Models java.lang.Object
 */
class Object {
public:
    Object(const Object &) = default;

    Object &operator=(const Object &) = default;

    virtual ~Object() = default;

    Object(JNIEnv *env, jobject obj);

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
        Plane(JNIEnv *env, jobject obj);

        int pixelStride;
        int rowStride;
        uint8_t *data;
    };

    int64_t getTimestamp() const;

    Size getSize() const;

    Plane getPlane(int32_t index) const;
};

/**
 * Models cz.fmo.Lib$FrameCallback
 */
struct Callback : public Object {
    Callback(JNIEnv *, jobject);

    void frameTimings(float q50, float q95, float q99) const;

    void cameraError();

private:
    const jmethodID mFrameTimings;
};

/**
 * Wraps other objects, extending their lifetime past the duration of the native function.
 */
template <typename T>
struct Reference {
    Reference(const Reference&) = delete;
    Reference& operator=(const Reference&) = delete;

    Reference() noexcept : mObj(nullptr) { }

    Reference(Reference&& rhs) noexcept : Reference() {
        std::swap(mObj, rhs.mObj);
    }

    Reference& operator=(Reference&& rhs) noexcept {
        std::swap(mObj, rhs.mObj);
        return *this;
    }

    Reference(JNIEnv *env, jobject obj) :
            mObj(env->NewGlobalRef(obj)) { }

    ~Reference() {
        FMO_ASSERT(mObj == nullptr, "release() must be called before a Reference is destroyed");
    }

    void release(JNIEnv *env) {
        env->DeleteGlobalRef(mObj);
        mObj = nullptr;
    }

    T get(JNIEnv *env) {
        return {env, mObj};
    }

private:
    jobject mObj;
};

#endif //FMO_ANDROID_JAVA_CLASSES_HPP
