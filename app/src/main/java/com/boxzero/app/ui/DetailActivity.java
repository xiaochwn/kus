package com.boxzero.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.boxzero.app.R;
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
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DetailActivity extends AppCompatActivity {

    private ImageView poster;
    private TextView title, remarks, content;
    private LinearLayout srcRow;
    private GridLayout epGrid;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private Site site;
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
        srcRow = findViewById(R.id.srcRow);
        epGrid = findViewById(R.id.epGrid);

        String siteKey = getIntent().getStringExtra("siteKey");
        String vodId = getIntent().getStringExtra("vodId");
        site = SpiderLoader.get().getConfig().find(siteKey);
        if (site == null) {
            finish();
            return;
        }
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
        Toast.makeText(this, "解析中…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                Spider spider = SpiderLoader.get().spider(site);
                String json = spider.playerContent(flag, ep.url, new ArrayList<>());
                JSONObject o = new JSONObject(json);
                int parse = o.optInt("parse", 0);
                String url = o.optString("url", "");
                String header = o.optJSONObject("header") != null ? o.optJSONObject("header").toString() : "{}";
                if (parse == 1) {
                    String resolved = tryParse(url);
                    if (resolved != null) url = resolved;
                }
                if (url.isEmpty()) throw new Exception("empty play url");
                Intent it = new Intent(this, PlayerActivity.class);
                it.putExtra("url", url);
                it.putExtra("headers", header);
                it.putExtra("title", vod.vodName + " " + ep.name);
                startActivity(it);
            } catch (Exception e) {
                e.printStackTrace();
                main.post(() -> Toast.makeText(this, "播放解析失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    /**
     * Best-effort handling for parse==1 play URLs using the config's "parses".
     * type 0: concatenate parse.url + playUrl; type 1: fetch JSON, take "url".
     * (Full WebView sniffing is a TODO.)
     */
    private String tryParse(String playUrl) {
        try {
            JSONArray arr = new JSONArray(SpiderLoader.get().getConfig().rawParses);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.getJSONObject(i);
                int type = p.optInt("type", -1);
                String base = p.optString("url", "");
                if (base.isEmpty()) continue;
                if (type == 0) return base + playUrl;
                if (type == 1) {
                    String body = httpGet(base + playUrl);
                    String u = new JSONObject(body).optString("url", "");
                    if (!u.isEmpty()) return u;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String httpGet(String url) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        Request req = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36").build();
        try (Response resp = client.newCall(req).execute()) {
            if (resp.body() == null) throw new Exception("HTTP " + resp.code());
            return resp.body().string();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
