package com.boxzero.app.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.boxzero.app.R;
import com.boxzero.app.core.HistoryStore;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private String recSiteKey = "";
    private String recVodId = "";
    private String recEpUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.playerView);
        String url = getIntent().getStringExtra("url");
        String headersJson = getIntent().getStringExtra("headers");
        String title = getIntent().getStringExtra("title");
        if (title != null) setTitle(title);
        // History record keys for progress tracking (may be absent for live TV).
        if (getIntent().getStringExtra("siteKey") != null) recSiteKey = getIntent().getStringExtra("siteKey");
        if (getIntent().getStringExtra("vodId") != null) recVodId = getIntent().getStringExtra("vodId");
        if (getIntent().getStringExtra("epUrl") != null) recEpUrl = getIntent().getStringExtra("epUrl");

        Map<String, String> headers = parseHeaders(headersJson);
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
        if (!headers.isEmpty()) httpFactory.setDefaultRequestProperties(headers);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(httpFactory))
                .build();
        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.play();
    }

    private Map<String, String> parseHeaders(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null) return map;
        try {
            JSONObject o = new JSONObject(json);
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                map.put(k, o.optString(k));
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveProgress();
        if (player != null) player.pause();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
    }

    /** Persists playback progress into the history record. */
    private void saveProgress() {
        if (player == null || recEpUrl.isEmpty()) return;
        try {
            long pos = player.getCurrentPosition();
            long dur = player.getDuration();
            if (dur < 0) dur = 0;
            // Treat "finished" as fully watched.
            if (dur > 0 && dur - pos < 5000) pos = dur;
            HistoryStore.updateProgress(this, recSiteKey, recVodId, recEpUrl, pos, dur);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
