package com.boxzero.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.boxzero.app.R;

import java.util.ArrayList;
import java.util.List;

/** Horizontal category pill buttons. */
public class PillAdapter extends RecyclerView.Adapter<PillAdapter.Holder> {

    public static class Pill {
        public final String id;
        public final String name;

        public Pill(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public interface Listener {
        void onClick(Pill pill);
    }

    private final List<Pill> items = new ArrayList<>();
    private final Listener listener;

    public PillAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Pill> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pill, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Pill p = items.get(position);
        h.pill.setText(p.name);
        h.itemView.setOnClickListener(v -> listener.onClick(p));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView pill;

        Holder(View itemView) {
            super(itemView);
            pill = itemView.findViewById(R.id.pill);
        }
    }
}
