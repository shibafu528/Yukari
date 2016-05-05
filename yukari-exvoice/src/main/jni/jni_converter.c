#include <jni.h>
#include <mruby.h>
#include <mruby/array.h>
#include <mruby/hash.h>
#include <mruby/string.h>
#include <mruby/proc.h>
#include <stddef.h>

static jfieldID boolean_TRUE = NULL;
static jfieldID boolean_FALSE = NULL;

static jmethodID boolean_equals = NULL;
static jmethodID integer_constructor = NULL;
static jmethodID integer_intValue = NULL;
static jmethodID long_longValue = NULL;
static jmethodID float_floatValue = NULL;
static jmethodID double_constructor = NULL;
static jmethodID double_doubleValue = NULL;

static jmethodID linkedHashMap_constructor = NULL;
static jmethodID linkedHashMap_put = NULL;
static jmethodID map_entrySet = NULL;
static jmethodID map_entry_getKey = NULL;
static jmethodID map_entry_getValue = NULL;
static jmethodID set_iterator = NULL;
static jmethodID iterator_hasNext = NULL;
static jmethodID iterator_next = NULL;

static jmethodID procWrapper_constructor = NULL;

mrb_value convertJavaToMrbValue(JNIEnv *env, mrb_state *mrb, jobject obj) {
    mrb_value result = mrb_nil_value();

    jclass booleanClass = (*env)->FindClass(env, "java/lang/Boolean");
    jclass intClass = (*env)->FindClass(env, "java/lang/Integer");
    jclass longClass = (*env)->FindClass(env, "java/lang/Long");
    jclass floatClass = (*env)->FindClass(env, "java/lang/Float");
    jclass doubleClass = (*env)->FindClass(env, "java/lang/Double");
    jclass arrayClass = (*env)->FindClass(env, "[Ljava/lang/Object;");
    jclass mapClass = (*env)->FindClass(env, "java/util/Map");
    jclass objClass = (*env)->GetObjectClass(env, obj);
    if (obj != NULL) {
        if ((*env)->IsInstanceOf(env, obj, booleanClass)) {
            // Boolean to TrueClass/FalseClass
            if (boolean_equals == NULL) {
                boolean_equals = (*env)->GetMethodID(env, booleanClass, "equals", "(Ljava/lang/Object;)Z");
            }
            if (boolean_TRUE == NULL) {
                boolean_TRUE = (*env)->GetStaticFieldID(env, booleanClass, "TRUE", "Ljava/lang/Boolean;");
            }
            jobject trueObject = (*env)->GetStaticObjectField(env, booleanClass, boolean_TRUE);
            if ((*env)->CallBooleanMethod(env, obj, boolean_equals, trueObject) == JNI_TRUE) {
                result = mrb_true_value();
            } else {
                result = mrb_false_value();
            }
            (*env)->DeleteLocalRef(env, trueObject);
        } else if ((*env)->IsInstanceOf(env, obj, intClass)) {
            // Integer to Fixnum
            if (integer_intValue == NULL) {
                integer_intValue = (*env)->GetMethodID(env, objClass, "intValue", "()I");
            }
            jint val = (*env)->CallIntMethod(env, obj, integer_intValue);
            result = mrb_fixnum_value(val);
        } else if ((*env)->IsInstanceOf(env, obj, longClass)) {
            // Long to Fixnum
            if (long_longValue == NULL) {
                long_longValue = (*env)->GetMethodID(env, objClass, "longValue", "()J");
            }
            jlong val = (*env)->CallLongMethod(env, obj, long_longValue);
            result = mrb_fixnum_value(val); // !! incompatible !!
        } else if ((*env)->IsInstanceOf(env, obj, floatClass)) {
            // Float to Float
            if (float_floatValue == NULL) {
                float_floatValue = (*env)->GetMethodID(env, objClass, "floatValue", "()F");
            }
            jfloat val = (*env)->CallFloatMethod(env, obj, float_floatValue);
            result = mrb_float_value(mrb, val);
        } else if ((*env)->IsInstanceOf(env, obj, doubleClass)) {
            // Double to Float
            if (double_doubleValue == NULL) {
                double_doubleValue = (*env)->GetMethodID(env, objClass, "doubleValue", "()D");
            }
            jdouble val = (*env)->CallDoubleMethod(env, obj, double_doubleValue);
            result = mrb_float_value(mrb, val);
        } else if ((*env)->IsInstanceOf(env, obj, arrayClass)) {
            // Object[] to Array
            result = mrb_ary_new(mrb);
            jsize length = (*env)->GetArrayLength(env, obj);
            for (int i = 0; i < length; i++) {
                jobject o = (*env)->GetObjectArrayElement(env, obj, i);
                mrb_ary_push(mrb, result, convertJavaToMrbValue(env, mrb, o));
                (*env)->DeleteLocalRef(env, o);
            }
        } else if ((*env)->IsInstanceOf(env, obj, mapClass)) {
            // Map to Hash
            if (map_entrySet == NULL) {
                map_entrySet = (*env)->GetMethodID(env, mapClass, "entrySet", "()Ljava/util/Set;");
            }
            if (set_iterator == NULL) {
                jclass setClass = (*env)->FindClass(env, "java/util/Set");
                set_iterator = (*env)->GetMethodID(env, setClass, "iterator", "()Ljava/util/Iterator;");

                (*env)->DeleteLocalRef(env, setClass);
            }
            if (iterator_hasNext == NULL || iterator_next == NULL) {
                jclass iteratorClass = (*env)->FindClass(env, "java/util/Iterator");
                iterator_hasNext = (*env)->GetMethodID(env, iteratorClass, "hasNext", "()Z");
                iterator_next = (*env)->GetMethodID(env, iteratorClass, "next", "()Ljava/lang/Object;");

                (*env)->DeleteLocalRef(env, iteratorClass);
            }
            if (map_entry_getKey == NULL || map_entry_getValue == NULL) {
                jclass mapEntryClass = (*env)->FindClass(env, "java/util/Map$Entry");
                map_entry_getKey = (*env)->GetMethodID(env, mapEntryClass, "getKey", "()Ljava/lang/Object;");
                map_entry_getValue = (*env)->GetMethodID(env, mapEntryClass, "getValue", "()Ljava/lang/Object;");

                (*env)->DeleteLocalRef(env, mapEntryClass);
            }

            result = mrb_hash_new(mrb);
            jobject set = (*env)->CallObjectMethod(env, obj, map_entrySet);
            jobject iterator = (*env)->CallObjectMethod(env, set, set_iterator);
            while ((*env)->CallBooleanMethod(env, iterator, iterator_hasNext) == JNI_TRUE) {
                jobject entry = (*env)->CallObjectMethod(env, iterator, iterator_next);
                jobject key = (*env)->CallObjectMethod(env, entry, map_entry_getKey);
                jobject value = (*env)->CallObjectMethod(env, entry, map_entry_getValue);

                mrb_value mKey = convertJavaToMrbValue(env, mrb, key);
                mrb_value mValue = convertJavaToMrbValue(env, mrb, value);
                mrb_hash_set(mrb, result, mKey, mValue);

                (*env)->DeleteLocalRef(env, entry);
                (*env)->DeleteLocalRef(env, key);
                (*env)->DeleteLocalRef(env, value);
            }
            (*env)->DeleteLocalRef(env, set);
            (*env)->DeleteLocalRef(env, iterator);
        } else {
            // Other object to String
            jmethodID toString = (*env)->GetMethodID(env, objClass, "toString", "()Ljava/lang/String;");
            jobject str = (*env)->CallObjectMethod(env, obj, toString);
            const char *cstr = (*env)->GetStringUTFChars(env, str, NULL);
            result = mrb_str_new_cstr(mrb, cstr);

            (*env)->ReleaseStringUTFChars(env, str, cstr);
            (*env)->DeleteLocalRef(env, str);
        }
    }

    (*env)->DeleteLocalRef(env, booleanClass);
    (*env)->DeleteLocalRef(env, intClass);
    (*env)->DeleteLocalRef(env, longClass);
    (*env)->DeleteLocalRef(env, floatClass);
    (*env)->DeleteLocalRef(env, doubleClass);
    (*env)->DeleteLocalRef(env, arrayClass);
    (*env)->DeleteLocalRef(env, mapClass);
    (*env)->DeleteLocalRef(env, objClass);

    return result;
}

