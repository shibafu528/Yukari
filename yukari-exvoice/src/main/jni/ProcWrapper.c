#include <jni.h>
#include <mruby.h>
#include <mruby/value.h>
#include <mruby/array.h>
#include <mruby/hash.h>
#include <mruby/string.h>
#include <mruby/proc.h>
#include <mruby/error.h>
#include <stddef.h>
#include <android/log.h>
#include "MRuby.h"
#include "jni_converter.h"
#include "jni_common.h"

static inline jlong getField_ProcWrapper_rProcPointer(JNIEnv *env, jobject self) {
    static jfieldID field_ProcWrapper_rProcPointer = NULL;
    if (field_ProcWrapper_rProcPointer == NULL) {
        jclass selfClass = (*env)->GetObjectClass(env, self);
        field_ProcWrapper_rProcPointer = (*env)->GetFieldID(env, selfClass, "rProcPointer", "J");

        (*env)->DeleteLocalRef(env, selfClass);
    }
    return (*env)->GetLongField(env, self, field_ProcWrapper_rProcPointer);
}

JNIEXPORT void JNICALL Java_info_shibafu528_yukari_exvoice_ProcWrapper_disposeNative(JNIEnv *env, jobject self, jlong jMRubyPointer) {
    mrb_state *mrb = (mrb_state *) jMRubyPointer;
    if (mrb != NULL) {
        jlong rProcPointer = getField_ProcWrapper_rProcPointer(env, self);
        mrb_value rProc = mrb_obj_value(rProcPointer);
        mrb_gc_unregister(mrb, rProc);
    }
}

JNIEXPORT jobject JNICALL Java_info_shibafu528_yukari_exvoice_ProcWrapper_execNative(JNIEnv *env, jobject self, jlong jMRubyPointer, jobjectArray jArgs) {
    mrb_state *mrb = (mrb_state *) jMRubyPointer;
    jlong rProcPointer = getField_ProcWrapper_rProcPointer(env, self);
    mrb_value rProc = mrb_obj_value(rProcPointer);

    // Create args array
    jsize argc = (*env)->GetArrayLength(env, jArgs);
    mrb_value *rArgs = mrb_calloc(mrb, (size_t) argc, sizeof(mrb_value));
    for (int i = 0; i < argc; i++) {
        // Convert argument to mrb_value
        jobject obj = (*env)->GetObjectArrayElement(env, jArgs, i);
        rArgs[i] = convertJavaToMrbValue(env, mrb, obj);
        (*env)->DeleteLocalRef(env, obj);
    }

    // Call Proc#call
    mrb_value rResult = mrb_funcall_argv(mrb, rProc, mrb_intern_cstr(mrb, "call"), argc, rArgs);
    mrb_free(mrb, rArgs);

    // Rescue
    if (mrb_exception_p(rResult)) {
        jclass runtimeExceptionClass = (*env)->FindClass(env, "java/lang/RuntimeException");
        mrb_value ins = mrb_inspect(mrb, rResult);
        (*env)->ThrowNew(env, runtimeExceptionClass, mrb_str_to_cstr(mrb, ins));

        (*env)->DeleteLocalRef(env, runtimeExceptionClass);

        mrb->exc = 0;
        return NULL;
    }

    // Convert mrb_value to Java Object
    return convertMrbValueToJava(env, mrb, rResult);
}