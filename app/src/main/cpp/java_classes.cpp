#include "java_classes.hpp"

Object::Object(JNIEnv *env, jobject obj) :
        mEnv(env),
        mObj(obj),
        mClass(env->GetObjectClass(obj)) { }

int64_t Image::getTimestamp() const {
    auto method = mEnv->GetMethodID(mClass, "getTimestamp", "()J");
    return mEnv->CallLongMethod(mObj, method);
}

Image::Size Image::getSize() const {
    auto methodW = mEnv->GetMethodID(mClass, "getWidth", "()I");
    auto methodH = mEnv->GetMethodID(mClass, "getHeight", "()I");
    Size result;
    result.width = mEnv->CallIntMethod(mObj, methodW);
    result.height = mEnv->CallIntMethod(mObj, methodH);
    return result;
}

Image::Plane Image::getPlane(int32_t index) const {
    auto method = mEnv->GetMethodID(mClass, "getPlanes", "()[Landroid/media/Image$Plane;");
    auto array = (jobjectArray) mEnv->CallObjectMethod(mObj, method);
    auto object = mEnv->GetObjectArrayElement(array, index);
    return {mEnv, object};
}

Image::Plane::Plane(JNIEnv *env, jobject obj) : Object(env, obj) {
    auto methodPixelStride = mEnv->GetMethodID(mClass, "getPixelStride", "()I");
    auto methodRowStride = mEnv->GetMethodID(mClass, "getRowStride", "()I");
    auto methodBuffer = mEnv->GetMethodID(mClass, "getBuffer", "()Ljava/nio/ByteBuffer;");
    pixelStride = mEnv->CallIntMethod(mObj, methodPixelStride);
    rowStride = mEnv->CallIntMethod(mObj, methodRowStride);
    auto buffer = mEnv->CallObjectMethod(mObj, methodBuffer);
    data = (uint8_t *) mEnv->GetDirectBufferAddress(buffer);
}

Callback::Callback(JNIEnv *env, jobject obj) :
        Object(env, obj),
        mFrameTimings(env->GetMethodID(mClass, "frameTimings", "(FFF)V")) {}

void Callback::frameTimings(float q50, float q95, float q99) const {
    mEnv->CallVoidMethod(mObj, mFrameTimings, q50, q95, q99);
}

void Callback::cameraError() {
    auto id = mEnv->GetMethodID(mClass, "cameraError", "()V");
    mEnv->CallVoidMethod(mObj, id);
}
