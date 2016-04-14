#include <jni.h>
#include <mruby/version.h>

JNIEXPORT jstring JNICALL Java_info_shibafu528_yukari_exvoice_BuildInfo_getBuildDateTime(JNIEnv *env, jclass clazz) {
    return (*env)->NewStringUTF(env, __DATE__ " " __TIME__);
}

JNIEXPORT jstring JNICALL Java_info_shibafu528_yukari_exvoice_BuildInfo_getABI(JNIEnv *env, jclass clazz) {
#if defined(__arm__)
    #if defined(__ARM_ARCH_7A__)
        #if defined(__ARM_NEON__)
            #if defined(__ARM_PCS_VFP)
                #define ABI "armeabi-v7a/NEON (hard-float)"
            #else
                #define ABI "armeabi-v7a/NEON"
            #endif
        #else
            #if defined(__ARM_PCS_VFP)
                #define ABI "armeabi-v7a (hard-float)"
            #else
                #define ABI "armeabi-v7a"
            #endif
        #endif
    #else
        #define ABI "armeabi"
    #endif
#elif defined(__i386__)
    #define ABI "x86"
#elif defined(__x86_64__)
    #define ABI "x86_64"
#elif defined(__mips64)  /* mips64el-* toolchain defines __mips__ too */
    #define ABI "mips64"
#elif defined(__mips__)
    #define ABI "mips"
#elif defined(__aarch64__)
    #define ABI "arm64-v8a"
#else
    #define ABI "unknown"
#endif
    return (*env)->NewStringUTF(env, ABI);
}

JNIEXPORT jstring JNICALL Java_info_shibafu528_yukari_exvoice_BuildInfo_getMRubyDescription(JNIEnv *env, jclass clazz) {
    return (*env)->NewStringUTF(env, MRUBY_DESCRIPTION);
}

JNIEXPORT jstring JNICALL Java_info_shibafu528_yukari_exvoice_BuildInfo_getMRubyCopyright(JNIEnv *env, jclass clazz) {
    return (*env)->NewStringUTF(env, MRUBY_COPYRIGHT);
}

