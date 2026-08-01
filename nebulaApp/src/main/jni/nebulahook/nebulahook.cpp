#include <jni.h>
#include <android/log.h>
#include <string_view>

#include <lsplant.hpp>
#include <external/dobby.h>
#include <external/xdl.h>

namespace {
void *art_handle = nullptr;
bool initialized = false;

void *inline_hook(void *target, void *replacement) {
    void *backup = nullptr;
    return DobbyHook(target, reinterpret_cast<dobby_dummy_func_t>(replacement),
                     reinterpret_cast<dobby_dummy_func_t *>(&backup)) == 0 ? backup : nullptr;
}

bool inline_unhook(void *target) {
    return DobbyDestroy(target) == 0;
}

void *resolve_art(std::string_view symbol) {
    if (!art_handle) return nullptr;
    std::string name(symbol);
    void *result = xdl_sym(art_handle, name.c_str(), nullptr);
    return result ? result : xdl_dsym(art_handle, name.c_str(), nullptr);
}

bool initialize(JNIEnv *env) {
    if (initialized) return true;
    art_handle = xdl_open("libart.so", XDL_DEFAULT);
    if (!art_handle) return false;
    lsplant::InitInfo info{
            .inline_hooker = inline_hook,
            .inline_unhooker = inline_unhook,
            .art_symbol_resolver = resolve_art,
            .art_symbol_prefix_resolver = [](std::string_view) -> void * { return nullptr; },
            .generated_class_name = "NebulaHook_",
            .generated_source_name = "NebulaHook"
    };
    initialized = lsplant::Init(env, info);
    __android_log_print(initialized ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR,
                        "NebulaHook", "LSPlant initialization %s",
                        initialized ? "succeeded" : "failed");
    return initialized;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_allofus_fusioncore_NebulaHook_nativeInitialize(JNIEnv *env, jclass) {
    return initialize(env) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_dev_allofus_fusioncore_NebulaHook_nativeHook(JNIEnv *env, jclass,
                                                  jobject target, jobject hooker) {
    if (!initialize(env)) return nullptr;
    jclass hooker_class = env->GetObjectClass(hooker);
    jmethodID callback = env->GetMethodID(hooker_class, "callback", "([Ljava/lang/Object;)Ljava/lang/Object;");
    if (!callback) return nullptr;
    return lsplant::Hook(env, target, hooker, env->ToReflectedMethod(hooker_class, callback, JNI_FALSE));
}
