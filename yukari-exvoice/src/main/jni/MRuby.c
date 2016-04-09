#include <stddef.h>
#include <jni.h>
#include <mruby.h>
#include <mruby/compile.h>
#include <mruby/string.h>
#include <android/log.h>
#include "jni_common.h"
#include "exvoice_Android.h"

typedef struct _MRubyInstance {
    mrb_state* mrb;
    jclass javaInstance;
} MRubyInstance;

typedef enum _MRubyInstanceManagerResult {
    MRB_INSTANCE_SUCCESS,
    MRB_INSTANCE_FAIL_ALLOC,
    MRB_INSTANCE_FAIL_NOT_FOUND
} MRubyInstanceManagerResult;

#define MRB_INSTANCE_STORE_SIZE 16
static MRubyInstance instances[MRB_INSTANCE_STORE_SIZE] = {};

static MRubyInstance* findMRubyInstance(mrb_state *mrb) {
    for (int i = 0; i < MRB_INSTANCE_STORE_SIZE; i++) {
        if (instances[i].mrb == mrb) {
            return &instances[i];
        }
    }
    return NULL;
}

static MRubyInstanceManagerResult storeMRubyInstance(mrb_state *mrb, jclass instance) {
    for (int i = 0; i < MRB_INSTANCE_STORE_SIZE; i++) {
        if (instances[i].mrb == NULL) {
            JNIEnv *env = getJNIEnv();
            instances[i].mrb = mrb;
            instances[i].javaInstance = (*env)->NewGlobalRef(env, instance);

            return MRB_INSTANCE_SUCCESS;
        }
    }
    return MRB_INSTANCE_FAIL_ALLOC;
}

static MRubyInstanceManagerResult removeMRubyInstance(mrb_state *mrb) {
    for (int i = 0; i < MRB_INSTANCE_STORE_SIZE; i++) {
        if (instances[i].mrb == mrb) {
            JNIEnv *env = getJNIEnv();
            (*env)->DeleteGlobalRef(env, instances[i].javaInstance);

            instances[i].mrb = NULL;
            instances[i].javaInstance = NULL;

            return MRB_INSTANCE_SUCCESS;
        }
    }
    return MRB_INSTANCE_FAIL_NOT_FOUND;
}

static mrb_value mrb_printstr(mrb_state *mrb, mrb_value self) {
    mrb_value argv;
    mrb_get_args(mrb, "o", &argv);

    if (mrb_string_p(argv)) {
        char *string = mrb_str_to_cstr(mrb, argv);
        JNIEnv *env = getJNIEnv();
        MRubyInstance *instance = findMRubyInstance(mrb);
        if (instance != NULL) {
            jstring jstr = (*env)->NewStringUTF(env, string);
            jclass jcls = (*env)->GetObjectClass(env, instance->javaInstance);
            jmethodID jmid = (*env)->GetMethodID(env, jcls, "printStringCallback", "(Ljava/lang/String;)V");
            (*env)->CallVoidMethod(env, instance->javaInstance, jmid, jstr);
            (*env)->DeleteLocalRef(env, jstr);
            (*env)->DeleteLocalRef(env, jcls);
        }
    }

    return argv;
}

JNIEXPORT jlong JNICALL Java_info_shibafu528_yukari_exvoice_MRuby_n_1open(JNIEnv *env, jobject self) {
    mrb_state *mrb = mrb_open();
    __android_log_print(ANDROID_LOG_DEBUG, "exvoice", "open addr: %d", mrb);

    // Initialize Objects
    exvoice_init_android(mrb);

    // Override mruby-print Kernel.__printstr__
    mrb_define_module_function(mrb, mrb->kernel_module, "__printstr__", mrb_printstr, MRB_ARGS_REQ(1));

    // Store instances
    storeMRubyInstance(mrb, self);

    return (jlong) mrb;
}

JNIEXPORT void JNICALL Java_info_shibafu528_yukari_exvoice_MRuby_n_1close(JNIEnv *env, jobject self, jlong mrb) {
    removeMRubyInstance((mrb_state*) mrb);
    mrb_close((mrb_state*) mrb);
}

JNIEXPORT void JNICALL Java_info_shibafu528_yukari_exvoice_MRuby_n_1loadString(JNIEnv *env, jobject self, jlong mrb, jstring code) {
    const char *codeBytes = (*env)->GetStringUTFChars(env, code, NULL);
    __android_log_print(ANDROID_LOG_DEBUG, "exvoice", "mrb_load_string\n%s", codeBytes);
    mrb_load_string((mrb_state*) mrb, codeBytes);
    (*env)->ReleaseStringChars(env, code, codeBytes);
}