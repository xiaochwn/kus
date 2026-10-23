package com.boxzero.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
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

    private Spinner siteSpinner;
    private EditText searchInput;
    private RecyclerView grid;
    private ProgressBar loading;
    private VodAdapter adapter;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private BoxConfig config;
    private Site current;
    private boolean spinnerReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        siteSpinner = findViewById(R.id.siteSpinner);
        searchInput = findViewById(R.id.searchInput);
        grid = findViewById(R.id.grid);
        loading = findViewById(R.id.loading);

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

        loadConfig();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Pick up a newly saved config URL from Settings.
        String url = Prefs.configUrl(this);
        if (!url.isEmpty() && (config == null || !url.equals(lastUrl))) {
            loadConfig();
        }
    }

    private String lastUrl = "";

    private void loadConfig() {
        String url = Prefs.configUrl(this);
        lastUrl = url;
        if (url.isEmpty()) {
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        setLoading(true);
        io.execute(() -> {
            try {
                BoxConfig cfg = BoxConfig.load(url);
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
                if (!spinnerReady) {
                    spinnerReady = true;
                    // fall through: load the initial site too
                }
                current = config.sites.get(position);
                Prefs.siteKey(MainActivity.this, current.key);
                loadHome();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        siteSpinner.setSelection(sel);
    }

    private void loadHome() {
        if (current == null) return;
        setLoading(true);
        io.execute(() -> {
            List<Vod> list = new ArrayList<>();
            try {
                Spider spider = SpiderLoader.get().spider(current);
                list = parseList(spider.homeVideoContent());
                if (list.isEmpty()) {
                    // Fall back: first class from homeContent, then its first page.
                    String home = spider.homeContent(true);
                    JSONObject o = new JSONObject(home);
                    JSONArray classes = o.optJSONArray("class");
                    if (classes != null && classes.length() > 0) {
                        String tid = classes.getJSONObject(0).optString("type_id");
                        list = parseList(spider.categoryContent(tid, "1", true, new HashMap<>()));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            List<Vod> result = list;
            main.post(() -> {
                setLoading(false);
                adapter.setItems(result);
            });
        });
    }

    private void doSearch() {
        String key = searchInput.getText().toString().trim();
        if (key.isEmpty() || current == null) return;
        setLoading(true);
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

    private void setLoading(boolean show) {
        loading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
