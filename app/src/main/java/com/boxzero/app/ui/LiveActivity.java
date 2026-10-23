package com.boxzero.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.boxzero.app.R;
import com.boxzero.app.core.BoxConfig;
import com.boxzero.app.core.LiveParser;
import com.boxzero.app.core.SpiderLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Live TV: group tabs + channel list, plays through PlayerActivity. */
public class LiveActivity extends AppCompatActivity {

    private Spinner groupSpinner;
    private ListView channelList;
    private ProgressBar loading;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private List<LiveParser.Group> groups = new ArrayList<>();
    private boolean spinnerReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live);
        setTitle("直播");

        groupSpinner = findViewById(R.id.groupSpinner);
        channelList = findViewById(R.id.channelList);
        loading = findViewById(R.id.loading);

        channelList.setOnItemClickListener((parent, view, position, id) -> {
            LiveParser.Channel c = currentChannels().get(position);
            Intent it = new Intent(this, PlayerActivity.class);
            it.putExtra("url", c.url);
            it.putExtra("headers", "{}");
            it.putExtra("title", c.name);
            startActivity(it);
        });

        load();
    }

    private List<LiveParser.Channel> currentChannels() {
        int pos = groupSpinner.getSelectedItemPosition();
        if (pos >= 0 && pos < groups.size()) return groups.get(pos).channels;
        return new ArrayList<>();
    }

    private void load() {
        loading.setVisibility(View.VISIBLE);
        io.execute(() -> {
            List<LiveParser.Group> result = new ArrayList<>();
            String err = null;
            try {
                BoxConfig cfg = SpiderLoader.get().getConfig();
                String raw = cfg == null ? "" : cfg.livesRaw.trim();
                if (raw.isEmpty()) {
                    result = LiveParser.demoGroups(this);
                } else if (raw.startsWith("http")) {
                    result = LiveParser.parse(LiveParser.fetch(raw));
                } else {
                    result = LiveParser.parse(raw);
                }
            } catch (Exception e) {
                e.printStackTrace();
                err = e.getMessage();
            }
            List<LiveParser.Group> groupsResult = result;
            String errResult = err;
            main.post(() -> {
                loading.setVisibility(View.GONE);
                if (errResult != null) {
                    Toast.makeText(this, "直播源加载失败: " + errResult, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                if (groupsResult.isEmpty()) {
                    Toast.makeText(this, "没有可用的直播频道", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                groups = groupsResult;
                setupGroups();
            });
        });
    }

    private void setupGroups() {
        spinnerReady = false;
        List<String> names = new ArrayList<>();
        for (LiveParser.Group g : groups) names.add(g.name + " (" + g.channels.size() + ")");
        ArrayAdapter<String> sa = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        groupSpinner.setAdapter(sa);
        groupSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!spinnerReady) spinnerReady = true;
                showChannels(groups.get(position).channels);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        groupSpinner.setSelection(0);
    }

    private void showChannels(List<LiveParser.Channel> channels) {
        List<String> names = new ArrayList<>();
        for (LiveParser.Channel c : channels) names.add(c.name);
        channelList.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, names));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
