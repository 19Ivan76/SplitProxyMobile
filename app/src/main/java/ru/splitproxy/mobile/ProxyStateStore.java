package ru.splitproxy.mobile;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class ProxyStateStore {
    public static final String STOPPED = "STOPPED";
    public static final String STARTING = "STARTING";
    public static final String RUNNING = "RUNNING";
    public static final String ERROR = "ERROR";

    private static final String FILE_NAME = "vpn-state.txt";
    private static final long RUNNING_STALE_MS = 15_000L;

    private ProxyStateStore() {}

    public static final class State {
        public final String name;
        public final String message;
        public final long timestamp;

        State(String name, String message, long timestamp) {
            this.name = name;
            this.message = message;
            this.timestamp = timestamp;
        }

        public boolean isActive() {
            return STARTING.equals(name) || RUNNING.equals(name);
        }
    }

    public static void starting(Context context, String message) {
        write(context, STARTING, message);
    }

    public static void running(Context context) {
        write(context, RUNNING, "Подключено через " + ProxyConfig.HOST + ":" + ProxyConfig.PORT);
    }

    public static void heartbeat(Context context) {
        State state = read(context);
        if (RUNNING.equals(state.name)) running(context);
    }

    public static void error(Context context, String message) {
        write(context, ERROR, message);
    }

    public static void stopped(Context context) {
        write(context, STOPPED, "VPN выключен");
    }

    public static State read(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) return new State(STOPPED, "VPN выключен", 0L);
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] chunk = new byte[512];
                int count;
                while ((count = input.read(chunk)) != -1) buffer.write(chunk, 0, count);
            }
            String raw = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 3);
            if (parts.length < 3) return new State(STOPPED, "VPN выключен", 0L);
            long timestamp = Long.parseLong(parts[1]);
            String name = parts[0];
            String message = parts[2];
            if (RUNNING.equals(name) && System.currentTimeMillis() - timestamp > RUNNING_STALE_MS) {
                return new State(ERROR, "VPN-процесс остановился", timestamp);
            }
            return new State(name, message, timestamp);
        } catch (Exception ignored) {
            return new State(STOPPED, "VPN выключен", 0L);
        }
    }

    private static synchronized void write(Context context, String state, String message) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        String safeMessage = String.valueOf(message).replace('|', '/').replace('\n', ' ');
        String raw = state + "|" + System.currentTimeMillis() + "|" + safeMessage;
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(raw.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (Exception ignored) {
        }
    }
}
