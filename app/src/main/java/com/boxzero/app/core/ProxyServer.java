package com.boxzero.app.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal local HTTP server on 127.0.0.1.
 * <p>
 * Two jobs:
 * 1. Spider localProxy: spiders rewrite protected URLs to
 * {@code http://127.0.0.1:PORT?do=py&siteKey=...}; we forward to the
 * spider's {@code proxy()} and stream the bytes back.
 * 2. Built-in demo assets: {@code ?do=demo_video&name=...} /
 * {@code ?do=demo_poster&name=...} serve files from filesDir/demo,
 * so the demo source plays fully offline (with Range support for seeking).
 */
public class ProxyServer {

    private static final ProxyServer INSTANCE = new ProxyServer();

    public static ProxyServer get() {
        return INSTANCE;
    }

    private ServerSocket server;
    private int port;
    private File demoDir;

    public synchronized void start() {
        if (server != null) return;
        try {
            server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            port = server.getLocalPort();
            Thread t = new Thread(this::loop, "boxzero-proxy");
            t.setDaemon(true);
            t.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setDemoDir(File dir) {
        this.demoDir = dir;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private void loop() {
        while (server != null && !server.isClosed()) {
            try {
                Socket socket = server.accept();
                new Thread(() -> handle(socket), "boxzero-proxy-conn").start();
            } catch (IOException ignored) {
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String requestLine = in.readLine();
            if (requestLine == null) return;
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                int c = line.indexOf(':');
                if (c > 0) headers.put(line.substring(0, c).trim().toLowerCase(Locale.US),
                        line.substring(c + 1).trim());
            }
            Map<String, String> params = parseQuery(requestLine);
            String doParam = params.get("do");
            Object[] result = null; // {code, mime, byte[], extraHeaders?}
            try {
                if ("py".equals(doParam)) {
                    result = SpiderLoader.get().proxy(params);
                } else if ("demo_video".equals(doParam) || "demo_poster".equals(doParam)) {
                    result = serveDemoFile(doParam, params.get("name"), headers.get("range"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            OutputStream out = s.getOutputStream();
            if (result == null) {
                write(out, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8), null);
                return;
            }
            int code = (int) result[0];
            String mime = String.valueOf(result[1]);
            byte[] data = toBytes(result[2]);
            @SuppressWarnings("unchecked")
            Map<String, String> extra = result.length > 3 ? (Map<String, String>) result[3] : null;
            write(out, code, mime, data, extra);
        } catch (Exception ignored) {
        }
    }

    /** Serves filesDir/demo/videos|posters/<name>, honoring Range requests. */
    private Object[] serveDemoFile(String kind, String name, String range) {
        if (demoDir == null || name == null || !name.matches("[A-Za-z0-9_.-]+")) return null;
        String sub = "demo_video".equals(kind) ? "videos" : "posters";
        File f = new File(new File(demoDir, sub), name);
        if (!f.isFile()) return null;
        String mime = "demo_video".equals(kind) ? "video/mp4" : "image/jpeg";
        try {
            byte[] all = Files.readAllBytes(f.toPath());
            long len = all.length;
            if (range != null && range.startsWith("bytes=")) {
                long[] r = parseRange(range.substring(6), len);
                if (r != null) {
                    byte[] part = Arrays.copyOfRange(all, (int) r[0], (int) (r[1] + 1));
                    Map<String, String> extra = new HashMap<>();
                    extra.put("Content-Range", "bytes " + r[0] + "-" + r[1] + "/" + len);
                    return new Object[]{206, mime, part, extra};
                }
            }
            return new Object[]{200, mime, all, null};
        } catch (IOException e) {
            return null;
        }
    }

    /** Parses "0-" / "0-1023" against length; null if unsatisfiable. */
    private long[] parseRange(String spec, long len) {
        try {
            String s = spec.split(",")[0].trim();
            long start, end;
            if (s.endsWith("-")) {
                start = Long.parseLong(s.substring(0, s.length() - 1));
                end = len - 1;
            } else {
                String[] p = s.split("-", 2);
                start = Long.parseLong(p[0]);
                end = Long.parseLong(p[1]);
            }
            if (start < 0 || start >= len) return null;
            end = Math.min(end, len - 1);
            if (end < start) return null;
            return new long[]{start, end};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private byte[] toBytes(Object o) {
        if (o instanceof byte[]) return (byte[]) o;
        if (o instanceof InputStream) {
            try (InputStream in = (InputStream) o;
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            } catch (IOException e) {
                return new byte[0];
            }
        }
        if (o instanceof String) return ((String) o).getBytes(StandardCharsets.UTF_8);
        return new byte[0];
    }

    private void write(OutputStream out, int code, String mime, byte[] data,
                       Map<String, String> extra) throws IOException {
        String status = code == 200 ? "OK" : code == 206 ? "Partial Content" : "Error";
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.0 ").append(code).append(' ').append(status).append("\r\n");
        h.append("Content-Type: ").append(mime).append("\r\n");
        h.append("Content-Length: ").append(data.length).append("\r\n");
        h.append("Accept-Ranges: bytes\r\n");
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                h.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
        }
        h.append("Connection: close\r\n\r\n");
        out.write(h.toString().getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.flush();
    }

    private Map<String, String> parseQuery(String requestLine) {
        Map<String, String> map = new HashMap<>();
        try {
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return map;
            String path = parts[1];
            int q = path.indexOf('?');
            if (q < 0) return map;
            for (String kv : path.substring(q + 1).split("&")) {
                int eq = kv.indexOf('=');
                if (eq < 0) continue;
                String k = URLDecoder.decode(kv.substring(0, eq), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(kv.substring(eq + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
        } catch (Exception ignored) {
        }
        return map;
    }
}
