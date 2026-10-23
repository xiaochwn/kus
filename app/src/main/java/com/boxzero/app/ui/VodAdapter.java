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

public class VodAdapter extends RecyclerView.Adapter<VodAdapter.Holder> {

    public interface Listener {
        void onClick(Vod vod);
    }

    private final List<Vod> items = new ArrayList<>();
    private final Listener listener;

    public VodAdapter(Listener listener) {
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
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vod, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Vod vod = items.get(position);
        h.name.setText(vod.vodName == null ? "" : vod.vodName);
        h.remarks.setText(vod.vodRemarks == null ? "" : vod.vodRemarks);
        Glide.with(h.pic).load(vod.vodPic).centerCrop().into(h.pic);
        h.itemView.setOnClickListener(v -> listener.onClick(vod));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView pic;
        TextView name, remarks;

        Holder(View itemView) {
            super(itemView);
            pic = itemView.findViewById(R.id.pic);
            name = itemView.findViewById(R.id.name);
            remarks = itemView.findViewById(R.id.remarks);
        }
    }
}
