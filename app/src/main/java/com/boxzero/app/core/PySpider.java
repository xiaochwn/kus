package com.boxzero.app.core;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.github.catvod.crawler.Spider;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a .py spider inside the embedded Chaquopy interpreter.
 * <p>
 * The Python side (boxzero/app.py) owns the call protocol:
 * Java passes JSON strings for extend/ids/vipFlags/params and gets
 * JSON strings back. Python bools come back as Java booleans.
 */
public class PySpider extends Spider {

    private static final Gson GSON = new Gson();

    private final PyObject mod;
    private final PyObject obj;

    public PySpider(Context context, String siteKey, String api, String ext, String proxyBase) throws Exception {
        this.siteKey = siteKey == null ? "" : siteKey;
        Python py = Python.getInstance();
        mod = py.getModule("boxzero.app");
        obj = mod.callAttr("create", api == null ? "" : api, this.siteKey, proxyBase == null ? "" : proxyBase);
        mod.callAttr("init_spider", obj, ext == null ? "" : ext);
    }

    private String str(PyObject o) {
        return o == null ? "" : o.toString();
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        return str(mod.callAttr("home", obj, filter));
    }

    @Override
    public String homeVideoContent() throws Exception {
        return str(mod.callAttr("home_video", obj));
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return str(mod.callAttr("category", obj, tid, pg, filter, GSON.toJson(extend)));
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        return str(mod.callAttr("detail", obj, GSON.toJson(ids)));
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return str(mod.callAttr("search", obj, key, quick, pg));
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return str(mod.callAttr("player", obj, flag, id, GSON.toJson(vipFlags)));
    }

    @Override
    public String liveContent(String url) throws Exception {
        return str(mod.callAttr("live", obj, url));
    }

    @Override
    public boolean manualVideoCheck() {
        try {
            return mod.callAttr("manual_check", obj).toBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isVideoFormat(String url) {
        try {
            return mod.callAttr("is_video", obj, url).toBoolean();
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        PyObject r = mod.callAttr("proxy", obj, GSON.toJson(params));
        List<PyObject> list = r.asList();
        int code = list.get(0).toInt();
        String mime = list.get(1).toString();
        byte[] data = list.get(2).toJava(byte[].class);
        return new Object[]{code, mime, data};
    }

    @Override
    public void destroy() {
        try {
            mod.callAttr("destroy_spider", obj);
        } catch (Exception ignored) {
        }
    }
}
