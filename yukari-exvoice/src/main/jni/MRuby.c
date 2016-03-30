#include <jni.h>
#include <mruby.h>
#include <mruby/compile.h>
#include <android/log.h>

static mrb_value kernel_android_log(mrb_state *mrb, mrb_value self) {
    char *arg;
    mrb_get_args(mrb, "z", &arg);

    __android_log_print(ANDROID_LOG_DEBUG, "exvoice", arg);

    return self;
}

JNIEXPORT jlong JNICALL Java_info_shibafu528_yukari_exvoice_MRuby_n_1open(JNIEnv *env, jclass clazz) {
    mrb_state *mrb = mrb_open();
    __android_log_print(ANDROID_LOG_DEBUG, "exvoice", "open addr: %d", mrb);

    // bind
    mrb_define_module_function(mrb, mrb->kernel_module, "android_log", kernel_android_log, MRB_ARGS_REQ(1));

    return (jlong) mrb;
}

JNIEXPORT void JNICALL Java_info_shibafu528_yukari_exvoice_MRuby_n_1close(JNIEnv *env, jclass clazz, jlong mrb) {
    mrb_close((mrb_state*) mrb);
}

JNIEXPORT void JNICALL Java_info_shibafu528_yukari_exvoice_MRuby_n_1loadString(JNIEnv *env, jclass clazz, jlong mrb, jstring code) {
    const char *codeBytes = (*env)->GetStringUTFChars(env, code, NULL);
    __android_log_print(ANDROID_LOG_DEBUG, "exvoice", "mrb addr: %d", (mrb_state*) mrb);
    mrb_load_string((mrb_state*) mrb, "android_log 'Yo'");
    __android_log_print(ANDROID_LOG_DEBUG, "exvoice", codeBytes);
    mrb_load_string((mrb_state*) mrb, codeBytes);
    (*env)->ReleaseStringChars(env, code, codeBytes);
}