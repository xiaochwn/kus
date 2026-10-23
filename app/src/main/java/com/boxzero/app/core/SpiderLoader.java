package com.boxzero.app.core;

import android.content.Context;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns spider instances. Dispatch rule (ecosystem convention):
 * api contains ".py" -> {@link PySpider} (Chaquopy),
 * api starts with "csp_" -> {@link JarSpider} (DexClassLoader),
 * otherwise -> {@link SpiderNull}.
 */
public class SpiderLoader {

    private static final SpiderLoader INSTANCE = new SpiderLoader();

    public static SpiderLoader get() {
        return INSTANCE;
    }

    private Context appContext;
    private BoxConfig config;
    private final Map<String, Spider> cache = new ConcurrentHashMap<>();
    private volatile String recentKey = "";

    public void init(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public synchronized void setConfig(BoxConfig config) {
        clear();
        this.config = config;
    }

    public BoxConfig getConfig() {
        return config;
    }

    public Spider spider(Site site) {
        if (site == null) return new SpiderNull();
        recentKey = site.key;
        Spider hit = cache.get(site.key);
        if (hit != null) return hit;
        Spider created = create(site);
        cache.put(site.key, created);
        return created;
    }

    private Spider create(Site site) {
        try {
            String api = site.api == null ? "" : site.api;
            String ext = site.ext == null ? "" : site.ext;
            // "@demo/..." -> filesDir/demo/... (built-in offline demo source)
            if (api.startsWith("@demo/")) {
                api = new java.io.File(new java.io.File(appContext.getFilesDir(), "demo"),
                        api.substring(6)).getAbsolutePath();
            }
            Spider spider;
            if (site.isPy()) {
                spider = new PySpider(appContext, site.key, api, ext, ProxyServer.get().baseUrl());
            } else if (site.isJar()) {
                String jarUrl = config == null ? "" : config.cleanJarUrl();
                spider = JarSpider.load(appContext, jarUrl, api, ext);
            } else {
                return new SpiderNull();
            }
            spider.siteKey = site.key;
            return spider;
        } catch (Throwable t) {
            t.printStackTrace();
            return new SpiderNull();
        }
    }

    /** Called by {@link ProxyServer} for ?do=py requests. */
    public Object[] proxy(Map<String, String> params) throws Exception {
        Spider spider = cache.get(recentKey);
        if (spider == null) return null;
        return spider.proxy(params);
    }

    public synchronized void clear() {
        for (Spider s : cache.values()) {
            try {
                s.destroy();
            } catch (Exception ignored) {
            }
        }
        cache.clear();
        recentKey = "";
    }
}
