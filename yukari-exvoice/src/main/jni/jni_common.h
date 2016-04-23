//
// Created by shibafu on 2016/04/03.
//

#ifndef YUKARI_EXVOICE_JNI_COMMON_H
#define YUKARI_EXVOICE_JNI_COMMON_H

#include <jni.h>
#include <android/asset_manager.h>

extern JavaVM *g_jvm;

JNIEnv* getJNIEnv();
AAssetManager* getAAssetManager(mrb_state *mrb);

#endif //YUKARI_EXVOICE_JNI_COMMON_H
