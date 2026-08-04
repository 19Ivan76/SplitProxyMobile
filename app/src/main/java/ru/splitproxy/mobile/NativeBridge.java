package ru.splitproxy.mobile;

public final class NativeBridge {
    private static volatile boolean loaded;

    private NativeBridge() {}

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        // Load only our small bridge. It will dlopen libtun2proxy.so itself and
        // return the actual Android linker error instead of NoClassDefFoundError.
        System.loadLibrary("splitproxy_jni");
        loaded = true;
    }

    public static int run(String commandLine, int mtu) {
        ensureLoaded();
        return nativeRun(commandLine, mtu);
    }

    public static int stop() {
        if (!loaded) return 0;
        return nativeStop();
    }

    private static native int nativeRun(String commandLine, int mtu);
    private static native int nativeStop();
}
