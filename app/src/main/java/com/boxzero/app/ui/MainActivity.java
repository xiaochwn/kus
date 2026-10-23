package com.boxzero.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static class Clazz {
        String id = "";
        String name = "";
    }

    private Spinner siteSpinner;
    private EditText searchInput;
    private RecyclerView grid;
    private ProgressBar loading;
    private VodAdapter adapter;
    private LinearLayout tabRow;
    private HorizontalScrollView tabScroll;
    private LinearLayout pageRow;
    private TextView pageInfo;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private BoxConfig config;
    private Site current;
    private boolean spinnerReady;
    private String lastUrl = "";

    private final List<Clazz> classes = new ArrayList<>();
    private String currentTid = "";
    private int currentPage = 1;
    private int pageCount = 1;
    private boolean inCategory = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        siteSpinner = findViewById(R.id.siteSpinner);
        searchInput = findViewById(R.id.searchInput);
        grid = findViewById(R.id.grid);
        loading = findViewById(R.id.loading);
        tabRow = findViewById(R.id.tabRow);
        tabScroll = findViewById(R.id.tabScroll);
        pageRow = findViewById(R.id.pageRow);
        pageInfo = findViewById(R.id.pageInfo);

        grid.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new VodAdapter(vod -> {
            Intent it = new Intent(this, DetailActivity.class);
            it.putExtra("siteKey", current.key);
            it.putExtra("vodId", vod.vodId);
            startActivity(it);
        });
        grid.setAdapter(adapter);

        findViewById(R.id.btnSearch).setOnClickListener(v -> doSearch());
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnHistory).setOnClickListener(v ->
                openRecords(RecordsActivity.MODE_HISTORY));
        findViewById(R.id.btnFav).setOnClickListener(v ->
                openRecords(RecordsActivity.MODE_FAV));
        findViewById(R.id.btnLive).setOnClickListener(v ->
                startActivity(new Intent(this, LiveActivity.class)));
        findViewById(R.id.btnPrev).setOnClickListener(v -> {
            if (inCategory && currentPage > 1) loadCategory(currentTid, currentPage - 1);
        });
        findViewById(R.id.btnNext).setOnClickListener(v -> {
            if (inCategory && currentPage < pageCount) loadCategory(currentTid, currentPage + 1);
        });

        loadConfig();
    }

    private void openRecords(String mode) {
        Intent it = new Intent(this, RecordsActivity.class);
        it.putExtra(RecordsActivity.EXTRA_MODE, mode);
        startActivity(it);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String url = Prefs.configUrl(this);
        if (config == null || !url.equals(lastUrl)) {
            loadConfig();
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
                    // Built-in demo source: fully offline, no network needed.
                    cfg = BoxConfig.loadFromFile(new java.io.File(getFilesDir(), "demo/config.json"));
                } else {
                    cfg = BoxConfig.load(url);
                }
                SpiderLoader.get().setConfig(cfg);
                main.post(() -> {
                    setLoading(false);
                    config = cfg;
                    setupSites();
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

    private void setupSites() {
        if (config.sites.isEmpty()) {
            Toast.makeText(this, "接口里没有可用站点", Toast.LENGTH_LONG).show();
            return;
        }
        spinnerReady = false;
        ArrayAdapter<Site> sa = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, config.sites);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        siteSpinner.setAdapter(sa);
        String saved = Prefs.siteKey(this);
        int sel = 0;
        for (int i = 0; i < config.sites.size(); i++) {
            if (config.sites.get(i).key.equals(saved)) {
                sel = i;
                break;
            }
        }
        siteSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!spinnerReady) spinnerReady = true;
                current = config.sites.get(position);
                Prefs.siteKey(MainActivity.this, current.key);
                inCategory = false;
                loadHome();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        siteSpinner.setSelection(sel);
    }

    /** Home recommendations + category tabs. */
    private void loadHome() {
        if (current == null) return;
        setLoading(true);
        pageRow.setVisibility(View.GONE);
        io.execute(() -> {
            List<Vod> list = new ArrayList<>();
            List<Clazz> cls = new ArrayList<>();
            boolean usedCategory = false;
            String firstTid = "";
            int pc = 1;
            try {
                Spider spider = SpiderLoader.get().spider(current);
                list = parseList(spider.homeVideoContent());
                try {
                    JSONObject home = new JSONObject(spider.homeContent(true));
                    JSONArray arr = home.optJSONArray("class");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject c = arr.getJSONObject(i);
                            Clazz cl = new Clazz();
                            cl.id = c.optString("type_id");
                            cl.name = c.optString("type_name");
                            if (!cl.id.isEmpty()) cls.add(cl);
                        }
                    }
                } catch (Exception ignored) {
                }
                if (list.isEmpty() && !cls.isEmpty()) {
                    // Fall back to first category's first page.
                    String r = spider.categoryContent(cls.get(0).id, "1", true, new HashMap<>());
                    list = parseList(r);
                    pc = parsePageCount(r);
                    firstTid = cls.get(0).id;
                    usedCategory = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            List<Vod> result = list;
            List<Clazz> classesResult = cls;
            boolean inCat = usedCategory;
            String tid = firstTid;
            int pageCountResult = pc;
            main.post(() -> {
                setLoading(false);
                classes.clear();
                classes.addAll(classesResult);
                inCategory = inCat;
                currentTid = tid;
                currentPage = 1;
                pageCount = pageCountResult;
                buildTabs();
                adapter.setItems(result);
                if (inCategory) showPageRow();
            });
        });
    }

    private void buildTabs() {
        tabRow.removeAllViews();
        if (classes.isEmpty()) {
            tabScroll.setVisibility(View.GONE);
            return;
        }
        tabScroll.setVisibility(View.VISIBLE);
        addTab("推荐", "", !inCategory);
        for (Clazz c : classes) {
            addTab(c.name, c.id, inCategory && c.id.equals(currentTid));
        }
    }

    private void addTab(String name, String tid, boolean selected) {
        Button b = new Button(this);
        b.setText(name);
        b.setAllCaps(false);
        b.setAlpha(selected ? 1.0f : 0.6f);
        b.setOnClickListener(v -> {
            if (tid.isEmpty()) {
                inCategory = false;
                loadHome();
            } else {
                loadCategory(tid, 1);
            }
        });
        tabRow.addView(b);
    }

    private void loadCategory(String tid, int page) {
        if (current == null) return;
        setLoading(true);
        io.execute(() -> {
            List<Vod> list = new ArrayList<>();
            int pc = 1;
            try {
                Spider spider = SpiderLoader.get().spider(current);
                String r = spider.categoryContent(tid, String.valueOf(page), true, new HashMap<>());
                list = parseList(r);
                pc = parsePageCount(r);
            } catch (Exception e) {
                e.printStackTrace();
            }
            List<Vod> result = list;
            int pageCountResult = pc;
            main.post(() -> {
                setLoading(false);
                inCategory = true;
                currentTid = tid;
                currentPage = page;
                pageCount = pageCountResult;
                buildTabs();
                adapter.setItems(result);
                showPageRow();
                grid.scrollToPosition(0);
            });
        });
    }

    private void showPageRow() {
        pageRow.setVisibility(View.VISIBLE);
        pageInfo.setText("第 " + currentPage + " / " + pageCount + " 页");
    }

    private void doSearch() {
        String key = searchInput.getText().toString().trim();
        if (key.isEmpty() || current == null) return;
        setLoading(true);
        pageRow.setVisibility(View.GONE);
        io.execute(() -> {
            List<Vod> list = new ArrayList<>();
            try {
                Spider spider = SpiderLoader.get().spider(current);
                list = parseList(spider.searchContent(key, true, "1"));
            } catch (Exception e) {
                e.printStackTrace();
            }
            List<Vod> result = list;
            main.post(() -> {
                setLoading(false);
                inCategory = false;
                buildTabs();
                adapter.setItems(result);
                if (result.isEmpty()) Toast.makeText(this, "没有搜到", Toast.LENGTH_SHORT).show();
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

    private int parsePageCount(String json) {
        try {
            return Math.max(1, new JSONObject(json).optInt("pagecount", 1));
        } catch (Exception e) {
            return 1;
        }
    }

    private void setLoading(boolean show) {
        loading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
