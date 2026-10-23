package com.boxzero.app;

import android.app.Application;
import android.content.res.AssetManager;

import com.boxzero.app.core.ProxyServer;
import com.boxzero.app.core.SpiderLoader;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class App extends Application {

    private static App instance;

    public static App get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // Embedded Python interpreter (Chaquopy) for .py spiders.
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        // Built-in demo assets (config + spider + videos + posters) -> filesDir,
        // so the demo source works fully offline.
        File demoDir = copyDemoAssets();
        // Local HTTP proxy: spiders rewrite URLs to http://127.0.0.1:port?...;
        // also serves the built-in demo videos/posters.
        ProxyServer.get().setDemoDir(demoDir);
        ProxyServer.get().start();
        SpiderLoader.get().init(this);
    }

    /** Copies assets/demo to filesDir/demo on first launch. Returns the dir. */
    private File copyDemoAssets() {
        File demoDir = new File(getFilesDir(), "demo");
        if (new File(demoDir, "spider_demo.py").exists()) return demoDir;
        try {
            copyAssetDir(getAssets(), "demo", demoDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return demoDir;
    }

    private void copyAssetDir(AssetManager am, String assetPath, File outDir) throws IOException {
        String[] children = am.list(assetPath);
        if (children == null || children.length == 0) {
            // It's a file.
            if (!outDir.getParentFile().exists()) outDir.getParentFile().mkdirs();
            try (InputStream in = am.open(assetPath);
                 OutputStream out = new FileOutputStream(outDir)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return;
        }
        if (!outDir.exists()) outDir.mkdirs();
        for (String child : children) {
            copyAssetDir(am, assetPath + "/" + child, new File(outDir, child));
        }
    }
}
