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

/** Favorites, persisted as JSON. */
public class FavStore {

    public static class Item {
        public String siteKey = "";
        public String vodId = "";
        public String vodName = "";
        public String vodPic = "";
        public long time;
    }

    private static final String FILE = "favorites.json";
    private static final Gson GSON = new Gson();

    /** Toggles favorite state. Returns true if now favorited. */
    public static synchronized boolean toggle(Context ctx, Item item) {
        List<Item> list = list(ctx);
        for (int i = 0; i < list.size(); i++) {
            Item x = list.get(i);
            if (x.siteKey.equals(item.siteKey) && x.vodId.equals(item.vodId)) {
                list.remove(i);
                save(ctx, list);
                return false;
            }
        }
        item.time = System.currentTimeMillis();
        list.add(0, item);
        save(ctx, list);
        return true;
    }

    public static synchronized boolean isFav(Context ctx, String siteKey, String vodId) {
        for (Item x : list(ctx)) {
            if (x.siteKey.equals(siteKey) && x.vodId.equals(vodId)) return true;
        }
        return false;
    }

    public static synchronized List<Item> list(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), FILE);
            if (!f.exists()) return new ArrayList<>();
            byte[] bytes = HistoryStore.readAll(f);
            List<Item> list = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8),
                    new TypeToken<List<Item>>() {
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

    private static void save(Context ctx, List<Item> list) {
        try (FileOutputStream out = new FileOutputStream(new File(ctx.getFilesDir(), FILE))) {
            out.write(GSON.toJson(list).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }
}
