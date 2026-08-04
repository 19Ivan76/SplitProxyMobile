package ru.splitproxy.mobile;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ProxyStateStore {
    private static final String FILE_NAME = "vpn-heartbeat";
    private static final long STALE_AFTER_MS = 12_000L;

    private ProxyStateStore() {}

    public static void heartbeat(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(Long.toString(System.currentTimeMillis()).getBytes(StandardCharsets.US_ASCII));
        } catch (IOException ignored) {
        }
    }

    public static void stopped(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (file.exists()) file.delete();
    }

    public static boolean isRunning(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) return false;
        boolean fresh = System.currentTimeMillis() - file.lastModified() < STALE_AFTER_MS;
        if (!fresh) file.delete();
        return fresh;
    }
}
