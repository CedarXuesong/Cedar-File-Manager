#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_openxida_cedarmanager_nativebridge_NativeBridge_architecture(JNIEnv* env, jobject) {
#if defined(__aarch64__)
    constexpr const char* architecture = "arm64-v8a";
#elif defined(__arm__)
    constexpr const char* architecture = "armeabi-v7a";
#elif defined(__x86_64__)
    constexpr const char* architecture = "x86_64";
#else
    constexpr const char* architecture = "unknown";
#endif
    return env->NewStringUTF(architecture);
}
