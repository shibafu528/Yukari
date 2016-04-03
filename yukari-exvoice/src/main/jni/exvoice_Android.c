//
// Created by shibafu on 2016/04/03.
//
#include <mruby.h>
#include "exvoice_Android_Log.h"

void exvoice_init_android(mrb_state *mrb) {
    struct RClass *android = mrb_define_module(mrb, "Android");

    exvoice_init_android_log(mrb, android);
}

