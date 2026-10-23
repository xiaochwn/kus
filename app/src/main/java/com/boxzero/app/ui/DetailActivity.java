package com.boxzero.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.boxzero.app.R;
import com.boxzero.app.core.FavStore;
import com.boxzero.app.core.HistoryStore;
import com.boxzero.app.core.PlayerLauncher;
import com.boxzero.app.core.Site;
import com.boxzero.app.core.SpiderLoader;
import com.boxzero.app.core.Vod;
import com.bumptech.glide.Glide;
import com.github.catvod.crawler.Spider;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetailActivity extends AppCompatActivity {

    private ImageView poster;
    private TextView title, remarks, content;
    private Button btnFav;
    private LinearLayout srcRow;
    private GridLayout epGrid;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private Site site;
    private String siteKey;
    private String vodId;
    private Vod vod;
    private List<Vod.Src> sources = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        poster = findViewById(R.id.poster);
        title = findViewById(R.id.title);
        remarks = findViewById(R.id.remarks);
        content = findViewById(R.id.content);
        btnFav = findViewById(R.id.btnFav);
        srcRow = findViewById(R.id.srcRow);
        epGrid = findViewById(R.id.epGrid);

        siteKey = getIntent().getStringExtra("siteKey");
        vodId = getIntent().getStringExtra("vodId");
        site = SpiderLoader.get().getConfig().find(siteKey);
        if (site == null) {
            finish();
            return;
        }

        btnFav.setOnClickListener(v -> toggleFav());
        loadDetail(vodId);
    }

    private void loadDetail(String vodId) {
        io.execute(() -> {
            try {
                Spider spider = SpiderLoader.get().spider(site);
                String json = spider.detailContent(Collections.singletonList(vodId));
                JSONObject o = new JSONObject(json);
                JSONArray arr = o.optJSONArray("list");
                if (arr != null && arr.length() > 0) {
                    vod = gson.fromJson(arr.getJSONObject(0).toString(), Vod.class);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            main.post(this::render);
        });
    }

    private void render() {
        if (vod == null) {
            Toast.makeText(this, "详情加载失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        title.setText(vod.vodName);
        remarks.setText(vod.vodRemarks == null ? "" : vod.vodRemarks);
        content.setText(vod.vodContent == null ? "" : vod.vodContent.replaceAll("<.*?>", ""));
        Glide.with(this).load(vod.vodPic).into(poster);
        refreshFavButton();

        sources = vod.parseSources();
        srcRow.removeAllViews();
        for (int i = 0; i < sources.size(); i++) {
            Vod.Src src = sources.get(i);
            Button b = new Button(this);
            b.setText(src.name);
            b.setAllCaps(false);
            int idx = i;
            b.setOnClickListener(v -> renderEpisodes(idx));
            srcRow.addView(b);
        }
        if (!sources.isEmpty()) renderEpisodes(0);
    }

    private void refreshFavButton() {
        boolean faved = FavStore.isFav(this, siteKey, vodId);
        btnFav.setText(faved ? "★ 已收藏" : "☆ 收藏");
    }

    private void toggleFav() {
        if (vod == null) return;
        FavStore.Item item = new FavStore.Item();
        item.siteKey = siteKey;
        item.vodId = vodId;
        item.vodName = vod.vodName;
        item.vodPic = vod.vodPic;
        boolean nowFaved = FavStore.toggle(this, item);
        refreshFavButton();
        Toast.makeText(this, nowFaved ? "已收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
    }

    private void renderEpisodes(int srcIndex) {
        epGrid.removeAllViews();
        Vod.Src src = sources.get(srcIndex);
        for (Vod.Ep ep : src.eps) {
            Button b = new Button(this);
            b.setText(ep.name);
            b.setAllCaps(false);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> play(src.name, ep));
            epGrid.addView(b);
        }
    }

    private void play(String flag, Vod.Ep ep) {
        HistoryStore.Record r = new HistoryStore.Record();
        r.siteKey = siteKey;
        r.vodId = vodId;
        r.vodName = vod.vodName;
        r.vodPic = vod.vodPic;
        r.flag = flag;
        r.epName = ep.name;
        r.epUrl = ep.url;
        HistoryStore.add(this, r);
        PlayerLauncher.play(this, site, flag, ep.url, vod.vodName + " " + ep.name);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
