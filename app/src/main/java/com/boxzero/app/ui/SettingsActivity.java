package com.boxzero.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.boxzero.app.BuildConfig;
import com.boxzero.app.R;
import com.boxzero.app.core.FavStore;
import com.boxzero.app.core.HistoryStore;
import com.boxzero.app.core.Prefs;

/** "我的" page: profile header, version, and settings rows. */
public class SettingsActivity extends AppCompatActivity {

    private TextView configSummary;
    private TextView tmdbSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle("我的");
        BottomNav.setup(this, BottomNav.TAB_MINE);

        ((TextView) findViewById(R.id.versionName)).setText(BuildConfig.VERSION_NAME);
        configSummary = findViewById(R.id.configSummary);
        tmdbSummary = findViewById(R.id.tmdbSummary);

        findViewById(R.id.rowConfig).setOnClickListener(v -> editConfigUrl());
        findViewById(R.id.rowTmdb).setOnClickListener(v -> editTmdbKey());
        findViewById(R.id.rowClearHistory).setOnClickListener(v -> confirmClear(true));
        findViewById(R.id.rowClearFav).setOnClickListener(v -> confirmClear(false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSummaries();
        BottomNav.setup(this, BottomNav.TAB_MINE);
    }

    private void refreshSummaries() {
        String url = Prefs.configUrl(this);
        configSummary.setText(url.isEmpty() ? "内置演示源" : url);
        String key = Prefs.tmdbKey(this);
        tmdbSummary.setText(key.isEmpty() ? "未设置，点击填写" : "已设置（" + mask(key) + "）");
    }

    private static String mask(String key) {
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private void editConfigUrl() {
        EditText input = new EditText(this);
        input.setText(Prefs.configUrl(this));
        input.setHint("https://…/config.json（留空使用内置演示源）");
        input.setSingleLine(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("接口地址")
                .setView(input)
                .setPositiveButton("保存", (d, which) -> {
                    String url = input.getText().toString().trim();
                    Prefs.configUrl(this, url);
                    Toast.makeText(this, url.isEmpty() ? "已切换为内置演示源" : "已保存", Toast.LENGTH_SHORT).show();
                    refreshSummaries();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void editTmdbKey() {
        EditText input = new EditText(this);
        input.setText(Prefs.tmdbKey(this));
        input.setHint("themoviedb.org → Settings → API");
        input.setSingleLine(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("TMDB API Key")
                .setView(input)
                .setPositiveButton("保存", (d, which) -> {
                    Prefs.tmdbKey(this, input.getText().toString());
                    Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                    refreshSummaries();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmClear(boolean history) {
        new AlertDialog.Builder(this)
                .setTitle(history ? "清空历史记录？" : "清空收藏？")
                .setPositiveButton("清空", (d, which) -> {
                    if (history) HistoryStore.clear(this);
                    else FavStore.clear(this);
                    Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
