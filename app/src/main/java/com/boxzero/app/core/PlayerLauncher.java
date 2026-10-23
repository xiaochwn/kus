package com.boxzero.app.core;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.boxzero.app.ui.PlayerActivity;
import com.boxzero.app.ui.SniffActivity;
import com.github.catvod.crawler.Spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Resolves an episode to a playable URL and launches playback.
 * <p>
 * playerContent parse==0: play directly.
 * parse==1: first try the config's "parses" (type 0 concat / type 1 JSON);
 * if that yields a direct video URL, play it; otherwise hand the parse
 * page to {@link SniffActivity} for WebView sniffing.
 */
public class PlayerLauncher {

    private static final ExecutorService IO = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void play(Activity activity, Site site, String flag, String epUrl, String title) {
        play(activity, site, flag, epUrl, title, "", "", "");
    }

    /**
     * @param siteKey/vodId/epUrlOfRecord history record keys for progress tracking;
     *                                    empty when not applicable (e.g. live TV).
     */
    public static void play(Activity activity, Site site, String flag, String epUrl, String title,
                            String siteKey, String vodId, String epUrlOfRecord) {
        Toast.makeText(activity.getApplicationContext(), "解析中…", Toast.LENGTH_SHORT).show();
        IO.execute(() -> {
            try {
                Spider spider = SpiderLoader.get().spider(site);
                String json = spider.playerContent(flag, epUrl, new ArrayList<>());
                JSONObject o = new JSONObject(json);
                int parse = o.optInt("parse", 0);
                String url = o.optString("url", "");
                String header = o.optJSONObject("header") != null ? o.optJSONObject("header").toString() : "{}";
                if (url.isEmpty()) throw new Exception("empty play url");
                if (parse == 1) {
                    String direct = tryParse(site, url);
                    if (direct != null && isVideoUrl(direct)) {
                        launchPlayer(activity, direct, header, title, siteKey, vodId, epUrlOfRecord);
                        return;
                    }
                    String sniffUrl = direct != null ? direct : firstParseTarget(site, url);
                    if (sniffUrl == null) throw new Exception("无可用解析接口");
                    launchSniff(activity, sniffUrl, header, title, siteKey, vodId, epUrlOfRecord);
                    return;
                }
                launchPlayer(activity, url, header, title, siteKey, vodId, epUrlOfRecord);
            } catch (Exception e) {
                e.printStackTrace();
                MAIN.post(() -> Toast.makeText(activity.getApplicationContext(),
                        "播放解析失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    public static boolean isVideoUrl(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        int q = u.indexOf('?');
        if (q > 0) u = u.substring(0, q);
        int h = u.indexOf('#');
        if (h > 0) u = u.substring(0, h);
        return u.endsWith(".m3u8") || u.endsWith(".mp4") || u.endsWith(".m4s")
                || u.endsWith(".flv") || u.endsWith(".ts") || u.endsWith(".mov");
    }

    /** Best-effort: type 0 concat, type 1 JSON fetch. Returns null if unusable. */
    private static String tryParse(Site site, String playUrl) {
        try {
            JSONArray arr = new JSONArray(SpiderLoader.get().getConfig().rawParses);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.getJSONObject(i);
                int type = p.optInt("type", -1);
                String base = p.optString("url", "");
                if (base.isEmpty()) continue;
                if (type == 0) return base + playUrl;
                if (type == 1) {
                    String body = httpGet(base + playUrl);
                    String u = new JSONObject(body).optString("url", "");
                    if (!u.isEmpty()) return u;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String firstParseTarget(Site site, String playUrl) {
        try {
            JSONArray arr = new JSONArray(SpiderLoader.get().getConfig().rawParses);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.getJSONObject(i);
                String base = p.optString("url", "");
                if (!base.isEmpty()) return base + playUrl;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String httpGet(String url) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        Request req = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36").build();
        try (Response resp = client.newCall(req).execute()) {
            if (resp.body() == null) throw new Exception("HTTP " + resp.code());
            return resp.body().string();
        }
    }

    private static void launchPlayer(Activity activity, String url, String headers, String title,
                                     String siteKey, String vodId, String epUrlOfRecord) {
        MAIN.post(() -> {
            Intent it = new Intent(activity, PlayerActivity.class);
            it.putExtra("url", url);
            it.putExtra("headers", headers);
            it.putExtra("title", title);
            it.putExtra("siteKey", siteKey);
            it.putExtra("vodId", vodId);
            it.putExtra("epUrl", epUrlOfRecord);
            activity.startActivity(it);
        });
    }

    private static void launchSniff(Activity activity, String url, String headers, String title,
                                    String siteKey, String vodId, String epUrlOfRecord) {
        MAIN.post(() -> {
            Intent it = new Intent(activity, SniffActivity.class);
            it.putExtra("url", url);
            it.putExtra("headers", headers);
            it.putExtra("title", title);
            it.putExtra("siteKey", siteKey);
            it.putExtra("vodId", vodId);
            it.putExtra("epUrl", epUrlOfRecord);
            activity.startActivity(it);
        });
    }
}
