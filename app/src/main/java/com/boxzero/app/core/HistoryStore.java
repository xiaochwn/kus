package com.boxzero.app.core;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Watch history, persisted as JSON. Most recent first, capped at 100. */
public class HistoryStore {

    public static class Record {
        public String siteKey = "";
        public String vodId = "";
        public String vodName = "";
        public String vodPic = "";
        public String flag = "";
        public String epName = "";
        public String epUrl = "";
        public long time;
        /** Playback progress in ms; duration<=0 means unknown. */
        public long position;
        public long duration;
    }

    private static final String FILE = "history.json";
    private static final int MAX = 100;
    private static final Gson GSON = new Gson();

    public static synchronized void add(Context ctx, Record r) {
        List<Record> list = list(ctx);
        for (int i = list.size() - 1; i >= 0; i--) {
            Record x = list.get(i);
            if (x.siteKey.equals(r.siteKey) && x.vodId.equals(r.vodId) && x.epUrl.equals(r.epUrl)) {
                list.remove(i);
            }
        }
        r.time = System.currentTimeMillis();
        list.add(0, r);
        while (list.size() > MAX) list.remove(list.size() - 1);
        save(ctx, list);
    }

    public static synchronized List<Record> list(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), FILE);
            if (!f.exists()) return new ArrayList<>();
            byte[] bytes = readAll(f);
            List<Record> list = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8),
                    new TypeToken<List<Record>>() {
                    }.getType());
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static synchronized void clear(Context ctx) {
        // noinspection ResultOfMethodCallIgnored
        new File(ctx.getFilesDir(), FILE).delete();
    }

    /** Updates playback progress for an existing record (matched by episode). */
    public static synchronized void updateProgress(Context ctx, String siteKey, String vodId,
                                                  String epUrl, long position, long duration) {
        List<Record> list = list(ctx);
        boolean changed = false;
        for (Record r : list) {
            if (r.siteKey.equals(siteKey) && r.vodId.equals(vodId) && r.epUrl.equals(epUrl)) {
                r.position = position;
                r.duration = duration;
                changed = true;
                break;
            }
        }
        if (changed) save(ctx, list);
    }

    private static void save(Context ctx, List<Record> list) {
        try (FileOutputStream out = new FileOutputStream(new File(ctx.getFilesDir(), FILE))) {
            out.write(GSON.toJson(list).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    static byte[] readAll(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
