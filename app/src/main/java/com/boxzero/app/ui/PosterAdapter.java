package com.boxzero.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.boxzero.app.R;
import com.boxzero.app.core.Vod;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Horizontal poster cards with optional score badge. */
public class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.Holder> {

    private static final Pattern SCORE = Pattern.compile("\\d+\\.\\d+");

    public interface Listener {
        void onClick(Vod vod);
    }

    private final List<Vod> items = new ArrayList<>();
    private final Listener listener;

    public PosterAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Vod> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_poster, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Vod vod = items.get(position);
        h.name.setText(vod.vodName == null ? "" : vod.vodName);
        String remarks = vod.vodRemarks == null ? "" : vod.vodRemarks;
        java.util.regex.Matcher m = SCORE.matcher(remarks);
        if (m.find()) {
            h.badge.setVisibility(View.VISIBLE);
            h.badge.setText(m.group());
            h.remarks.setText("");
        } else {
            h.badge.setVisibility(View.GONE);
            h.remarks.setText(remarks);
        }
        Glide.with(h.pic).load(vod.vodPic).centerCrop().into(h.pic);
        h.itemView.setOnClickListener(v -> listener.onClick(vod));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView pic;
        TextView name, remarks, badge;

        Holder(View itemView) {
            super(itemView);
            pic = itemView.findViewById(R.id.pic);
            name = itemView.findViewById(R.id.name);
            remarks = itemView.findViewById(R.id.remarks);
            badge = itemView.findViewById(R.id.badge);
        }
    }
}
