package ru.splitproxy.mobile;

public final class NativeBridge {
    static {
        System.loadLibrary("tun2proxy");
        System.loadLibrary("splitproxy_jni");
    }

    private NativeBridge() {}

    public static native int run(String commandLine, int mtu);
    public static native int stop();
}
