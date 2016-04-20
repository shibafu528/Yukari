//
// Created by shibafu on 2016/04/21.
//

#ifndef YUKARI_EXVOICE_MRUBY_H
#define YUKARI_EXVOICE_MRUBY_H

#include <jni.h>

typedef struct _MRubyInstance {
    mrb_state* mrb;
    jclass javaInstance;
} MRubyInstance;

typedef enum _MRubyInstanceManagerResult {
    MRB_INSTANCE_SUCCESS,
    MRB_INSTANCE_FAIL_ALLOC,
    MRB_INSTANCE_FAIL_NOT_FOUND
} MRubyInstanceManagerResult;

extern jfieldID field_exvoice_MRuby_assetManager;

MRubyInstance *findMRubyInstance(mrb_state *mrb);
MRubyInstanceManagerResult storeMRubyInstance(mrb_state *mrb, jclass instance);
MRubyInstanceManagerResult removeMRubyInstance(mrb_state *mrb);

#endif //YUKARI_EXVOICE_MRUBY_H