jobject convertMrbValueToJava(JNIEnv *env, mrb_state *mrb, mrb_value value) {
    if (mrb_nil_p(value)) {
        return NULL;
    }
    switch (mrb_type(value)) {
        case MRB_TT_TRUE: {
            jclass booleanClass = (*env)->FindClass(env, "java/lang/Boolean");
            if (boolean_TRUE == NULL) {
                boolean_TRUE = (*env)->GetStaticFieldID(env, booleanClass, "TRUE", "Ljava/lang/Boolean;");
            }
            jobject object = (*env)->GetStaticObjectField(env, booleanClass, boolean_TRUE);

            (*env)->DeleteLocalRef(env, booleanClass);
            return object;
        }
        case MRB_TT_FALSE: {
            jclass booleanClass = (*env)->FindClass(env, "java/lang/Boolean");
            if (boolean_FALSE == NULL) {
                boolean_FALSE = (*env)->GetStaticFieldID(env, booleanClass, "FALSE", "Ljava/lang/Boolean;");
            }
            jobject object = (*env)->GetStaticObjectField(env, booleanClass, boolean_FALSE);

            (*env)->DeleteLocalRef(env, booleanClass);
            return object;
        }
        case MRB_TT_STRING:
            return (*env)->NewStringUTF(env, RSTRING_PTR(value));
        case MRB_TT_SYMBOL:
            return (*env)->NewStringUTF(env, mrb_sym2name(mrb, mrb_symbol(value)));
        case MRB_TT_FIXNUM: {
            jclass intClass = (*env)->FindClass(env, "java/lang/Integer");
            if (integer_constructor == NULL) {
                integer_constructor = (*env)->GetMethodID(env, intClass, "<init>", "(I)V");
            }
            jobject object = (*env)->NewObject(env, intClass, integer_constructor, mrb_fixnum(value));

            (*env)->DeleteLocalRef(env, intClass);
            return object;
        }
        case MRB_TT_FLOAT: {
            jclass doubleClass = (*env)->FindClass(env, "java/lang/Double");
            if (double_constructor == NULL) {
                double_constructor = (*env)->GetMethodID(env, doubleClass, "<init>", "(D)V");
            }
            jobject object = (*env)->NewObject(env, doubleClass, double_constructor, mrb_float(value));

            (*env)->DeleteLocalRef(env, doubleClass);
            return object;
        }
        case MRB_TT_ARRAY: {
            jclass objClass = (*env)->FindClass(env, "java/lang/Object");
            jobjectArray array = (*env)->NewObjectArray(env, RARRAY_LEN(value), objClass, NULL);
            for (int i = 0; i < RARRAY_LEN(value); i++) {
                jobject jValue = convertMrbValueToJava(env, mrb, mrb_ary_ref(mrb, value, i));
                (*env)->SetObjectArrayElement(env, array, i, jValue);

                (*env)->DeleteLocalRef(env, jValue);
            }

            (*env)->DeleteLocalRef(env, objClass);
            return array;
        }
        case MRB_TT_HASH: {
            jclass linkedHashMapClass = (*env)->FindClass(env, "java/util/LinkedHashMap");
            if (linkedHashMap_constructor == NULL || linkedHashMap_put == NULL) {
                linkedHashMap_constructor = (*env)->GetMethodID(env, linkedHashMapClass, "<init>", "()V");
                linkedHashMap_put = (*env)->GetMethodID(env, linkedHashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
            }
            jobject map = (*env)->NewObject(env, linkedHashMapClass, linkedHashMap_constructor);

            mrb_value keys = mrb_hash_keys(mrb, value);
            for (int i = 0; i < RARRAY_LEN(keys); i++) {
                mrb_value rKey = mrb_ary_ref(mrb, keys, i);
                mrb_value rValue = mrb_hash_get(mrb, value, rKey);

                jobject jKey = convertMrbValueToJava(env, mrb, rKey);
                jobject jValue = convertMrbValueToJava(env, mrb, rValue);
                (*env)->CallObjectMethod(env, map, linkedHashMap_put, jKey, jValue);

                (*env)->DeleteLocalRef(env, jKey);
                (*env)->DeleteLocalRef(env, jValue);
            }

            (*env)->DeleteLocalRef(env, linkedHashMapClass);
            return map;
        }
        case MRB_TT_PROC: {
            jclass procWrapperClass = (*env)->FindClass(env, "info/shibafu528/yukari/exvoice/ProcWrapper");
            if (procWrapper_constructor == NULL) {
                procWrapper_constructor = (*env)->GetMethodID(env, procWrapperClass, "<init>", "(JJ)V");
            }
            mrb_gc_register(mrb, value);
            jobject object = (*env)->NewObject(env, procWrapperClass, procWrapper_constructor, mrb, mrb_proc_ptr(value));

            (*env)->DeleteLocalRef(env, procWrapperClass);
            return object;
        }
        default:
            return (*env)->NewStringUTF(env, RSTRING_PTR(mrb_obj_as_string(mrb, value)));
    }
    return NULL;
}
