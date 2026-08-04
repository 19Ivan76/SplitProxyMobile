package ru.splitproxy.mobile;

import android.graphics.drawable.Drawable;

public final class AppEntry {
    public final String label;
    public final String packageName;
    public final Drawable icon;
    public boolean selected;

    public AppEntry(String label, String packageName, Drawable icon, boolean selected) {
        this.label = label;
        this.packageName = packageName;
        this.icon = icon;
        this.selected = selected;
    }
}
