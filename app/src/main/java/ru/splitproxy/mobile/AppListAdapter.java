package ru.splitproxy.mobile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.Holder> {
    public interface SelectionListener {
        void onSelectionChanged(AppEntry app, boolean selected);
    }

    private final List<AppEntry> all = new ArrayList<>();
    private final List<AppEntry> visible = new ArrayList<>();
    private final SelectionListener listener;
    private boolean locked;

    public AppListAdapter(SelectionListener listener) {
        this.listener = listener;
    }

    public void submit(List<AppEntry> entries) {
        all.clear();
        all.addAll(entries);
        filter("");
    }

    public void setLocked(boolean locked) {
        if (this.locked == locked) return;
        this.locked = locked;
        notifyDataSetChanged();
    }

    public void filter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (AppEntry app : all) {
            if (q.isEmpty()
                    || app.label.toLowerCase(Locale.ROOT).contains(q)
                    || app.packageName.toLowerCase(Locale.ROOT).contains(q)) {
                visible.add(app);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        AppEntry app = visible.get(position);
        holder.icon.setImageDrawable(app.icon);
        holder.name.setText(app.label);
        holder.pkg.setText(app.packageName);
        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(app.selected);
        holder.check.setEnabled(!locked);
        holder.itemView.setEnabled(!locked);
        holder.itemView.setAlpha(locked ? 0.65f : 1.0f);
        holder.itemView.setOnClickListener(v -> {
            if (!locked) holder.check.setChecked(!holder.check.isChecked());
        });
        holder.check.setOnCheckedChangeListener((button, checked) -> {
            app.selected = checked;
            listener.onSelectionChanged(app, checked);
        });
    }

    @Override
    public int getItemCount() {
        return visible.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView pkg;
        final CheckBox check;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.appIcon);
            name = itemView.findViewById(R.id.appName);
            pkg = itemView.findViewById(R.id.appPackage);
            check = itemView.findViewById(R.id.appCheck);
        }
    }
}
