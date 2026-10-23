package com.boxzero.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

import com.boxzero.app.R;
import com.boxzero.app.core.BoxConfig;
import com.boxzero.app.core.Prefs;
import com.boxzero.app.core.Site;
import com.boxzero.app.core.SpiderLoader;
import com.boxzero.app.core.Vod;
import com.github.catvod.crawler.Spider;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home screen: banner carousel, category pills, recommendation rail,
 * frosted bottom navigation. Matches the reference design.
 */
public class MainActivity extends AppCompatActivity {

    private TextView siteName;
    private RecyclerView banner;
    private TextView bannerTitle;
    private TextView bannerScore;
    private LinearLayout dots;
    private RecyclerView pillRow;
    private RecyclerView posterRow;
    private ProgressBar loading;

    private BannerAdapter bannerAdapter;
    private PillAdapter pillAdapter;
    private PosterAdapter posterAdapter;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private BoxConfig config;
    private Site current;
    private String lastUrl = "";

    private final Handler autoScroll = new Handler(Looper.getMainLooper());
    private int bannerPos = 0;
    private final Runnable autoScrollTask = new Runnable() {
        @Override
        public void run() {
            int count = bannerAdapter.getItemCount();
            if (count > 1) {
                bannerPos = (bannerPos + 1) % count;
                banner.smoothScrollToPosition(bannerPos);
            }
            autoScroll.postDelayed(this, 4500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Content behind a transparent status bar (dark top, so light icons).
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(R.layout.activity_main);

        siteName = findViewById(R.id.siteName);
        banner = findViewById(R.id.banner);
        { // AVBox hero height: 58% of screen height, clamped [360,560]dp
            android.view.View wrap = findViewById(R.id.bannerWrap);
            float sh = getResources().getDisplayMetrics().heightPixels
                    / getResources().getDisplayMetrics().density;
            float h = Math.max(360f, Math.min(560f, sh * 0.58f));
            android.view.ViewGroup.LayoutParams lp = wrap.getLayoutParams();
            lp.height = (int) (h * getResources().getDisplayMetrics().density);
            wrap.setLayoutParams(lp);
        }
        bannerTitle = findViewById(R.id.bannerTitle);
        bannerScore = findViewById(R.id.bannerScore);
        dots = findViewById(R.id.dots);
        pillRow = findViewById(R.id.pillRow);
        posterRow = findViewById(R.id.posterRow);
        loading = findViewById(R.id.loading);

        // Banner carousel with paging snap.
        LinearLayoutManager bannerLm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        banner.setLayoutManager(bannerLm);
        bannerAdapter = new BannerAdapter(vod -> openDetail(vod));
        banner.setAdapter(bannerAdapter);
        SnapHelper snap = new PagerSnapHelper();
        snap.attachToRecyclerView(banner);
        banner.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View snapView = snap.findSnapView(bannerLm);
                    if (snapView != null) {
                        int pos = bannerLm.getPosition(snapView);
                        if (pos != bannerPos) {
                            bannerPos = pos;
                            updateBannerUi();
                            restartAutoScroll();
                        }
                    }
                }
            }
        });

        pillRow.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        pillAdapter = new PillAdapter(pill -> {
            Intent it = new Intent(this, CategoryActivity.class);
            it.putExtra("siteKey", current.key);
            it.putExtra("tid", pill.id);
            it.putExtra("title", pill.name);
            startActivity(it);
        });
        pillRow.setAdapter(pillAdapter);

        posterRow.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        posterAdapter = new PosterAdapter(vod -> openDetail(vod));
        posterRow.setAdapter(posterAdapter);

        findViewById(R.id.sitePill).setOnClickListener(v -> showSiteDialog());
        findViewById(R.id.btnSearch).setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class)));
        findViewById(R.id.btnMenu).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        BottomNav.setup(this, BottomNav.TAB_HOME);
        loadConfig();
    }

    private void openDetail(Vod vod) {
        if (current == null) return;
        Intent it = new Intent(this, DetailActivity.class);
        it.putExtra("siteKey", current.key);
        it.putExtra("vodId", vod.vodId);
        startActivity(it);
    }

    private void showSiteDialog() {
        if (config == null || config.sites.isEmpty()) return;
        String[] names = new String[config.sites.size()];
        int checked = 0;
        for (int i = 0; i < config.sites.size(); i++) {
            names[i] = config.sites.get(i).name;
            if (config.sites.get(i) == current) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("切换源")
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    current = config.sites.get(which);
                    Prefs.siteKey(this, current.key);
                    siteName.setText(current.name + " \u00b7 \u9996\u9875");
                    d.dismiss();
                    loadHome();
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String url = Prefs.configUrl(this);
        if (config == null || !url.equals(lastUrl)) {
            loadConfig();
        }
        BottomNav.setup(this, BottomNav.TAB_HOME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoScroll.removeCallbacks(autoScrollTask);
    }

    private void restartAutoScroll() {
        autoScroll.removeCallbacks(autoScrollTask);
        autoScroll.postDelayed(autoScrollTask, 4500);
    }

    private void updateBannerUi() {
        int count = bannerAdapter.getItemCount();
        if (count == 0) {
            bannerTitle.setText("");
            bannerScore.setVisibility(View.GONE);
            dots.removeAllViews();
            return;
        }
        Vod vod = bannerAdapter.get(bannerPos % count);
        bannerTitle.setText(vod.vodName == null ? "" : vod.vodName);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+\\.\\d+")
                .matcher(vod.vodRemarks == null ? "" : vod.vodRemarks);
        if (m.find()) {
            bannerScore.setVisibility(View.VISIBLE);
            bannerScore.setText("评分 " + m.group());
        } else {
            bannerScore.setVisibility(View.GONE);
        }
        dots.removeAllViews();
        for (int i = 0; i < count; i++) {
            ImageView dot = new ImageView(this);
            dot.setImageResource(i == bannerPos % count ? R.drawable.dot_active : R.drawable.dot_inactive);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(6, 0, 6, 0);
            dots.addView(dot, lp);
        }
    }

    private void loadConfig() {
        String url = Prefs.configUrl(this);
        lastUrl = url;
        setLoading(true);
        io.execute(() -> {
            try {
                BoxConfig cfg;
                if (url.isEmpty()) {
                    cfg = BoxConfig.loadFromFile(new java.io.File(getFilesDir(), "demo/config.json"));
                } else {
                    cfg = BoxConfig.load(url);
                }
                SpiderLoader.get().setConfig(cfg);
                main.post(() -> {
                    setLoading(false);
                    config = cfg;
                    if (config.sites.isEmpty()) {
                        Toast.makeText(this, "接口里没有可用站点", Toast.LENGTH_LONG).show();
                        return;
                    }
                    String saved = Prefs.siteKey(this);
                    current = config.sites.get(0);
                    for (Site s : config.sites) {
                        if (s.key.equals(saved)) {
                            current = s;
                            break;
                        }
                    }
                    siteName.setText(current.name + " \u00b7 \u9996\u9875");
                    loadHome();
                });
            } catch (Exception e) {
                e.printStackTrace();
                main.post(() -> {
                    setLoading(false);
                    Toast.makeText(this, "接口加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadHome() {
        if (current == null) return;
        setLoading(true);
        io.execute(() -> {
            List<Vod> home = new ArrayList<>();
            List<PillAdapter.Pill> pills = new ArrayList<>();
            try {
                Spider spider = SpiderLoader.get().spider(current);
                home = parseList(spider.homeVideoContent());
                try {
                    JSONObject hc = new JSONObject(spider.homeContent(true));
                    JSONArray arr = hc.optJSONArray("class");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject c = arr.getJSONObject(i);
                            String id = c.optString("type_id");
                            String name = c.optString("type_name");
                            if (!id.isEmpty() && !name.isEmpty()) {
                                pills.add(new PillAdapter.Pill(id, name));
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            List<Vod> homeResult = home;
            List<PillAdapter.Pill> pillsResult = pills;
            main.post(() -> {
                setLoading(false);
                // Banner: first 5 with pictures.
                List<Vod> bannerItems = new ArrayList<>();
                for (Vod v : homeResult) {
                    if (v.vodPic != null && !v.vodPic.isEmpty()) {
                        bannerItems.add(v);
                        if (bannerItems.size() >= 5) break;
                    }
                }
                bannerPos = 0;
                bannerAdapter.setItems(bannerItems);
                banner.scrollToPosition(0);
                updateBannerUi();
                restartAutoScroll();
                pillAdapter.setItems(pillsResult);
                posterAdapter.setItems(homeResult);
            });
        });
    }

    private List<Vod> parseList(String json) {
        try {
            JSONObject o = new JSONObject(json);
            JSONArray arr = o.optJSONArray("list");
            if (arr == null) return new ArrayList<>();
            return gson.fromJson(arr.toString(), new TypeToken<List<Vod>>() {
            }.getType());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void setLoading(boolean show) {
        loading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoScroll.removeCallbacks(autoScrollTask);
        io.shutdownNow();
    }
}
