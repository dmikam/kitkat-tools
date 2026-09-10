package com.custom.browser;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int CONTEXT_MENU_DOWNLOAD = 1001;
    private WebView webView;
    private EditText urlInput;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setWindowAnimations(0);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        urlInput = findViewById(R.id.url_input);
        progressBar = findViewById(R.id.page_progress);

        ImageButton goButton = findViewById(R.id.go_button);
        ImageButton clearButton = findViewById(R.id.clear_button);
        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnForward = findViewById(R.id.btn_forward);
        ImageButton btnRefresh = findViewById(R.id.btn_refresh);

        registerForContextMenu(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                urlInput.setText(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (isDownloadableFile(url)) {
                    triggerCustomDownloader(url, null, null, null, 0);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                urlInput.setText(url);
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                triggerCustomDownloader(url, userAgent, contentDisposition, mimetype, contentLength);
            }
        });

        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });

        btnForward.setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });

        btnRefresh.setOnClickListener(v -> webView.reload());

        clearButton.setOnClickListener(v -> urlInput.setText(""));
        goButton.setOnClickListener(v -> loadFromInput());

        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                loadFromInput();
                return true;
            }
            return false;
        });

        webView.loadUrl("https://html5test.com");
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        WebView.HitTestResult result = webView.getHitTestResult();

        int type = result.getType();
        if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            menu.setHeaderTitle(result.getExtra());
            menu.add(0, CONTEXT_MENU_DOWNLOAD, 0, "Download");
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == CONTEXT_MENU_DOWNLOAD) {
            WebView.HitTestResult result = webView.getHitTestResult();
            String url = result.getExtra();
            if (url != null) {
                triggerCustomDownloader(url, null, null, null, 0);
                return true;
            }
        }
        return super.onContextItemSelected(item);
    }

    private void loadFromInput() {
        String url = urlInput.getText().toString().trim();
        if (!url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        hideKeyboard();
        webView.loadUrl(url);
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private boolean isDownloadableFile(String url) {
        if (url == null) return false;
        String cleanUrl = url.toLowerCase().split("\\?")[0];

        if (cleanUrl.endsWith(".html") || cleanUrl.endsWith(".htm")
                || cleanUrl.endsWith(".php") || cleanUrl.endsWith(".asp")
                || cleanUrl.endsWith(".aspx") || cleanUrl.endsWith(".jsp")
                || cleanUrl.endsWith(".shtml") || cleanUrl.endsWith("/")) {
            return false;
        }

        int lastSlash = cleanUrl.lastIndexOf('/');
        int lastDot = cleanUrl.lastIndexOf('.');
        return lastDot > lastSlash;
    }

    private void triggerCustomDownloader(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(url), mimetype != null ? mimetype : "*/*");
        intent.putExtra("EXTRA_USER_AGENT", userAgent);
        intent.putExtra("EXTRA_CONTENT_DISPOSITION", contentDisposition);
        startActivity(Intent.createChooser(intent, "Download via..."));
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.overridePendingTransition(0, 0);
            super.onBackPressed();
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
