package com.boxzero.app.core;

import android.content.Context;
import android.content.SharedPreferences;

/** Tiny SharedPreferences wrapper for config URL + current site. */
public class Prefs {

    private static final String FILE = "boxzero";
    private static final String K_CONFIG = "config_url";
    private static final String K_SITE = "site_key";

    /** Demo config bundled via the repo: install-and-play, no typing needed. */
    public static final String DEFAULT_CONFIG =
            "https://cdn.jsdelivr.net/gh/xiaochwn/kus@main/demo/config.json";

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String configUrl(Context ctx) {
        return prefs(ctx).getString(K_CONFIG, DEFAULT_CONFIG);
    }

    public static void configUrl(Context ctx, String url) {
        prefs(ctx).edit().putString(K_CONFIG, url == null ? "" : url).apply();
    }

    public static String siteKey(Context ctx) {
        return prefs(ctx).getString(K_SITE, "");
    }

    public static void siteKey(Context ctx, String key) {
        prefs(ctx).edit().putString(K_SITE, key == null ? "" : key).apply();
    }
}
