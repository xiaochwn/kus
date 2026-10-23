package com.boxzero.app.core;

import android.content.Context;

import com.github.catvod.crawler.Spider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import dalvik.system.DexClassLoader;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Loads jar spiders (api = "csp_Xxx") with a DexClassLoader.
 * The jar is downloaded once from the config's "spider" URL and cached
 * under filesDir/jars. Because our {@link Spider} keeps the ecosystem's
 * binary name (com.github.catvod.crawler.Spider), classes compiled
 * against the original interface link and cast cleanly.
 */
public class JarSpider {

    public static Spider load(Context context, String jarUrl, String api, String ext) throws Exception {
        String clean = jarUrl == null ? "" : jarUrl.split(";")[0].trim();
        if (clean.isEmpty()) throw new Exception("config has no spider jar url");
        File dir = new File(context.getFilesDir(), "jars");
        if (!dir.exists()) dir.mkdirs();
        File jar = new File(dir, "spider_" + Integer.toHexString(clean.hashCode()) + ".jar");
        if (!jar.exists() || jar.length() == 0) {
            download(clean, jar);
        }
        File dexDir = context.getDir("dex", Context.MODE_PRIVATE);
        DexClassLoader loader = new DexClassLoader(
                jar.getAbsolutePath(), dexDir.getAbsolutePath(), null,
                context.getClassLoader());
        Class<?> clazz = loader.loadClass(api);
        Spider spider = (Spider) clazz.getDeclaredConstructor().newInstance();
        spider.init(context.getApplicationContext(), ext == null ? "" : ext);
        return spider;
    }

    private static void download(String url, File to) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) throw new Exception("jar HTTP " + resp.code());
            try (InputStream in = resp.body().byteStream();
                 FileOutputStream out = new FileOutputStream(to)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        }
    }
}
