#include <jni.h>
#include <mruby/version.h>

JNIEXPORT jstring JNICALL Java_info_shibafu528_yukari_exvoice_BuildInfo_getBuildDateTime(JNIEnv *env, jclass clazz) {
    return (*env)->NewStringUTF(env, __DATE__ " " __TIME__);
}

JNIEXPORT jstring JNICALL Java_info_shibafu528_yukari_exvoice_BuildInfo_getMRubyDescription(JNIEnv *env, jclass clazz) {
    return (*env)->NewStringUTF(env, MRUBY_DESCRIPTION);
}

JNIEXPORT jstring JNICALL Java_info_shibafu528_yukari_exvoice_BuildInfo_getMRubyCopyright(JNIEnv *env, jclass clazz) {
    return (*env)->NewStringUTF(env, MRUBY_COPYRIGHT);
}