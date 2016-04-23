#include <mruby.h>
#include <mruby/string.h>
#include <mruby/data.h>
#include <mruby/class.h>
#include <mruby/variable.h>
#include <android/asset_manager.h>
#include <android/log.h>
#include "jni_common.h"

#define log_d(mrb,v) mrb_funcall((mrb), mrb_obj_value((mrb)->kernel_module), "p", 1, (v))

static void assetdir_free(mrb_state *mrb, void *ptr) {
    AAssetDir *dir = ptr;
    if (dir != NULL) {
        AAssetDir_close(dir);
    }
    mrb_free(mrb, dir);
}
const static struct mrb_data_type mrb_assetdir_type = {"AssetDir", assetdir_free};

/**
 * AndroidアプリのAssetから、指定のディレクトリを開きます。
 *
 * @param [String] dir_name ディレクトリ名
 */
static mrb_value assetdir_initialize(mrb_state *mrb, mrb_value self) {
    mrb_value dirName;
    mrb_get_args(mrb, "o", &dirName);

    AAssetManager *manager = getAAssetManager(mrb);
    AAssetDir *dir = AAssetManager_openDir(manager, mrb_str_to_cstr(mrb, dirName));

    DATA_TYPE(self) = &mrb_assetdir_type;
    DATA_PTR(self) = dir;
    mrb_iv_set(mrb, self, mrb_intern_cstr(mrb, "dir_name"), dirName);
    
    return self;
}

/**
 * オープンされているディレクトリを閉じます。
 * 既に閉じられている場合であっても、例外は発しません。
 */
static mrb_value assetdir_close(mrb_state *mrb, mrb_value self) {
    mrb_sym symClosed = mrb_intern_cstr(mrb, "closed");
    mrb_value closed = mrb_iv_get(mrb, self, symClosed);
    AAssetDir *dir = DATA_PTR(self);
    if (!mrb_bool(closed) && dir != NULL) {
        AAssetDir_close(dir);
        DATA_PTR(self) = NULL;
        mrb_iv_set(mrb, self, symClosed, mrb_bool_value(TRUE));
    }

    return mrb_nil_value();
}

/**
 * ディレクトリ内のファイル名を列挙し、与えられたブロックの引数に渡します。
 */
static mrb_value assetdir_each(mrb_state *mrb, mrb_value self) {
    mrb_value block;
    mrb_get_args(mrb, "&", &block);

    if (mrb_nil_p(block)) {
        return mrb_nil_value();
    }

    mrb_value dirName = mrb_iv_get(mrb, self, mrb_intern_cstr(mrb, "dir_name"));
    mrb_str_concat(mrb, dirName, mrb_str_new_cstr(mrb, "/"));

    mrb_sym symRead = mrb_intern_cstr(mrb, "read");
    for (mrb_value filename = mrb_funcall_argv(mrb, self, symRead, 0, NULL);
         !mrb_nil_p(filename);
         filename = mrb_funcall_argv(mrb, self, symRead, 0, NULL)) {

        mrb_yield(mrb, block, mrb_str_plus(mrb, dirName, filename));
    }

    return mrb_nil_value();
}

/**
 * 内部ポインタを進め、次のエントリのファイル名を返します。
 * 既に内部ポインタが末尾に到達していてこれ以上ファイル名を返せない場合、nilを返します。
 *
 * @return [String] ファイル名 or nil
 */
static mrb_value assetdir_read(mrb_state *mrb, mrb_value self) {
    AAssetDir *dir = DATA_PTR(self);
    if (dir == NULL) {
        return mrb_nil_value();
    }

    const char *filename = AAssetDir_getNextFileName(dir);
    if (filename == NULL) {
        return mrb_nil_value();
    }

    return mrb_str_new_cstr(mrb, filename);
}

/**
 * call-seq: open(dir_name)
 *
 * AndroidアプリのAssetから、指定のディレクトリを開きます。
 *
 * @param [String] dir_name ディレクトリ名
 * @return [AssetDir] ディレクトリ内を巡回するインスタンス
 */
static mrb_value assetdir_open(mrb_state *mrb, mrb_value self) {
    mrb_value dirName;
    mrb_get_args(mrb, "o", &dirName);

    return mrb_obj_new(mrb, mrb_class_ptr(self), 1, &dirName);
}

void exvoice_init_android_assetdir(mrb_state *mrb, struct RClass *android) {
    struct RClass *assetDir = mrb_define_class_under(mrb, android, "AssetDir", mrb->object_class);
    MRB_SET_INSTANCE_TT(assetDir, MRB_TT_DATA);

    mrb_include_module(mrb, assetDir, mrb_module_get(mrb, "Enumerable"));
    mrb_define_method(mrb, assetDir, "initialize", assetdir_initialize, MRB_ARGS_REQ(1));
    mrb_define_method(mrb, assetDir, "close", assetdir_close, MRB_ARGS_NONE());
    mrb_define_method(mrb, assetDir, "each", assetdir_each, MRB_ARGS_BLOCK());
    mrb_define_method(mrb, assetDir, "read", assetdir_read, MRB_ARGS_NONE());
    mrb_define_class_method(mrb, assetDir, "open", assetdir_open, MRB_ARGS_REQ(1));
}

