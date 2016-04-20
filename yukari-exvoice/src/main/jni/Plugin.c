#include <jni.h>
#include <mruby.h>
#include <mruby/value.h>
#include <mruby/array.h>
#include <mruby/string.h>
#include <stddef.h>

static mrb_value convertJavaToMrbValue(JNIEnv *env, mrb_state *mrb, jobject obj) {
    mrb_value result = mrb_nil_value();

    jclass intClass = (*env)->FindClass(env, "Ljava/lang/Integer;");
    jclass longClass = (*env)->FindClass(env, "Ljava/lang/Long;");
    jclass floatClass = (*env)->FindClass(env, "Ljava/lang/Float;");
    jclass doubleClass = (*env)->FindClass(env, "Ljava/lang/Double;");
    jclass objClass = (*env)->GetObjectClass(env, obj);
    if (obj != NULL) {
        if ((*env)->IsInstanceOf(env, obj, intClass)) {
            // Integer to Fixnum
            jmethodID intValue = (*env)->GetMethodID(env, objClass, "intValue", "()I");
            jint val = (*env)->CallIntMethod(env, obj, intValue);
            result = mrb_fixnum_value(val);
        } else if ((*env)->IsInstanceOf(env, obj, longClass)) {
            // Long to Fixnum
            jmethodID longValue = (*env)->GetMethodID(env, objClass, "longValue", "()J");
            jlong val = (*env)->CallLongMethod(env, obj, longValue);
            result = mrb_fixnum_value(val); // !! incompatible !!
        } else if ((*env)->IsInstanceOf(env, obj, floatClass)) {
            // Float to Float
            jmethodID floatValue = (*env)->GetMethodID(env, objClass, "floatValue", "()F");
            jfloat val = (*env)->CallFloatMethod(env, obj, floatValue);
            result = mrb_float_value(mrb, val);
        } else if ((*env)->IsInstanceOf(env, obj, doubleClass)) {
            // Double to Float
            jmethodID doubleValue = (*env)->GetMethodID(env, objClass, "doubleValue", "()D");
            jdouble val = (*env)->CallDoubleMethod(env, obj, doubleValue);
            result = mrb_float_value(mrb, val);
        } else {
            // Other object to String
            jmethodID toString = (*env)->GetMethodID(env, objClass, "toString", "()Ljava/lang/String;");
            jobject str = (*env)->CallObjectMethod(env, obj, toString);
            const char *cstr = (*env)->GetStringUTFChars(env, str, NULL);
            result = mrb_str_new_cstr(mrb, cstr);

            (*env)->ReleaseStringChars(env, str, cstr);
            (*env)->DeleteLocalRef(env, str);
        }
    }

    (*env)->DeleteLocalRef(env, intClass);
    (*env)->DeleteLocalRef(env, longClass);
    (*env)->DeleteLocalRef(env, floatClass);
    (*env)->DeleteLocalRef(env, doubleClass);
    (*env)->DeleteLocalRef(env, objClass);

    return result;
}

static jobject convertMrbValueToJava(JNIEnv *env, mrb_value value) {
    // TODO: mrb_valueのタイプ判定をしてJavaオブジェクトを作る
    switch (mrb_type(value)) {
        case MRB_TT_STRING:
            return (*env)->NewStringUTF(env, RSTRING_PTR(value));
        case MRB_TT_FIXNUM: {
            jclass intClass = (*env)->FindClass(env, "Ljava/lang/Integer;");
            jmethodID intConstructor = (*env)->GetMethodID(env, intClass, "<init>", "(I)V");
            jobject object = (*env)->NewObject(env, intClass, intConstructor, mrb_fixnum(value));

            (*env)->DeleteLocalRef(env, intClass);
            return object;
        }
        case MRB_TT_FLOAT: {
            jclass doubleClass = (*env)->FindClass(env, "Ljava/lang/Double;");
            jmethodID doubleConstructor = (*env)->GetMethodID(env, doubleClass, "<init>", "(D)V");
            jobject object = (*env)->NewObject(env, doubleClass, doubleConstructor, mrb_float(value));

            (*env)->DeleteLocalRef(env, doubleClass);
            return object;
        }
    }
    return NULL;
}

JNIEXPORT jobjectArray JNICALL Java_info_shibafu528_yukari_exvoice_Plugin_filtering(JNIEnv *env, jclass clazz, jobject mRuby, jstring eventName, jobjectArray args) {
    mrb_state *mrb;
    {
        static jfieldID mRubyInstancePointerFid = NULL;
        if (mRubyInstancePointerFid == NULL) {
            jclass mRubyClass = (*env)->GetObjectClass(env, mRuby);
            mRubyInstancePointerFid = (*env)->GetFieldID(env, mRubyClass, "mrubyInstancePointer", "J");

            (*env)->DeleteLocalRef(env, mRubyClass);
        }
        mrb = (mrb_state*) (*env)->GetLongField(env, mRuby, mRubyInstancePointerFid);
    }

    struct RClass *pluggaloid = mrb_module_get(mrb, "Pluggaloid");
    struct RClass *plugin = mrb_class_get_under(mrb, pluggaloid, "Plugin");

    // Convert eventName to Symbol
    mrb_sym rEventName;
    {
        const char *cEventName = (*env)->GetStringUTFChars(env, eventName, NULL);
        rEventName = mrb_intern_cstr(mrb, cEventName);

        (*env)->ReleaseStringChars(env, eventName, cEventName);
    }

    // Create filtering args array
    jsize argc = (*env)->GetArrayLength(env, args);
    mrb_value *rArgs = mrb_calloc(mrb, (size_t) 1 + argc, sizeof(mrb_value));
    rArgs[0] = mrb_symbol_value(rEventName);
    for (int i = 0; i < argc; i++) {
        // Convert argument to mrb_value
        jobject obj = (*env)->GetObjectArrayElement(env, args, i);
        rArgs[i + 1] = convertJavaToMrbValue(env, mrb, obj);
        (*env)->DeleteLocalRef(env, obj);
    }

    // Call filtering
    mrb_value filteringResult = mrb_funcall_argv(mrb, mrb_obj_value(plugin), mrb_intern_cstr(mrb, "filtering"), 1 + argc, rArgs);
    mrb_free(mrb, rArgs);

    // Create Result Array
    mrb_int resultLength = RARRAY_LEN(filteringResult);
    jobjectArray results;
    {
        jclass objectClass = (*env)->FindClass(env, "Ljava/lang/Object;");
        results = (*env)->NewObjectArray(env, resultLength, objectClass, NULL);

        (*env)->DeleteLocalRef(env, objectClass);
    }

    // Convert mrb_value to Java Object
    for (int i = 0; i < resultLength; i++) {
        mrb_value v = mrb_ary_ref(mrb, filteringResult, i);
        (*env)->SetObjectArrayElement(env, results, i, convertMrbValueToJava(env, v));
    }

    return results;
}
