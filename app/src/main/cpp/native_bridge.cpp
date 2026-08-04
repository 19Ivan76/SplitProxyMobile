#include <jni.h>
#include <android/log.h>
#include <string>

#define LOG_TAG "SplitProxyNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" int tun2proxy_run_with_cli_args(
        const char *cli_args,
        unsigned short tun_mtu,
        bool packet_information);
extern "C" int tun2proxy_stop();

extern "C" JNIEXPORT jint JNICALL
Java_ru_splitproxy_mobile_NativeBridge_run(
        JNIEnv *env,
        jclass,
        jstring args,
        jint mtu) {
    if (args == nullptr) return -100;
    const char *raw = env->GetStringUTFChars(args, nullptr);
    if (raw == nullptr) return -101;
    const int result = tun2proxy_run_with_cli_args(
            raw,
            static_cast<unsigned short>(mtu),
            false);
    env->ReleaseStringUTFChars(args, raw);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_ru_splitproxy_mobile_NativeBridge_stop(JNIEnv *, jclass) {
    return tun2proxy_stop();
}
