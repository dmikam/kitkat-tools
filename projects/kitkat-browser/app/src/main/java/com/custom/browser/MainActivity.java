package com.dmikam.kitkatbrowser;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import java.net.URLEncoder;
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
import java.util.List;

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

        // Hidden toolbar used so system app-menu opens overflow at top-right
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        ImageButton goButton = findViewById(R.id.go_button);
        ImageButton clearButton = findViewById(R.id.clear_button);
        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnForward = findViewById(R.id.btn_forward);
        ImageButton btnRefresh = findViewById(R.id.btn_refresh);
        ImageButton btnHome = findViewById(R.id.btn_home);


        // BOOKMARKS

        ImageButton btnBookmark = findViewById(R.id.btn_bookmark);

        // Click star to confirm add/remove
        btnBookmark.setOnClickListener(v -> {
            String url = webView.getUrl();
            String title = webView.getTitle();
            if (url == null) return;
            final boolean isBookmarked = BookmarkManager.isBookmarked(MainActivity.this, url);
            String message = isBookmarked ? "Remove this bookmark?" : "Add this bookmark?";
            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("Confirm")
                    .setMessage(message)
                    .setPositiveButton(isBookmarked ? "Remove" : "Add", (dialog, which) -> {
                        if (isBookmarked) {
                            BookmarkManager.removeBookmark(MainActivity.this, url);
                            btnBookmark.setImageResource(android.R.drawable.btn_star_big_off);
                            android.widget.Toast.makeText(MainActivity.this, "Bookmark removed", android.widget.Toast.LENGTH_SHORT).show();
                        } else {
                            BookmarkManager.addBookmark(MainActivity.this, title, url);
                            btnBookmark.setImageResource(android.R.drawable.btn_star_big_on);
                            android.widget.Toast.makeText(MainActivity.this, "Bookmark added", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // Long-press removed — bookmarks are available from the app menu.

        // Update star icon on page finish:
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                urlInput.setText(url);
                boolean bookmarked = BookmarkManager.isBookmarked(MainActivity.this, url);
                btnBookmark.setImageResource(bookmarked ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
            }
        });
        // END BOOKMARKS

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

                hideKeyboard();

                // Immediately update star state when navigating to a new URL
                updateBookmarkIcon(url);
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
                // Re-verify in case of redirects or dynamic title changes
                updateBookmarkIcon(url);

                // Inject E-Ink CSS overrides
                applyEInkOptimizations(view);
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

        btnHome.setOnClickListener(v -> loadHomepage());

        clearButton.setOnClickListener(v -> urlInput.setText(""));
        goButton.setOnClickListener(v -> loadFromInput());

        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                loadFromInput();
                return true;
            }
            return false;
        });

        // If launched via an external VIEW intent, open that URL; otherwise show homepage
        if (!handleIncomingIntent(getIntent())) {
            loadHomepage();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    /**
     * Handle incoming intents (e.g. ACTION_VIEW) that contain a URL to open.
     * Returns true if an URL was loaded.
     */
    private boolean handleIncomingIntent(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        Uri data = intent.getData();
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            String url = data.toString();
            if (url != null && !url.isEmpty()) {
                webView.loadUrl(url);
                urlInput.setText(url);
                return true;
            }
        }
        // fallback: check for common extras
        String extraUrl = intent.getStringExtra("url");
        if (extraUrl != null && !extraUrl.isEmpty()) {
            webView.loadUrl(extraUrl);
            urlInput.setText(extraUrl);
            return true;
        }
        return false;
    }

    private void loadHomepage() {
        List<BookmarkManager.Bookmark> bookmarks = BookmarkManager.getBookmarks(this);
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta name=viewport content=width=device-width,initial-scale=1>");
        sb.append("<style>\n");
        sb.append("  body{font-family:sans-serif;padding:12px;color:#000;background:#fff;}\n");
        sb.append("  h1{font-size:18px;margin:0 0 8px 0;}\n");
        sb.append("  .bm{margin:8px 0;padding:6px;border-bottom:1px solid #ddd;}\n");
        sb.append("  a{color:#000;text-decoration:none;}\n");
        sb.append("  .search-row{display:flex;gap:8px;align-items:center;margin:10px 0 14px 0;}\n");
        sb.append("  input.search{flex:1;padding:10px;border:2px solid #888888!important;background:white;border-radius:8px;-webkit-appearance:none;-moz-appearance:none;appearance:none;}\n");
        sb.append("  button.search-btn{padding:10px 12px;border:2px solid #888888!important;background:#999999!important;border-radius:8px;color:#000;font-weight:600;}\n");
        sb.append("  .bm .title{font-weight:600;margin-bottom:4px;}\n");
        sb.append("  @media (max-width:420px){.search-row{flex-direction:column} button.search-btn{width:100%}}\n");
        sb.append("</style>");
        sb.append("</head><body>");
        sb.append("<h1>Home</h1>");
        sb.append("<form action='https://html.duckduckgo.com/html/' method='get' target='_self' class='search-row'>");
        sb.append("<input name='q' type='search' class='search' placeholder='Search DuckDuckGo' />");
        sb.append("<button type='submit' class='search-btn'>Search</button>");
        sb.append("</form>");

        sb.append("<h2>Bookmarks</h2>");
        if (bookmarks.isEmpty()) {
            sb.append("<p>No saved bookmarks.</p>");
        } else {
            sb.append("<div>");
            for (BookmarkManager.Bookmark b : bookmarks) {
                String title = b.title != null ? escapeHtml(b.title) : escapeHtml(b.url);
                String url = b.url != null ? escapeHtml(b.url) : "";
                sb.append("<div class='bm'><a href='" + url + "'>" + title + "</a><div style='font-size:11px;color:#666'>" + url + "</div></div>");
            }
            sb.append("</div>");
        }

        sb.append("</body></html>");

        webView.loadDataWithBaseURL(null, sb.toString(), "text/html", "utf-8", null);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_bookmarks_open) {
            showBookmarksDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadFromInput() {
        String text = urlInput.getText().toString().trim();
        if (text.isEmpty()) return;

        // Heuristic: if it contains a space or doesn't contain a dot, treat as search query
        boolean looksLikeUrl = text.contains("://") || text.startsWith("www.") || (text.contains(".") && !text.contains(" "));

        hideKeyboard();

        if (!looksLikeUrl) {
            try {
                String q = URLEncoder.encode(text, "UTF-8");
                String searchUrl = "https://html.duckduckgo.com/html/?q=" + q;
                webView.loadUrl(searchUrl);
            } catch (Exception e) {
                webView.loadUrl("https://html.duckduckgo.com/html/?q=" + text);
            }
        } else {
            String url = text;
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }
            webView.loadUrl(url);
        }
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

    private void showBookmarksDialog() {
        final List<BookmarkManager.Bookmark> bookmarks = BookmarkManager.getBookmarks(this);
        if (bookmarks.isEmpty()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Bookmarks")
                    .setMessage("No saved bookmarks.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] titles = new String[bookmarks.size()];
        for (int i = 0; i < bookmarks.size(); i++) {
            titles[i] = bookmarks.get(i).title + "\n" + bookmarks.get(i).url;
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Bookmarks")
                .setItems(titles, (dialog, which) -> {
                    String targetUrl = bookmarks.get(which).url;
                    webView.loadUrl(targetUrl);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void updateBookmarkIcon(String url) {
        ImageButton btnBookmark = findViewById(R.id.btn_bookmark);
        if (btnBookmark != null && url != null) {
            boolean bookmarked = BookmarkManager.isBookmarked(MainActivity.this, url);
            btnBookmark.setImageResource(
                bookmarked ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off
            );
        }
    }



    private void applyEInkOptimizations(WebView view) {
        String eInkCss =
            "/* Disable animations and transitions */ " +
            "* { " +
            "  -webkit-transition: none !important; " +
            "  transition: none !important; " +
            "  -webkit-animation: none !important; " +
            "  animation: none !important; " +
            "  scroll-behavior: auto !important; " +
            "  box-shadow: none !important; " +
            "  text-shadow: none !important; " +
            "} " +
            "/* Bold & Underlined Links */ " +
            "a { " +
            "  font-weight: bold !important; " +
            "  text-decoration: underline !important; " +
            "  color: #000000 !important; " +
            "} " +
            "/* E-Ink Buttons with visible borders */ " +
            "button, input[type='button'], input[type='submit'], input[type='reset'], .btn { " +
            "  background-color: #e0e0e0 !important; " +
            "  background-image: none !important; " +
            "  color: #000000 !important; " +
            "  border: 2px solid #000000 !important; " +
            "  border-radius: 2px !important; " +
            "  font-weight: bold !important; " +
            "  padding: 4px 8px !important; " +
            "}";

        String js = "javascript:(function() { " +
                "var injectTimeout = null; " +
                "var lastExecutionTime = 0; " +
                "var DEBOUNCE_INTERVAL = 2000; " + // Limit execution to once every 2000ms
                "" +
                "function scheduleInjectAndFix() { " +
                "  var now = Date.now(); " +
                "  var timeSinceLast = now - lastExecutionTime; " +
                "  if (timeSinceLast >= DEBOUNCE_INTERVAL) { " +
                "    lastExecutionTime = now; " +
                "    injectAndFix(); " +
                "  } else { " +
                "    if (injectTimeout) clearTimeout(injectTimeout); " +
                "    injectTimeout = setTimeout(function() { " +
                "      lastExecutionTime = Date.now(); " +
                "      injectAndFix(); " +
                "    }, DEBOUNCE_INTERVAL - timeSinceLast); " +
                "  } " +
                "} " +
                "" +
                "function injectAndFix() { " +
                "  var styleId = 'eink-optimization-styles'; " +
                "  var existingStyle = document.getElementById(styleId); " +
                "  if (!existingStyle) { " +
                "    existingStyle = document.createElement('style'); " +
                "    existingStyle.id = styleId; " +
                "    existingStyle.type = 'text/css'; " +
                "    existingStyle.innerHTML = '" + eInkCss + "'; " +
                "    document.head.appendChild(existingStyle); " +
                "  } else { " +
                "    document.head.appendChild(existingStyle); " + // Move to bottom of <head>
                "  } " +
                "  fixContrast(); " +
                "} " +
                "" +
                "function fixContrast() { " +
                "  var elements = document.querySelectorAll('p, span, a, li, h1, h2, h3, h4, h5, h6, td'); " +
                "  for (var i = 0; i < elements.length; i++) { " +
                "    var el = elements[i]; " +
                "    var style = window.getComputedStyle(el); " +
                "    var color = style.color; " +
                "    var bg = style.backgroundColor; " +
                "    if (color && bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)') { " +
                "      var mC = color.match(/\\d+/g); " +
                "      var mB = bg.match(/\\d+/g); " +
                "      if (mC && mB) { " +
                "        var lumC = (parseInt(mC[0])*299 + parseInt(mC[1])*587 + parseInt(mC[2])*114)/1000; " +
                "        var lumB = (parseInt(mB[0])*299 + parseInt(mB[1])*587 + parseInt(mB[2])*114)/1000; " +
                "        if (Math.abs(lumC - lumB) < 40) { " +
                "          el.style.setProperty('color', lumB < 128 ? '#ffffff' : '#000000', 'important'); " +
                "        } " +
                "      } " +
                "    } " +
                "  } " +
                "} " +
                "" +
                "/* Run initial pass immediately */ " +
                "scheduleInjectAndFix(); " +
                "" +
                "/* Attach throttled observer */ " +
                "if (!window.einkObserver) { " +
                "  window.einkObserver = new MutationObserver(function(mutations) { " +
                "    scheduleInjectAndFix(); " +
                "  }); " +
                "  window.einkObserver.observe(document.head, { childList: true, subtree: true }); " +
                "} " +
                "})()";

        view.loadUrl(js);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
