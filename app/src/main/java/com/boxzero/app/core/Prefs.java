package com.boxzero.app.core;

import android.content.Context;
import android.content.SharedPreferences;

/** Tiny SharedPreferences wrapper for config URL + current site. */
public class Prefs {

    private static final String FILE = "boxzero";
    private static final String K_CONFIG = "config_url";
    private static final String K_SITE = "site_key";
    private static final String K_TMDB = "tmdb_key";

    /**
     * Empty means "use the built-in demo source" (assets/demo copied to
     * filesDir on first launch, fully offline). A non-empty value is a
     * user-supplied TVBox-style config URL.
     */
    public static final String DEFAULT_CONFIG = "";

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

    /** TMDB API key (v3). Empty = scraping disabled. */
    public static String tmdbKey(Context ctx) {
        return prefs(ctx).getString(K_TMDB, "");
    }

    public static void tmdbKey(Context ctx, String key) {
        prefs(ctx).edit().putString(K_TMDB, key == null ? "" : key.trim()).apply();
    }
}
