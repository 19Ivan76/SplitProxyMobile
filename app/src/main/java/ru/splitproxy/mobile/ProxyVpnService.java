package ru.splitproxy.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProxyVpnService extends VpnService {
    public static final String ACTION_START = "ru.splitproxy.mobile.START";
    public static final String ACTION_STOP = "ru.splitproxy.mobile.STOP";

    private static final String TAG = "SplitProxyService";
    private static final String CHANNEL = "split_proxy_status";
    private static final int NOTIFICATION_ID = 1001;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

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
    private volatile int tunFd = -1;

    public static boolean isRunning() {
        return RUNNING.get();
    }

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

        startForeground(NOTIFICATION_ID, buildNotification("Подключение…"));
        if (!RUNNING.get()) startTunnel();
        return START_STICKY;
    }

    private synchronized void startTunnel() {
        Set<String> packages = SelectionStore.load(this);
        if (packages.isEmpty()) {
            stopSelf();
            return;
        }

        Builder builder = new Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(ProxyConfig.MTU)
                .addAddress("10.77.0.2", 32)
                .addAddress("fd00:77::2", 128)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("198.18.0.1")
                .setBlocking(true);

        int accepted = 0;
        for (String packageName : packages) {
            try {
                builder.addAllowedApplication(packageName);
                accepted++;
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Package disappeared: " + packageName);
            }
        }
        if (accepted == 0) {
            stopSelf();
            return;
        }

        ParcelFileDescriptor descriptor = builder.establish();
        if (descriptor == null) {
            stopSelf();
            return;
        }
        tunFd = descriptor.detachFd();
        RUNNING.set(true);
        ProxyStateStore.heartbeat(this);
        heartbeatHandler.removeCallbacks(heartbeat);
        heartbeatHandler.post(heartbeat);
        updateNotification("Работает через " + ProxyConfig.HOST);

        String command = "tun2proxy-bin"
                + " --tun-fd " + tunFd
                + " --close-fd-on-drop true"
                + " --proxy " + ProxyConfig.URL
                + " --dns virtual"
                + " --ipv6-enabled"
                + " --max-sessions 1024"
                + " --tcp-timeout 600"
                + " --udp-timeout 2"
                + " --verbosity info";

        nativeThread = new Thread(() -> {
            int result;
            try {
                result = NativeBridge.run(command, ProxyConfig.MTU);
            } catch (Throwable error) {
                Log.e(TAG, "Native engine failed", error);
                result = -999;
            }
            Log.i(TAG, "tun2proxy exited: " + result);
            RUNNING.set(false);
            ProxyStateStore.stopped(this);
            heartbeatHandler.removeCallbacks(heartbeat);
            tunFd = -1;
            stopSelf();
        }, "tun2proxy");
        nativeThread.start();
    }

    private synchronized void stopTunnel() {
        if (RUNNING.get()) {
            try {
                NativeBridge.stop();
            } catch (Throwable error) {
                Log.w(TAG, "Stop error", error);
            }
        }
        RUNNING.set(false);
        ProxyStateStore.stopped(this);
        heartbeatHandler.removeCallbacks(heartbeat);
        tunFd = -1;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            //noinspection deprecation
            stopForeground(true);
        }
    }

    @Override
    public void onRevoke() {
        stopTunnel();
        stopSelf();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        stopTunnel();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL,
                    "Состояние VPN",
                    NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}
