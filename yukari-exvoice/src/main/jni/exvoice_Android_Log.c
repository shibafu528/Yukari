#include <mruby.h>
#include <android/log.h>

static const char* const LOG_TAG = "exvoice";

static mrb_value log_d(mrb_state *mrb, mrb_value self) {
    char *arg;
    mrb_get_args(mrb, "z", &arg);

    __android_log_print(ANDROID_LOG_DEBUG, "exvoice", arg);

    return self;
}

static mrb_value log_i(mrb_state *mrb, mrb_value self) {
    char *arg;
    mrb_get_args(mrb, "z", &arg);

    __android_log_print(ANDROID_LOG_INFO, "exvoice", arg);

    return self;
}

static mrb_value log_w(mrb_state *mrb, mrb_value self) {
    char *arg;
    mrb_get_args(mrb, "z", &arg);

    __android_log_print(ANDROID_LOG_WARN, "exvoice", arg);

    return self;
}

static mrb_value log_e(mrb_state *mrb, mrb_value self) {
    char *arg;
    mrb_get_args(mrb, "z", &arg);

    __android_log_print(ANDROID_LOG_ERROR, "exvoice", arg);

    return self;
}

static mrb_value log_f(mrb_state *mrb, mrb_value self) {
    char *arg;
    mrb_get_args(mrb, "z", &arg);

    __android_log_print(ANDROID_LOG_FATAL, "exvoice", arg);

    return self;
}

void exvoice_init_android_log(mrb_state *mrb, struct RClass *android) {
    struct RClass *log = mrb_define_module_under(mrb, android, "Log");

    mrb_define_module_function(mrb, log, "d", log_d, MRB_ARGS_REQ(1));
    mrb_define_module_function(mrb, log, "i", log_i, MRB_ARGS_REQ(1));
    mrb_define_module_function(mrb, log, "w", log_w, MRB_ARGS_REQ(1));
    mrb_define_module_function(mrb, log, "e", log_e, MRB_ARGS_REQ(1));
    mrb_define_module_function(mrb, log, "f", log_f, MRB_ARGS_REQ(1));
}

