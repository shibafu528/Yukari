#include <jni.h>
#include <mruby.h>
#include <mruby/compile.h>
#include <mruby/string.h>
#include <android/log.h>
#include "jni_common.h"
#include "exvoice_Android.h"

static mrb_value mrb_printstr(mrb_state *mrb, mrb_value self) {
    mrb_value argv;
    mrb_get_args(mrb, "o", &argv);

    if (mrb_string_p(argv)) {
        char *string = mrb_str_to_cstr(mrb, argv);
        JNIEnv *env = getJNIEnv();


    }

    return argv;
}

JNIEXPORT jlong JNICALL Java_info_shibafu528_yukari_exvoice_MRuby_n_1open(JNIEnv *env, jclass clazz) {
    mrb_state *mrb = mrb_open();
    __android_log_print(ANDROID_LOG_DEBUG, "exvoice", "open addr: %d", mrb);

    // Initialize Objects
    exvoice_init_android(mrb);

    // Override mruby-print Kernel.__printstr__
    mrb_define_module_function(mrb, mrb->kernel_module, "__printstr__", mrb_printstr, MRB_ARGS_REQ(1));

    return (jlong) mrb;
}

JNIEXPORT void JNICALL Java_info_shibafu528_yukari_exvoice_MRuby_n_1close(JNIEnv *env, jclass clazz, jlong mrb) {
    mrb_close((mrb_state*) mrb);
}

JNIEXPORT void JNICALL Java_info_shibafu528_yukari_exvoice_MRuby_n_1loadString(JNIEnv *env, jclass clazz, jlong mrb, jstring code) {
    const char *codeBytes = (*env)->GetStringUTFChars(env, code, NULL);
    __android_log_print(ANDROID_LOG_DEBUG, "exvoice", "mrb_load_string\n%s", codeBytes);
    mrb_load_string((mrb_state*) mrb, codeBytes);
    (*env)->ReleaseStringChars(env, code, codeBytes);
}