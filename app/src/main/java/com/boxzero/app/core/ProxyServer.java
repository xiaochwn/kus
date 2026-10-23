package com.boxzero.app.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal local HTTP server on 127.0.0.1.
 * <p>
 * Spiders (py and jar) rewrite protected URLs to
 * {@code http://127.0.0.1:PORT?do=py&siteKey=...&...}; when ExoPlayer
 * requests them we forward to the spider's {@code proxy()} and stream
 * the bytes back. Only GET is needed.
 */
public class ProxyServer {

    private static final ProxyServer INSTANCE = new ProxyServer();

    public static ProxyServer get() {
        return INSTANCE;
    }

    private ServerSocket server;
    private int port;

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
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // skip headers
            }
            Map<String, String> params = parseQuery(requestLine);
            Object[] result = null;
            if ("py".equals(params.get("do"))) {
                try {
                    result = SpiderLoader.get().proxy(params);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            OutputStream out = s.getOutputStream();
            if (result == null) {
                write(out, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            int code = (int) result[0];
            String mime = String.valueOf(result[1]);
            byte[] data = toBytes(result[2]);
            write(out, code, mime, data);
        } catch (Exception ignored) {
        }
    }

    private byte[] toBytes(Object o) {
        if (o instanceof byte[]) return (byte[]) o;
        if (o instanceof InputStream) {
            try (InputStream in = (InputStream) o) {
                return in.readAllBytes();
            } catch (IOException e) {
                return new byte[0];
            }
        }
        if (o instanceof String) return ((String) o).getBytes(StandardCharsets.UTF_8);
        return new byte[0];
    }

    private void write(OutputStream out, int code, String mime, byte[] data) throws IOException {
        String status = code == 200 ? "OK" : code == 206 ? "Partial Content" : "Error";
        String head = "HTTP/1.0 " + code + " " + status + "\r\n"
                + "Content-Type: " + mime + "\r\n"
                + "Content-Length: " + data.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
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
