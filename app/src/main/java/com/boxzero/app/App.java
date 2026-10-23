package com.boxzero.app;

import android.app.Application;

import com.boxzero.app.core.ProxyServer;
import com.boxzero.app.core.SpiderLoader;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

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
        // Local HTTP proxy: spiders rewrite URLs to http://127.0.0.1:port?do=py&siteKey=...
        ProxyServer.get().start();
        SpiderLoader.get().init(this);
    }
}
