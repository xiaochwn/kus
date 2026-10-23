package com.boxzero.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.boxzero.app.R;
import com.boxzero.app.core.FavStore;
import com.boxzero.app.core.HistoryStore;
import com.boxzero.app.core.PlayerLauncher;
import com.boxzero.app.core.Site;
import com.boxzero.app.core.SpiderLoader;
import com.boxzero.app.core.Vod;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows watch history (progress cards) or favorites (poster grid).
 * <p>
 * History tap: re-resolves the episode and plays. Favorite tap: opens detail.
 */
public class RecordsActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_HISTORY = "history";
    public static final String MODE_FAV = "fav";

    private RecyclerView grid;
    private HistoryAdapter historyAdapter;
    private VodAdapter favAdapter;
    private boolean isHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);

        String mode = getIntent().getStringExtra(EXTRA_MODE);
        isHistory = MODE_HISTORY.equals(mode);
        BottomNav.setup(this, isHistory ? BottomNav.TAB_HISTORY : BottomNav.TAB_FAV);

        TextView title = findViewById(R.id.title);
        title.setText(isHistory ? "历史记录" : "我的收藏");
        setTitle(isHistory ? "历史记录" : "我的收藏");

        grid = findViewById(R.id.grid);
        if (isHistory) {
            grid.setLayoutManager(new LinearLayoutManager(this));
            historyAdapter = new HistoryAdapter(rec -> {
                Site site = SpiderLoader.get().getConfig().find(rec.siteKey);
                if (site == null) {
                    Toast.makeText(this, "站点已失效", Toast.LENGTH_SHORT).show();
                    return;
                }
                HistoryStore.add(this, rec); // bump to front
                PlayerLauncher.play(this, site, rec.flag, rec.epUrl,
                        rec.vodName + " " + rec.epName, rec.siteKey, rec.vodId, rec.epUrl);
            });
            grid.setAdapter(historyAdapter);
        } else {
            grid.setLayoutManager(new GridLayoutManager(this, 3));
            favAdapter = new VodAdapter(vod -> {
                FavStore.Item item = findFav(vod.vodId);
                if (item == null) return;
                Intent it = new Intent(this, DetailActivity.class);
                it.putExtra("siteKey", item.siteKey);
                it.putExtra("vodId", item.vodId);
                startActivity(it);
            });
            grid.setAdapter(favAdapter);
        }
        refresh();

        Button clear = findViewById(R.id.btnClear);
        clear.setOnClickListener(v -> {
            if (isHistory) HistoryStore.clear(this);
            else FavStore.clear(this);
            refresh();
            Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        BottomNav.setup(this, isHistory ? BottomNav.TAB_HISTORY : BottomNav.TAB_FAV);
    }

    private void refresh() {
        if (isHistory) {
            historyAdapter.setItems(HistoryStore.list(this));
        } else {
            List<Vod> vods = new ArrayList<>();
            for (FavStore.Item it : FavStore.list(this)) {
                Vod v = new Vod();
                v.vodId = it.vodId;
                v.vodName = it.vodName;
                v.vodPic = it.vodPic;
                vods.add(v);
            }
            favAdapter.setItems(vods);
        }
    }

    private FavStore.Item findFav(String vodId) {
        for (FavStore.Item it : FavStore.list(this)) {
            if (it.vodId.equals(vodId)) return it;
        }
        return null;
    }
}
