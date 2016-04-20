//
// Created by shibafu on 2016/04/03.
//
#include <mruby.h>
#include <mruby/string.h>
#include <mruby/compile.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <stdlib.h>
#include "exvoice_Android_Log.h"
#include "jni_common.h"
#include "MRuby.h"

static AAssetManager* getAAssetManager(mrb_state *mrb) {
    JNIEnv *env = getJNIEnv();
    MRubyInstance *instance = findMRubyInstance(mrb);

    AAssetManager *manager;
    {
        jobject assetManager = (*env)->GetObjectField(env, instance->javaInstance, field_exvoice_MRuby_assetManager);
        manager = AAssetManager_fromJava(env, assetManager);

        (*env)->DeleteLocalRef(env, assetManager);
    }
    return manager;
}

static mrb_value require_assets(mrb_state *mrb, mrb_value self) {
    mrb_value filename;
    mrb_get_args(mrb, "o", &filename);

    AAssetManager *manager = getAAssetManager(mrb);
    AAsset *asset = AAssetManager_open(manager, mrb_str_to_cstr(mrb, filename), AASSET_MODE_UNKNOWN);
    if (asset == NULL) {
        return mrb_nil_value();
    }

    size_t length = (size_t) AAsset_getLength(asset);
    char *buffer = calloc(length, sizeof(char));
    if (0 < AAsset_read(asset, buffer, length)) {
        mrb_load_nstring(mrb, buffer, length);
    }

    AAsset_close(asset);
    return filename;
}

void exvoice_init_android(mrb_state *mrb) {
    struct RClass *android = mrb_define_module(mrb, "Android");

    exvoice_init_android_log(mrb, android);
    mrb_define_module_function(mrb, android, "require_assets", require_assets, MRB_ARGS_REQ(1));
}

