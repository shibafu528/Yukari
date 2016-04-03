#include <jni.h>

JavaVM *g_jvm;

jint JNI_onLoad(JavaVM *jvm, void *reserved) {
    g_jvm = jvm;
    return JNI_VERSION_1_6;
}

JNIEnv* getJNIEnv() {
    JNIEnv *env;
    jint ret;

    ret = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        return 0;
    }
    return env;
}