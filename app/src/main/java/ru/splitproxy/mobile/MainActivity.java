package ru.splitproxy.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private static final int VPN_REQUEST = 7001;
    private static final int NOTIFICATION_REQUEST = 7002;
    private static final Set<String> DEFAULT_PACKAGES = new HashSet<>(Arrays.asList(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "com.google.android.youtube",
            "com.openai.chatgpt"
    ));

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusPoll = new Runnable() {
        @Override public void run() {
            updateStatus();
            statusHandler.postDelayed(this, 1000L);
        }
    };
    private final Set<String> selectedPackages = new HashSet<>();
    private AppListAdapter adapter;
    private TextView status;
    private Button startButton;
    private Button stopButton;
    private View progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        progress = findViewById(R.id.progress);
        TextView proxy = findViewById(R.id.proxyAddress);
        proxy.setText(ProxyConfig.HOST + ":" + ProxyConfig.PORT);

        RecyclerView list = findViewById(R.id.appList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppListAdapter((app, selected) -> {
            if (selected) selectedPackages.add(app.packageName);
            else selectedPackages.remove(app.packageName);
            SelectionStore.save(this, selectedPackages);
            updateButtons();
        });
        list.setAdapter(adapter);

        EditText search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        startButton.setOnClickListener(v -> requestStart());
        stopButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProxyVpnService.class);
            intent.setAction(ProxyVpnService.ACTION_STOP);
            startService(intent);
            updateStatus();
        });

        selectedPackages.addAll(SelectionStore.load(this));
        loadApplications();
        requestNotificationsIfNeeded();
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
        }
    }

    private void loadApplications() {
        progress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            boolean initialized = SelectionStore.isInitialized(this);
            List<AppEntry> entries = new ArrayList<>();

            for (ApplicationInfo info : installed) {
                if (getPackageName().equals(info.packageName)) continue;
                if (pm.getLaunchIntentForPackage(info.packageName) == null) continue;

                boolean selected = selectedPackages.contains(info.packageName);
                if (!initialized && DEFAULT_PACKAGES.contains(info.packageName)) {
                    selected = true;
                    selectedPackages.add(info.packageName);
                }
                entries.add(new AppEntry(
                        String.valueOf(pm.getApplicationLabel(info)),
                        info.packageName,
                        pm.getApplicationIcon(info),
                        selected));
            }
            entries.sort(Comparator.comparing(a -> a.label.toLowerCase()));

            if (!initialized) SelectionStore.save(this, selectedPackages);
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                adapter.submit(entries);
                updateStatus();
            });
        });
    }

    private void requestStart() {
        if (selectedPackages.isEmpty()) {
            Toast.makeText(this, "Выберите хотя бы одно приложение", Toast.LENGTH_SHORT).show();
            return;
        }
        SelectionStore.save(this, selectedPackages);
        Intent permission = VpnService.prepare(this);
        if (permission != null) {
            startActivityForResult(permission, VPN_REQUEST);
        } else {
            startProxyService();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST && resultCode == Activity.RESULT_OK) {
            startProxyService();
        }
    }

    private void startProxyService() {
        Intent intent = new Intent(this, ProxyVpnService.class);
        intent.setAction(ProxyVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
        status.postDelayed(this::updateStatus, 500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        statusHandler.removeCallbacks(statusPoll);
        statusHandler.post(statusPoll);
    }

    @Override
    protected void onPause() {
        statusHandler.removeCallbacks(statusPoll);
        super.onPause();
    }

    private void updateStatus() {
        boolean running = ProxyStateStore.isRunning(this);
        status.setText(running ? "VPN включён — остановите для изменения списка" : "VPN выключен");
        adapter.setLocked(running);
        startButton.setEnabled(!running && !selectedPackages.isEmpty());
        stopButton.setEnabled(running);
    }

    private void updateButtons() {
        updateStatus();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
