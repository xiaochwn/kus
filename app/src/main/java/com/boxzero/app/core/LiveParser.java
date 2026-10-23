package com.boxzero.app.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Parses TVBox "lives" into groups + channels.
 * <p>
 * Accepted formats: classic txt ("group,#genre#" / "name,url"),
 * m3u (#EXTM3U / #EXTINF), or a JSON array of {"name","url"}.
 */
public class LiveParser {

    public static class Channel {
        public String name = "";
        public String url = "";
    }

    public static class Group {
        public String name = "";
        public final List<Channel> channels = new ArrayList<>();
    }

    public static String fetch(String url) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .build();
        Request req = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36").build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) throw new Exception("HTTP " + resp.code());
            return resp.body().string();
        }
    }

    public static List<Group> parse(String content) throws Exception {
        String text = content.trim();
        if (text.startsWith("[")) return parseJson(text);
        if (text.startsWith("#EXTM3U")) return parseM3u(text);
        return parseTxt(text);
    }

    private static List<Group> parseJson(String text) throws Exception {
        List<Group> groups = new ArrayList<>();
        Group g = new Group();
        g.name = "全部";
        groups.add(g);
        JSONArray arr = new JSONArray(text);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Channel c = new Channel();
            c.name = o.optString("name", o.optString("title", "频道" + (i + 1)));
            c.url = o.optString("url", "");
            if (!c.url.isEmpty()) g.channels.add(c);
        }
        return groups;
    }

    private static List<Group> parseM3u(String text) {
        List<Group> groups = new ArrayList<>();
        Group all = new Group();
        all.name = "全部";
        groups.add(all);
        Pattern gp = Pattern.compile("group-title=\"([^\"]*)\"");
        String pendingName = null;
        String pendingGroup = null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("#EXTINF")) {
                int comma = line.lastIndexOf(',');
                pendingName = comma > 0 ? line.substring(comma + 1).trim() : "未知";
                Matcher m = gp.matcher(line);
                pendingGroup = m.find() ? m.group(1) : null;
            } else if (!line.isEmpty() && !line.startsWith("#") && pendingName != null) {
                Group g = findOrCreate(groups, pendingGroup == null || pendingGroup.isEmpty() ? "全部" : pendingGroup);
                Channel c = new Channel();
                c.name = pendingName;
                c.url = line;
                g.channels.add(c);
                pendingName = null;
                pendingGroup = null;
            }
        }
        dropEmpty(groups);
        return groups;
    }

    private static List<Group> parseTxt(String text) {
        List<Group> groups = new ArrayList<>();
        Group cur = new Group();
        cur.name = "全部";
        groups.add(cur);
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.contains(",#genre#")) {
                String name = line.substring(0, line.indexOf(',')).trim();
                if (name.startsWith("#")) name = name.substring(1).trim();
                cur = new Group();
                cur.name = name.isEmpty() ? "分组" : name;
                groups.add(cur);
                continue;
            }
            if (line.startsWith("#")) continue;
            int comma = line.indexOf(',');
            if (comma > 0) {
                Channel c = new Channel();
                c.name = line.substring(0, comma).trim();
                c.url = line.substring(comma + 1).trim();
                if (!c.name.isEmpty() && !c.url.isEmpty()) cur.channels.add(c);
            }
        }
        dropEmpty(groups);
        return groups;
    }

    private static Group findOrCreate(List<Group> groups, String name) {
        for (Group g : groups) if (g.name.equals(name)) return g;
        Group g = new Group();
        g.name = name;
        groups.add(g);
        return g;
    }

    private static void dropEmpty(List<Group> groups) {
        for (int i = groups.size() - 1; i >= 0; i--) {
            if (groups.get(i).channels.isEmpty()) groups.remove(i);
        }
    }

    /** Demo channels from the bundled demo videos (file:// URIs), shown when no lives configured. */
    public static List<Group> demoGroups(Context ctx) {
        List<Group> groups = new ArrayList<>();
        Group g = new Group();
        g.name = "演示频道";
        File dir = new File(ctx.getFilesDir(), "demo");
        File[] files = dir.listFiles();
        if (files != null) {
            int n = 1;
            for (File f : files) {
                if (f.getName().endsWith(".mp4")) {
                    Channel c = new Channel();
                    c.name = "演示 " + n++;
                    c.url = "file://" + f.getAbsolutePath();
                    g.channels.add(c);
                }
            }
        }
        if (!g.channels.isEmpty()) groups.add(g);
        return groups;
    }
}
