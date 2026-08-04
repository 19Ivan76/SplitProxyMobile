package ru.splitproxy.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProxyVpnService extends VpnService {
    public static final String ACTION_START = "ru.splitproxy.mobile.START";
    public static final String ACTION_STOP = "ru.splitproxy.mobile.STOP";
    public static final String EXTRA_PACKAGES = "packages";

    private static final String TAG = "SplitProxyService";
    private static final String CHANNEL = "split_proxy_status";
    private static final int NOTIFICATION_ID = 1001;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicBoolean STARTING = new AtomicBoolean(false);

    private Thread nativeThread;
    private final Handler heartbeatHandler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (RUNNING.get()) {
                ProxyStateStore.heartbeat(ProxyVpnService.this);
                heartbeatHandler.postDelayed(this, 3000L);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopTunnel();
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("Проверка прокси…", false));
        if (!RUNNING.get() && STARTING.compareAndSet(false, true)) {
            ArrayList<String> extra = intent == null ? null : intent.getStringArrayListExtra(EXTRA_PACKAGES);
            Set<String> packages = extra == null ? SelectionStore.load(this) : new HashSet<>(extra);
            ProxyStateStore.starting(this, "Проверка прокси…");
            new Thread(() -> startTunnel(packages), "vpn-start").start();
        }
        return START_STICKY;
    }

    private void startTunnel(Set<String> packages) {
        try {
            if (packages.isEmpty()) throw new IllegalStateException("Не выбраны приложения");
            testProxy();
            ProxyStateStore.starting(this, "Создание VPN…");
            updateNotification("Создание VPN…", false);

            Builder builder = new Builder()
                    .setSession(getString(R.string.app_name))
                    .setMtu(ProxyConfig.MTU)
                    .addAddress("10.77.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("198.18.0.1");
            // setBlocking(true) здесь нельзя: tun2proxy использует асинхронный TUN.

            int accepted = 0;
            for (String packageName : packages) {
                try {
                    builder.addAllowedApplication(packageName);
                    accepted++;
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "Package disappeared: " + packageName);
                }
            }
            if (accepted == 0) throw new IllegalStateException("Выбранные приложения не найдены");

            ParcelFileDescriptor descriptor = builder.establish();
            if (descriptor == null) throw new IllegalStateException("Android не создал VPN-интерфейс");
            int tunFd = descriptor.detachFd();

            RUNNING.set(true);
            STARTING.set(false);
            ProxyStateStore.running(this);
            heartbeatHandler.removeCallbacks(heartbeat);
            heartbeatHandler.post(heartbeat);
            updateNotification("Работает через " + ProxyConfig.HOST, true);

            String command = "tun2proxy-bin"
                    + " --tun-fd " + tunFd
                    + " --close-fd-on-drop true"
                    + " --proxy " + ProxyConfig.URL
                    + " --dns virtual"
                    + " --max-sessions 1024"
                    + " --tcp-timeout 600"
                    + " --udp-timeout 2"
                    + " --verbosity debug";

            nativeThread = Thread.currentThread();
            int result;
            try {
                result = NativeBridge.run(command, ProxyConfig.MTU);
            } catch (Throwable error) {
                Log.e(TAG, "Native engine failed", error);
                throw new IllegalStateException("Ошибка запуска движка: " + describeError(error), error);
            }

            if (RUNNING.get()) {
                throw new IllegalStateException("Сетевой движок остановился, код " + result);
            }
        } catch (Throwable error) {
            Log.e(TAG, "VPN start failed", error);
            RUNNING.set(false);
            STARTING.set(false);
            heartbeatHandler.removeCallbacks(heartbeat);
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            ProxyStateStore.error(this, message);
            updateNotification("Ошибка: " + message, false);
            stopForeground(false);
            stopSelf();
        }
    }

    private void testProxy() throws Exception {
        ProxyStateStore.starting(this, "Проверка " + ProxyConfig.HOST + ":" + ProxyConfig.PORT + "…");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ProxyConfig.HOST, ProxyConfig.PORT), 6000);
            socket.setSoTimeout(6000);
            OutputStream output = socket.getOutputStream();
            String request = "CONNECT chatgpt.com:443 HTTP/1.1\r\n"
                    + "Host: chatgpt.com:443\r\n"
                    + "Proxy-Connection: close\r\n\r\n";
            output.write(request.getBytes(StandardCharsets.US_ASCII));
            output.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            String status = reader.readLine();
            if (status == null || !status.contains(" 200 ")) {
                throw new IllegalStateException("Прокси ответил: " + (status == null ? "нет ответа" : status));
            }
        } catch (java.net.ConnectException e) {
            throw new IllegalStateException("Прокси недоступен: соединение отклонено");
        } catch (java.net.SocketTimeoutException e) {
            throw new IllegalStateException("Прокси недоступен: тайм-аут");
        }
    }

    private static String describeError(Throwable error) {
        Throwable current = error;
        String best = null;
        int depth = 0;
        while (current != null && depth++ < 8) {
            String message = current.getMessage();
            if (message != null && !message.trim().isEmpty()) best = message.trim();
            current = current.getCause();
        }
        if (best != null) return best;
        return error.getClass().getName();
    }

    private synchronized void stopTunnel() {
        boolean wasActive = RUNNING.getAndSet(false) || STARTING.getAndSet(false);
        heartbeatHandler.removeCallbacks(heartbeat);
        ProxyStateStore.stopped(this);
        if (wasActive) {
            try {
                NativeBridge.stop();
            } catch (Throwable error) {
                Log.w(TAG, "Stop error", error);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            //noinspection deprecation
            stopForeground(true);
        }
    }

    @Override public void onRevoke() {
        stopTunnel();
        stopSelf();
        super.onRevoke();
    }

    @Override public void onDestroy() {
        if (RUNNING.get() || STARTING.get()) stopTunnel();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    private Notification buildNotification(String text, boolean ongoing) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, ProxyVpnService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(ongoing)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);
        if (ongoing) builder.addAction(0, "Отключить", stopPending);
        return builder.build();
    }

    private void updateNotification(String text, boolean ongoing) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification(text, ongoing));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Состояние VPN",
                    NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}
