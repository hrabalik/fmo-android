#ifndef FMO_ANDROID_JAVA_CLASSES_HPP
#define FMO_ANDROID_JAVA_CLASSES_HPP

#include <jni.h>
#include <cstdint>

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
    using Object::Object;

    void frameTimings(float q50, float q95, float q99) const;
};

#endif //FMO_ANDROID_JAVA_CLASSES_HPP
