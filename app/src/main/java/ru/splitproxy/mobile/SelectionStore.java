package ru.splitproxy.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class SelectionStore {
    private static final String PREFS = "split_proxy_settings";
    private static final String KEY_PACKAGES = "selected_packages";
    private static final String KEY_INITIALIZED = "selection_initialized";

    private SelectionStore() {}

    public static Set<String> load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(KEY_PACKAGES, new HashSet<>()));
    }

    public static void save(Context context, Set<String> packages) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_PACKAGES, new HashSet<>(packages))
                .putBoolean(KEY_INITIALIZED, true)
                .apply();
    }

    public static boolean isInitialized(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_INITIALIZED, false);
    }
}
