package com.boxzero.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.boxzero.app.R;
import com.boxzero.app.core.PlayerLauncher;

/**
 * Opens a parse page in a WebView and sniffs video URLs (.m3u8/.mp4/...)
 * from resource requests and navigations. First hit wins.
 */
public class SniffActivity extends AppCompatActivity {

    private static final long TIMEOUT_MS = 30000;

    private WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean found = false;
    private final Runnable timeout = () -> {
        if (!found) {
            Toast.makeText(this, "解析超时，换条线路试试", Toast.LENGTH_LONG).show();
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sniff);

        String url = getIntent().getStringExtra("url");
        String title = getIntent().getStringExtra("title");
        if (title != null) setTitle("解析: " + title);
        TextView status = findViewById(R.id.status);
        status.setText("正在解析…");

        Button cancel = findViewById(R.id.btnCancel);
        cancel.setOnClickListener(v -> finish());

        webView = findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                check(request.getUrl().toString());
                return false;
            }

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                check(request.getUrl().toString());
                return super.shouldInterceptRequest(view, request);
            }
        });
        handler.postDelayed(timeout, TIMEOUT_MS);
        if (url != null) webView.loadUrl(url);
        else finish();
    }

    private void check(String url) {
        if (!found && PlayerLauncher.isVideoUrl(url)) {
            found = true;
            handler.post(() -> onFound(url));
        }
    }

    private void onFound(String videoUrl) {
        handler.removeCallbacks(timeout);
        Intent it = new Intent(this, PlayerActivity.class);
        it.putExtra("url", videoUrl);
        it.putExtra("headers", getIntent().getStringExtra("headers"));
        it.putExtra("title", getIntent().getStringExtra("title"));
        startActivity(it);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timeout);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
