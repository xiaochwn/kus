package com.boxzero.app.core;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Loads a TVBox-style config JSON from a URL.
 * <p>
 * Recognized top-level fields: "spider" (jar URL for csp_* sites),
 * "sites" (array of {@link Site}), "parses" (kept raw for player use).
 */
public class BoxConfig {

    public String jarUrl = "";
    public String rawParses = "[]";
    public final List<Site> sites = new ArrayList<>();

    public static BoxConfig load(String url) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .build();
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new Exception("HTTP " + resp.code());
            }
            return parse(resp.body().string());
        }
    }

    /** Jar URL may carry ";md5;..." suffixes in the wild; strip them. */
    public String cleanJarUrl() {
        if (jarUrl == null) return "";
        int i = jarUrl.indexOf(';');
        return (i < 0 ? jarUrl : jarUrl.substring(0, i)).trim();
    }

    /** Parses an already-loaded config JSON string. */
    public static BoxConfig parse(String body) throws Exception {
        JSONObject root = new JSONObject(body);
        BoxConfig cfg = new BoxConfig();
        cfg.jarUrl = root.optString("spider", "");
        JSONArray parses = root.optJSONArray("parses");
        if (parses != null) cfg.rawParses = parses.toString();
        JSONArray arr = root.optJSONArray("sites");
        Gson gson = new Gson();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                Site s = gson.fromJson(arr.getJSONObject(i).toString(), Site.class);
                if (s.key != null && !s.key.isEmpty()) cfg.sites.add(s);
            }
        }
        return cfg;
    }

    /** Loads a config JSON from a local file (built-in demo source). */
    public static BoxConfig loadFromFile(File file) throws Exception {
        byte[] bytes;
        try (java.io.FileInputStream in = new java.io.FileInputStream(file);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            bytes = out.toByteArray();
        }
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }

    public Site find(String key) {
        for (Site s : sites) if (s.key.equals(key)) return s;
        return sites.isEmpty() ? null : sites.get(0);
    }
}
