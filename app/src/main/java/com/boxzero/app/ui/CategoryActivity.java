package com.boxzero.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.boxzero.app.R;
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

/** Category grid with pagination. */
public class CategoryActivity extends AppCompatActivity {

    private TextView titleView;
    private TextView pageInfo;
    private Button btnPrev;
    private Button btnNext;
    private RecyclerView grid;
    private ProgressBar loading;
    private VodAdapter adapter;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private Site site;
    private String tid;
    private int page = 1;
    private int pageCount = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);

        titleView = findViewById(R.id.title);
        pageInfo = findViewById(R.id.pageInfo);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        grid = findViewById(R.id.grid);
        loading = findViewById(R.id.loading);

        String siteKey = getIntent().getStringExtra("siteKey");
        tid = getIntent().getStringExtra("tid");
        String title = getIntent().getStringExtra("title");
        titleView.setText(title == null ? "" : title);
        setTitle(title == null ? "" : title);

        site = SpiderLoader.get().getConfig().find(siteKey);
        if (site == null || tid == null) {
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
        btnPrev.setOnClickListener(v -> {
            if (page > 1) load(page - 1);
        });
        btnNext.setOnClickListener(v -> {
            if (page < pageCount) load(page + 1);
        });

        load(1);
    }

    private void load(int p) {
        loading.setVisibility(android.view.View.VISIBLE);
        io.execute(() -> {
            List<Vod> list = new ArrayList<>();
            int pc = 1;
            try {
                Spider spider = SpiderLoader.get().spider(site);
                String r = spider.categoryContent(tid, String.valueOf(p), true, new HashMap<>());
                JSONObject o = new JSONObject(r);
                JSONArray arr = o.optJSONArray("list");
                if (arr != null) {
                    list = gson.fromJson(arr.toString(), new TypeToken<List<Vod>>() {
                    }.getType());
                }
                pc = Math.max(1, o.optInt("pagecount", 1));
            } catch (Exception e) {
                e.printStackTrace();
            }
            List<Vod> result = list;
            int pageCountResult = pc;
            main.post(() -> {
                loading.setVisibility(android.view.View.GONE);
                if (result.isEmpty() && p == 1) {
                    Toast.makeText(this, "这个分类没有内容", Toast.LENGTH_SHORT).show();
                }
                page = p;
                pageCount = pageCountResult;
                adapter.setItems(result);
                pageInfo.setText("第 " + page + " / " + pageCount + " 页");
                btnPrev.setEnabled(page > 1);
                btnNext.setEnabled(page < pageCount);
                grid.scrollToPosition(0);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
