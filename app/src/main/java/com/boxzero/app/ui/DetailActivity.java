package com.boxzero.app.ui;

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
import com.boxzero.app.core.FavStore;
import com.boxzero.app.core.HistoryStore;
import com.boxzero.app.core.PlayerLauncher;
import com.boxzero.app.core.Prefs;
import com.boxzero.app.core.Site;
import com.boxzero.app.core.SpiderLoader;
import com.boxzero.app.core.Tmdb;
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
        Button btnTmdb = findViewById(R.id.btnTmdb);
        if (!Prefs.tmdbKey(this).isEmpty()) {
            btnTmdb.setVisibility(View.VISIBLE);
            btnTmdb.setOnClickListener(v -> tmdbEnrich(true));
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
        refreshFavButton();

        // Auto-fill missing poster/synopsis from TMDB when a key is set.
        if (!Prefs.tmdbKey(this).isEmpty()) {
            boolean picEmpty = vod.vodPic == null || vod.vodPic.isEmpty();
            String ct = vod.vodContent == null ? "" : vod.vodContent.replaceAll("<.*?>", "").trim();
            if (picEmpty || ct.isEmpty()) tmdbEnrich(false);
        }

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

    // ---------- TMDB scraping (silent) ----------
    //
    // Runs quietly in the background after the detail page loads: if the
    // poster or synopsis is missing and a TMDB key is set, the best-matching
    // TMDB entry fills them in. No toasts, no dialogs — failures are ignored.

    /** Auto-enrich when poster or synopsis is missing; manual via the TMDB button. */
    private void tmdbEnrich(boolean force) {
        String key = Prefs.tmdbKey(this);
        if (key.isEmpty() || vod == null) return;
        io.execute(() -> {
            try {
                String query = cleanTitle(vod.vodName);
                if (query.isEmpty()) return;
                String year = extractYear(vod.vodName + " " + vod.vodRemarks);
                List<Tmdb.Result> results = Tmdb.search(key, query, year);
                if (results.isEmpty() && !year.isEmpty()) {
                    results = Tmdb.search(key, query, "");
                }
                if (results.isEmpty()) return;
                Tmdb.Result best = bestMatch(results, query);
                if (best == null) return;
                Tmdb.Result finalBest = best;
                main.post(() -> applyTmdb(finalBest, force));
            } catch (Exception ignored) {
                // Silent: leave the page as-is.
            }
        });
    }

    /** Picks the most likely correct entry without asking the user. */
    private static Tmdb.Result bestMatch(List<Tmdb.Result> results, String query) {
        String q = query.toLowerCase();
        for (Tmdb.Result r : results) {
            if (r.title.equalsIgnoreCase(query) || r.originalTitle.equalsIgnoreCase(query)) return r;
        }
        for (Tmdb.Result r : results) {
            String t = r.title.toLowerCase();
            if (!t.isEmpty() && (t.contains(q) || q.contains(t))) return r;
        }
        for (Tmdb.Result r : results) {
            if (!r.posterPath.isEmpty() && !r.overview.isEmpty()) return r;
        }
        return results.isEmpty() ? null : results.get(0);
    }

    private void applyTmdb(Tmdb.Result r, boolean force) {
        if (vod == null) return;
        boolean picEmpty = vod.vodPic == null || vod.vodPic.isEmpty();
        String contentText = vod.vodContent == null ? "" : vod.vodContent.replaceAll("<.*?>", "").trim();
        String posterUrl = Tmdb.posterUrl(r.posterPath);
        if ((picEmpty || force) && !posterUrl.isEmpty()) {
            vod.vodPic = posterUrl;
            Glide.with(this).load(posterUrl).into(poster);
        }
        if ((contentEmpty(contentText) || force) && !r.overview.isEmpty()) {
            vod.vodContent = r.overview;
            content.setText(r.overview);
        }
        if (r.voteAverage > 0) {
            String score = "★ " + String.format(java.util.Locale.US, "%.1f", r.voteAverage);
            String cur = remarks.getText().toString();
            if (!cur.contains("★")) {
                remarks.setText(cur.isEmpty() ? score : cur + " · " + score);
            }
        }
    }

    private static boolean contentEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String cleanTitle(String name) {
        if (name == null) return "";
        // Strip trailing "(...)" / "[...]" decorations for better search hits.
        return name.replaceAll("[(\\[（【][^)\\]）】]*$", "").trim();
    }

    private static String extractYear(String text) {
        if (text == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(19|20)\\d{2}").matcher(text);
        return m.find() ? m.group() : "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
