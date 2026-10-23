package com.boxzero.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.boxzero.app.R;
import com.boxzero.app.core.HistoryStore;
import com.boxzero.app.core.Site;
import com.boxzero.app.core.SpiderLoader;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/** History cards with playback progress, AVBox style. */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {

    public interface Listener {
        void onClick(HistoryStore.Record record);
    }

    private final List<HistoryStore.Record> items = new ArrayList<>();
    private final Listener listener;

    public HistoryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<HistoryStore.Record> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        HistoryStore.Record r = items.get(position);
        h.name.setText(r.vodName == null ? "" : r.vodName);
        h.source.setText(siteName(r.siteKey));
        String ep = r.epName == null ? "" : r.epName;
        h.lastWatch.setText("上次看到" + (ep.isEmpty() ? "正片" : ep));
        if (r.duration > 0) {
            h.progressRow.setVisibility(View.VISIBLE);
            int pct = (int) Math.min(100, r.position * 100 / r.duration);
            h.progress.setProgress(pct);
            h.percent.setText("已看" + pct + "%");
        } else {
            h.progressRow.setVisibility(View.GONE);
        }
        Glide.with(h.pic).load(r.vodPic).centerCrop().into(h.pic);
        h.itemView.setOnClickListener(v -> listener.onClick(r));
    }

    private static String siteName(String siteKey) {
        try {
            Site s = SpiderLoader.get().getConfig().find(siteKey);
            if (s != null && s.key.equals(siteKey)) return s.name;
        } catch (Exception ignored) {
        }
        return siteKey;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView pic;
        TextView name, source, lastWatch, percent;
        ProgressBar progress;
        LinearLayout progressRow;

        Holder(View itemView) {
            super(itemView);
            pic = itemView.findViewById(R.id.pic);
            name = itemView.findViewById(R.id.name);
            source = itemView.findViewById(R.id.source);
            lastWatch = itemView.findViewById(R.id.lastWatch);
            percent = itemView.findViewById(R.id.percent);
            progress = itemView.findViewById(R.id.progress);
            progressRow = itemView.findViewById(R.id.progressRow);
        }
    }
}
