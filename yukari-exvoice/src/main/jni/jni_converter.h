//
// Created by shibafu on 2016/05/01.
//

#ifndef YUKARI_EXVOICE_JNI_CONVERTER_H
#define YUKARI_EXVOICE_JNI_CONVERTER_H

#include <jni.h>
#include <mruby.h>

mrb_value convertJavaToMrbValue(JNIEnv *env, mrb_state *mrb, jobject obj);
jobject convertMrbValueToJava(JNIEnv *env, mrb_state *mrb, mrb_value value);

#endif //YUKARI_EXVOICE_JNI_CONVERTER_H
