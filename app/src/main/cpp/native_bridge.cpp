#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <mutex>
#include <string>

#define LOG_TAG "SplitProxyNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef int (*run_fn)(const char *, unsigned short, bool);
typedef int (*stop_fn)();

static std::mutex g_mutex;
static void *g_handle = nullptr;
static run_fn g_run = nullptr;
static stop_fn g_stop = nullptr;

static void throw_state(JNIEnv *env, const std::string &message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) {
        env->ThrowNew(cls, message.c_str());
    }
}

static bool load_engine(JNIEnv *env) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_run != nullptr && g_stop != nullptr) return true;

    dlerror();
    g_handle = dlopen("libtun2proxy.so", RTLD_NOW | RTLD_LOCAL);
    if (g_handle == nullptr) {
        const char *error = dlerror();
        std::string message = "Не загружена libtun2proxy.so: ";
        message += error == nullptr ? "неизвестная ошибка Android linker" : error;
        LOGE("%s", message.c_str());
        throw_state(env, message);
        return false;
    }

    dlerror();
    g_run = reinterpret_cast<run_fn>(dlsym(g_handle, "tun2proxy_run_with_cli_args"));
    const char *run_error = dlerror();
    if (run_error != nullptr || g_run == nullptr) {
        std::string message = "В libtun2proxy.so нет tun2proxy_run_with_cli_args: ";
        message += run_error == nullptr ? "символ не найден" : run_error;
        LOGE("%s", message.c_str());
        throw_state(env, message);
        return false;
    }

    dlerror();
    g_stop = reinterpret_cast<stop_fn>(dlsym(g_handle, "tun2proxy_stop"));
    const char *stop_error = dlerror();
    if (stop_error != nullptr || g_stop == nullptr) {
        std::string message = "В libtun2proxy.so нет tun2proxy_stop: ";
        message += stop_error == nullptr ? "символ не найден" : stop_error;
        LOGE("%s", message.c_str());
        throw_state(env, message);
        return false;
    }

    return true;
}

extern "C" JNIEXPORT jint JNICALL
Java_ru_splitproxy_mobile_NativeBridge_nativeRun(
        JNIEnv *env,
        jclass,
        jstring args,
        jint mtu) {
    if (args == nullptr) {
        throw_state(env, "Пустая командная строка tun2proxy");
        return -100;
    }
    if (!load_engine(env)) return -102;

    const char *raw = env->GetStringUTFChars(args, nullptr);
    if (raw == nullptr) return -101;
    const int result = g_run(raw, static_cast<unsigned short>(mtu), false);
    env->ReleaseStringUTFChars(args, raw);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_ru_splitproxy_mobile_NativeBridge_nativeStop(JNIEnv *env, jclass) {
    if (!load_engine(env)) return -102;
    return g_stop();
}

JNIEXPORT jint JNI_OnLoad(JavaVM *, void *) {
    return JNI_VERSION_1_6;
}
