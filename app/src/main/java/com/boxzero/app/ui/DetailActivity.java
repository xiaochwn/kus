package com.boxzero.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

/** Detail page in AVBox style: backdrop, big play button, source cards, episodes. */
public class DetailActivity extends AppCompatActivity {

private ImageView backdrop;
private TextView titleView;
private TextView metaView;
private TextView tagsView;
private TextView contentView;
private TextView btnExpand;
private RecyclerView srcRow;
private GridLayout epGrid;
private TextView btnReverse;
private ImageButton btnStar;

private SrcCardAdapter srcAdapter;

private final ExecutorService io = Executors.newSingleThreadExecutor();
private final Handler main = new Handler(Looper.getMainLooper());
private final Gson gson = new Gson();

private Site site;
private String siteKey;
private String vodId;
private Vod vod;
private List<Vod.Src> sources = new ArrayList<>();
private int srcIndex = 0;
private boolean reversed = false;
private boolean expanded = false;

@Override
protected void onCreate(Bundle savedInstanceState) {
super.onCreate(savedInstanceState);
setContentView(R.layout.activity_detail);

backdrop = findViewById(R.id.backdrop);
titleView = findViewById(R.id.title);
metaView = findViewById(R.id.meta);
tagsView = findViewById(R.id.tags);
contentView = findViewById(R.id.content);
btnExpand = findViewById(R.id.btnExpand);
srcRow = findViewById(R.id.srcRow);
epGrid = findViewById(R.id.epGrid);
btnReverse = findViewById(R.id.btnReverse);
btnStar = findViewById(R.id.btnStar);

siteKey = getIntent().getStringExtra("siteKey");
vodId = getIntent().getStringExtra("vodId");
site = SpiderLoader.get().getConfig().find(siteKey);
if (site == null) {
finish();
return;
}

srcRow.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
srcAdapter = new SrcCardAdapter(idx -> {
srcIndex = idx;
srcAdapter.setSelected(idx);
renderEpisodes();
});
srcRow.setAdapter(srcAdapter);

findViewById(R.id.btnBack).setOnClickListener(v -> finish());
findViewById(R.id.btnPlay).setOnClickListener(v -> playDefault());
btnStar.setOnClickListener(v -> toggleFav());
findViewById(R.id.btnMusic).setOnClickListener(v ->
        Toast.makeText(this, "暂无主题曲资源", Toast.LENGTH_SHORT).show());
findViewById(R.id.btnCast).setOnClickListener(v ->
        Toast.makeText(this, "投屏功能即将上线", Toast.LENGTH_SHORT).show());
findViewById(R.id.btnFollow).setOnClickListener(v -> toggleFav());
findViewById(R.id.btnAll).setOnClickListener(v -> showAllEpisodes());
TextView srcFrom = findViewById(R.id.srcFrom);
srcFrom.setText("来源： " + (site.name != null && !site.name.isEmpty() ? site.name : siteKey));
btnExpand.setOnClickListener(v -> {
expanded =!expanded;
contentView.setMaxLines(expanded? Integer.MAX_VALUE: 3);
btnExpand.setText(expanded? "收起 ▴": "展开 ▾");
});
btnReverse.setOnClickListener(v -> {
reversed =!reversed;
btnReverse.setText(reversed? "↑ 正序": "↓ 倒序");
renderEpisodes();
});

loadDetail(vodId);
}

private void loadDetail(String vodId) {
io.execute(() -> {
try {
Spider spider = SpiderLoader.get().spider(site);
String json = spider.detailContent(Collections.singletonList(vodId));
JSONObject o = new JSONObject(json);
JSONArray arr = o.optJSONArray("list");
if (arr!= null && arr.length() > 0) {
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
titleView.setText(vod.vodName == null? "": vod.vodName);

String meta = join(" · ", vod.vodYear, vod.vodArea);
metaView.setText(meta);
metaView.setVisibility(meta.isEmpty()? View.GONE: View.VISIBLE);

String tags = vod.vodClass == null? "": vod.vodClass.replace(",", " · ").trim();
tagsView.setText(tags);
tagsView.setVisibility(tags.isEmpty()? View.GONE: View.VISIBLE);

String text = vod.vodContent == null? "": vod.vodContent.replaceAll("<.*?>", "").trim();
contentView.setText(text.isEmpty()? "暂无简介": text);

Glide.with(this).load(vod.vodPic).centerCrop().into(backdrop);
refreshStar();

sources = vod.parseSources();
srcAdapter.setItems(sources);
srcIndex = 0;
renderEpisodes();

// Silent TMDB enrichment for missing poster/synopsis.
if (!Prefs.tmdbKey(this).isEmpty()) {
boolean picEmpty = vod.vodPic == null || vod.vodPic.isEmpty();
if (picEmpty || text.isEmpty()) tmdbEnrich();
}
}

private static String join(String sep, String... parts) {
StringBuilder sb = new StringBuilder();
for (String p: parts) {
if (p!= null &&!p.trim().isEmpty()) {
if (sb.length() > 0) sb.append(sep);
sb.append(p.trim());
}
}
return sb.toString();
}

private void refreshStar() {
boolean faved = FavStore.isFav(this, siteKey, vodId);
btnStar.setColorFilter(faved? 0xFF8B5E34: 0xFF8A8A8A);
}

private void toggleFav() {
if (vod == null) return;
FavStore.Item item = new FavStore.Item();
item.siteKey = siteKey;
item.vodId = vodId;
item.vodName = vod.vodName;
item.vodPic = vod.vodPic;
boolean nowFaved = FavStore.toggle(this, item);
refreshStar();
Toast.makeText(this, nowFaved? "已收藏": "已取消收藏", Toast.LENGTH_SHORT).show();
}

private void renderEpisodes() {
epGrid.removeAllViews();
if (sources.isEmpty()) return;
List<Vod.Ep> eps = new ArrayList<>(sources.get(srcIndex).eps);
if (reversed) Collections.reverse(eps);
for (Vod.Ep ep: eps) {
Button b = new Button(this);
b.setText(ep.name);
b.setAllCaps(false);
GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
lp.width = 0;
lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
lp.setMargins(4, 4, 4, 4);
b.setLayoutParams(lp);
b.setOnClickListener(v -> play(sources.get(srcIndex).name, ep));
epGrid.addView(b);
}
}

/** Big play button: resume last-watched episode if any, else the first one. */
private void showAllEpisodes() {
    if (sources.isEmpty()) return;
    List<Vod.Ep> eps = new ArrayList<>(sources.get(srcIndex).eps);
    if (reversed) Collections.reverse(eps);
    android.widget.ScrollView sv = new android.widget.ScrollView(this);
    GridLayout grid = new GridLayout(this);
    grid.setColumnCount(3);
    int pad = (int) (16 * getResources().getDisplayMetrics().density);
    grid.setPadding(pad, pad, pad, pad);
    for (Vod.Ep ep : eps) {
        Button b = new Button(this);
        b.setText(ep.name);
        b.setAllCaps(false);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(6, 6, 6, 6);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> play(sources.get(srcIndex).name, ep));
        grid.addView(b);
    }
    sv.addView(grid);
    new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选集")
            .setView(sv)
            .setPositiveButton("关闭", null)
            .show();
}
private void playDefault() {
if (sources.isEmpty()) return;
Vod.Src src = sources.get(srcIndex);
if (src.eps.isEmpty()) return;
// Resume?
for (HistoryStore.Record r: HistoryStore.list(this)) {
if (r.siteKey.equals(siteKey) && r.vodId.equals(vodId)) {
for (Vod.Ep ep: src.eps) {
if (ep.url.equals(r.epUrl)) {
play(src.name, ep);
return;
}
}
break;
}
}
play(src.name, src.eps.get(0));
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
PlayerLauncher.play(this, site, flag, ep.url, vod.vodName + " " + ep.name,
siteKey, vodId, ep.url);
}

// ---------- TMDB scraping (silent) ----------

private void tmdbEnrich() {
String key = Prefs.tmdbKey(this);
if (key.isEmpty() || vod == null) return;
io.execute(() -> {
try {
String query = cleanTitle(vod.vodName);
if (query.isEmpty()) return;
String year = extractYear(vod.vodName + " " + vod.vodRemarks);
List<Tmdb.Result> results = Tmdb.search(key, query, year);
if (results.isEmpty() &&!year.isEmpty()) {
results = Tmdb.search(key, query, "");
}
if (results.isEmpty()) return;
Tmdb.Result best = bestMatch(results, query);
if (best == null) return;
Tmdb.Result finalBest = best;
main.post(() -> applyTmdb(finalBest));
} catch (Exception ignored) {
}
});
}

private static Tmdb.Result bestMatch(List<Tmdb.Result> results, String query) {
String q = query.toLowerCase();
for (Tmdb.Result r: results) {
if (r.title.equalsIgnoreCase(query) || r.originalTitle.equalsIgnoreCase(query)) return r;
}
for (Tmdb.Result r: results) {
String t = r.title.toLowerCase();
if (!t.isEmpty() && (t.contains(q) || q.contains(t))) return r;
}
for (Tmdb.Result r: results) {
if (!r.posterPath.isEmpty() &&!r.overview.isEmpty()) return r;
}
return results.isEmpty()? null: results.get(0);
}

private void applyTmdb(Tmdb.Result r) {
if (vod == null) return;
boolean picEmpty = vod.vodPic == null || vod.vodPic.isEmpty();
String contentText = vod.vodContent == null? "": vod.vodContent.replaceAll("<.*?>", "").trim();
String posterUrl = Tmdb.posterUrl(r.posterPath);
if (picEmpty &&!posterUrl.isEmpty()) {
vod.vodPic = posterUrl;
Glide.with(this).load(posterUrl).centerCrop().into(backdrop);
}
if (contentText.isEmpty() &&!r.overview.isEmpty()) {
vod.vodContent = r.overview;
contentView.setText(r.overview);
}
if (r.voteAverage > 0) {
String score = "★ " + String.format(java.util.Locale.US, "%.1f", r.voteAverage);
String cur = metaView.getText().toString();
if (!cur.contains("★")) {
metaView.setText(cur.isEmpty()? score: cur + " · " + score);
metaView.setVisibility(View.VISIBLE);
}
}
}

private static String cleanTitle(String name) {
if (name == null) return "";
return name.replaceAll("[(\\[（]*$", "").trim();
}

private static String extractYear(String text) {
if (text == null) return "";
java.util.regex.Matcher m = java.util.regex.Pattern.compile("(19|20)\\d{2}").matcher(text);
return m.find()? m.group(): "";
}

@Override
protected void onDestroy() {
super.onDestroy();
io.shutdownNow();
}
}
