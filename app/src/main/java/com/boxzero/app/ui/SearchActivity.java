package com.boxzero.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.boxzero.app.R;
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

/** Search screen. */
public class SearchActivity extends AppCompatActivity {

    private EditText searchInput;
    private RecyclerView grid;
    private ProgressBar loading;
    private VodAdapter adapter;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private Site site;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        setTitle("搜索");

        searchInput = findViewById(R.id.searchInput);
        grid = findViewById(R.id.grid);
        loading = findViewById(R.id.loading);

        String saved = Prefs.siteKey(this);
        site = SpiderLoader.get().getConfig().find(saved);
        if (site == null) {
            Toast.makeText(this, "请先加载接口", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        grid.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new VodAdapter(vod -> {
            Intent it = new Intent(this, DetailActivity.class);
            it.putExtra("siteKey", site.key);
            it.putExtra("vodId", vod.vodId);
            startActivity(it);
        });
        grid.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSearch).setOnClickListener(v -> doSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch();
                return true;
            }
            return false;
        });
    }

    private void doSearch() {
        String key = searchInput.getText().toString().trim();
        if (key.isEmpty()) return;
        loading.setVisibility(View.VISIBLE);
        io.execute(() -> {
            List<Vod> list = new ArrayList<>();
            try {
                Spider spider = SpiderLoader.get().spider(site);
                JSONObject o = new JSONObject(spider.searchContent(key, true, "1"));
                JSONArray arr = o.optJSONArray("list");
                if (arr != null) {
                    list = gson.fromJson(arr.toString(), new TypeToken<List<Vod>>() {
                    }.getType());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            List<Vod> result = list;
            main.post(() -> {
                loading.setVisibility(View.GONE);
                adapter.setItems(result);
                if (result.isEmpty()) Toast.makeText(this, "没有搜到", Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
