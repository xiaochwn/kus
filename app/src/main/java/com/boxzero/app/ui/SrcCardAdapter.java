package com.boxzero.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.boxzero.app.R;
import com.boxzero.app.core.Vod;

import java.util.ArrayList;
import java.util.List;

/** Source (线路) cards with episode counts; selected card gets a brown border. */
public class SrcCardAdapter extends RecyclerView.Adapter<SrcCardAdapter.Holder> {

    public interface Listener {
        void onClick(int index);
    }

    private final List<Vod.Src> items = new ArrayList<>();
    private final Listener listener;
    private int selected = 0;

    public SrcCardAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Vod.Src> list) {
        items.clear();
        if (list != null) items.addAll(list);
        selected = 0;
        notifyDataSetChanged();
    }

    public void setSelected(int index) {
        selected = index;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_src_card, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Vod.Src src = items.get(position);
        h.name.setText(src.name);
        h.count.setText("共 " + src.eps.size() + " 个视频");
        h.itemView.setBackgroundResource(position == selected
                ? R.drawable.src_card_selected : R.drawable.src_card_bg);
        h.itemView.setOnClickListener(v -> listener.onClick(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView name, count;

        Holder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.srcName);
            count = itemView.findViewById(R.id.srcCount);
        }
    }
}
