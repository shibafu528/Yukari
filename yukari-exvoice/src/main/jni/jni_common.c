#include <stddef.h>
#include <jni.h>
#include <android/log.h>

JavaVM *g_jvm;

jint JNI_OnLoad(JavaVM *jvm, void *reserved) {
    __android_log_print(ANDROID_LOG_DEBUG, "exvoice", "JNI_onLoad, jvm addr: %d", jvm);
    g_jvm = jvm;
    return JNI_VERSION_1_6;
}

JNIEnv* getJNIEnv() {
    JNIEnv *env;
    jint ret;

    ret = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        return NULL;
    }
    return env;
}