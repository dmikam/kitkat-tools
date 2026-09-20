package com.dmikam.kitkatebooklauncher;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.util.Base64;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.RatingBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import java.io.File;
import java.text.SimpleDateFormat;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final Uri CMS_METADATA_URI =
            Uri.parse("content://com.onyx.android.sdk.OnyxCmsProvider/library_metadata");
    private static final Uri CMS_HISTORY_URI =
            Uri.parse("content://com.onyx.android.sdk.OnyxCmsProvider/library_history");
    private static final Uri CMS_THUMBNAIL_URI =
            Uri.parse("content://com.onyx.android.sdk.OnyxCmsProvider/library_thumbnail");

    /** Map reader package name → friendly display name */
    private static final Map<String, String> READER_NAMES = new HashMap<>();
    static {
        READER_NAMES.put("com.onyx.reader",        "Onyx Neo Reader");
        READER_NAMES.put("com.neverland.oreader",  "AlReader");
        READER_NAMES.put("com.aldiko.android",     "Aldiko");
        READER_NAMES.put("com.fbreader",           "FBReader");
        READER_NAMES.put("org.geometerplus.zlibrary.ui.android", "FBReader");
    }

    static long normalizeLastAccessTimestamp(long rawValue) {
        if (rawValue <= 0L) {
            return 0L;
        }

        final long unixEpochStartMillis = 946684800000L;  // 2000-01-01
        final long unixEpochEndMillis = 4102444800000L;   // 2100-01-01

        if (rawValue >= unixEpochStartMillis && rawValue <= unixEpochEndMillis) {
            return rawValue;
        }

        if (rawValue >= 946684800L && rawValue <= 4102444800L) {
            return rawValue * 1000L;
        }

        return 0L;
    }

    private ImageButton btnRefresh;
    private Button tabBooks;
    private Button tabApps;
    private ImageButton btnFileManager;
    private ImageButton btnBrowser;
    private View containerBooks;
    private View containerApps;
    private ListView lvBooks;
    private GridView gvApps;
    private TextView tvEmptyBooks;
    private TextView tvEmptyApps;

    private BooksAdapter booksAdapter;
    private AppsAdapter appsAdapter;

    private boolean isAppsTabVisible = false;
    private android.content.SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        setContentView(R.layout.activity_main);
        prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        initViews();
        setupListeners();

        booksAdapter = new BooksAdapter(this);
        booksAdapter.setFavoriteToggleListener(new BooksAdapter.FavoriteToggleListener() {
            @Override
            public void onFavoriteToggle(BookItem item, int position) {
                confirmFavoriteToggle(item, position);
            }
        });
        lvBooks.setAdapter(booksAdapter);

        appsAdapter = new AppsAdapter(this);
        gvApps.setAdapter(appsAdapter);

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        overridePendingTransition(0, 0);
        loadBooks();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // View init
    // ──────────────────────────────────────────────────────────────────────────

    private void initViews() {
        btnRefresh    = findViewById(R.id.btn_refresh_icon);
        tabBooks      = findViewById(R.id.tab_books);
        tabApps       = findViewById(R.id.tab_apps);
        btnFileManager = findViewById(R.id.btn_file_manager);
        btnBrowser     = findViewById(R.id.btn_browser);
        containerBooks = findViewById(R.id.container_books);
        containerApps  = findViewById(R.id.container_apps);
        lvBooks       = findViewById(R.id.lv_books);
        gvApps        = findViewById(R.id.gv_apps);
        tvEmptyBooks  = findViewById(R.id.tv_empty_books);
        tvEmptyApps   = findViewById(R.id.tv_empty_apps);
    }

    private void setupListeners() {
        tabBooks.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showBooksTab(); }
        });
        tabApps.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAppsTab(); }
        });
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                loadBooks();
                loadApps();
            }
        });

        btnFileManager.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // Try to open a known StorageActivity, fallback to a generic file picker
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.parse("file:///storage/emulated/0/"), "*/*");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    try {
                        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                        pick.setType("*/*");
                        pick.addCategory(Intent.CATEGORY_OPENABLE);
                        pick.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(Intent.createChooser(pick, "Open file"));
                    } catch (ActivityNotFoundException ex) {
                        Toast.makeText(MainActivity.this, "No file manager available", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        btnBrowser.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("about:blank"));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "No browser available", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Single tap → details dialog
        lvBooks.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BookItem item = booksAdapter.getItem(position);
                showBookDetailsDialog(item, position);
            }
        });

        gvApps.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openApp(appsAdapter.getItem(position));
            }
        });
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tab switching
    // ──────────────────────────────────────────────────────────────────────────

    private void showBooksTab() {
        isAppsTabVisible = false;
        containerBooks.setVisibility(View.VISIBLE);
        containerApps.setVisibility(View.GONE);
        tabBooks.setBackgroundColor(getResources().getColor(R.color.black));
        tabBooks.setTextColor(getResources().getColor(R.color.white));
        tabApps.setBackgroundColor(getResources().getColor(R.color.white));
        tabApps.setTextColor(getResources().getColor(R.color.black));
    }

    private void showAppsTab() {
        isAppsTabVisible = true;
        containerBooks.setVisibility(View.GONE);
        containerApps.setVisibility(View.VISIBLE);
        tabBooks.setBackgroundColor(getResources().getColor(R.color.white));
        tabBooks.setTextColor(getResources().getColor(R.color.black));
        tabApps.setBackgroundColor(getResources().getColor(R.color.black));
        tabApps.setTextColor(getResources().getColor(R.color.white));
    }

    

    // ──────────────────────────────────────────────────────────────────────────
    // Data loading
    // ──────────────────────────────────────────────────────────────────────────

    private void loadBooks() {
        new AsyncTask<Void, Void, List<BookItem>>() {
            @Override
            protected List<BookItem> doInBackground(Void... voids) {
                List<BookItem> list = new ArrayList<>();
                Cursor cursor = null;
                try {
                        String[] projection = {"_id","Title","Name","Authors","Progress",
                            "Location","Type","MD5","LastAccess","Favorite","Tags","Series","Rating"};
                        cursor = getContentResolver().query(
                            CMS_METADATA_URI, projection,
                            "Location IS NOT NULL",
                            null, "LastAccess DESC LIMIT 100");

                    if (cursor != null && cursor.moveToFirst()) {
                        int colId       = cursor.getColumnIndex("_id");
                        int colTitle    = cursor.getColumnIndex("Title");
                        int colName     = cursor.getColumnIndex("Name");
                        int colAuthor   = cursor.getColumnIndex("Authors");
                        int colProgress = cursor.getColumnIndex("Progress");
                        int colLocation = cursor.getColumnIndex("Location");
                        int colType     = cursor.getColumnIndex("Type");
                        int colMd5      = cursor.getColumnIndex("MD5");
                        int colAccess   = cursor.getColumnIndex("LastAccess");
                        int colFav      = cursor.getColumnIndex("Favorite");
                        int colTags     = cursor.getColumnIndex("Tags");
                        int colSeries   = cursor.getColumnIndex("Series");
                        int colRating   = cursor.getColumnIndex("Rating");

                        do {
                            String title = cursor.isNull(colTitle) ? null : cursor.getString(colTitle);
                            if (TextUtils.isEmpty(title) || "NULL".equalsIgnoreCase(title)) {
                                // Try fallback fields: Name column, then filename from Location
                                if (colName >= 0 && !cursor.isNull(colName)) {
                                    title = cursor.getString(colName);
                                }
                            }
                            if (TextUtils.isEmpty(title) || "NULL".equalsIgnoreCase(title)) {
                                String loc = cursor.isNull(colLocation) ? null : cursor.getString(colLocation);
                                if (!TextUtils.isEmpty(loc)) {
                                    title = new File(loc).getName();
                                }
                            }
                            if (TextUtils.isEmpty(title) || "NULL".equalsIgnoreCase(title)) continue;

                            String progress = cursor.isNull(colProgress) ? null : cursor.getString(colProgress);
                            int currentPage = 0, totalPages = 0;
                            if (!TextUtils.isEmpty(progress) && progress.contains("/")) {
                                String[] parts = progress.split("/");
                                try {
                                    currentPage = Integer.parseInt(parts[0].trim());
                                    totalPages  = Integer.parseInt(parts[1].trim());
                                } catch (NumberFormatException ignore) {}
                            }

                                boolean favorite = !cursor.isNull(colFav) && cursor.getInt(colFav) == 1;
                                String tags   = cursor.isNull(colTags)   ? null : cursor.getString(colTags);
                                String series = cursor.isNull(colSeries) ? null : cursor.getString(colSeries);
                                int rating = (colRating >= 0 && !cursor.isNull(colRating)) ? cursor.getInt(colRating) : 0;

                                list.add(new BookItem(
                                    cursor.getLong(colId),
                                    title,
                                    cursor.isNull(colAuthor)   ? null : cursor.getString(colAuthor),
                                    progress,
                                    currentPage, totalPages,
                                    cursor.getString(colLocation),
                                    cursor.isNull(colType)     ? null : cursor.getString(colType),
                                    cursor.getString(colMd5),
                                    cursor.getLong(colAccess),
                                    tags, series, rating, favorite
                                ));
                        } while (cursor.moveToNext());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (cursor != null) cursor.close();
                }
                return list;
            }

            @Override
            protected void onPostExecute(List<BookItem> books) {
                if (books.isEmpty()) {
                    tvEmptyBooks.setVisibility(View.VISIBLE);
                    lvBooks.setVisibility(View.GONE);
                } else {
                    tvEmptyBooks.setVisibility(View.GONE);
                    lvBooks.setVisibility(View.VISIBLE);
                    booksAdapter.setData(books);
                    // If auto-load is enabled, prefetch covers in background for books without cache
                    boolean auto = prefs.getBoolean("pref_auto_load_covers", false);
                    if (auto) prefetchCovers(books);
                }
            }
        }.execute();
    }

    /** Background prefetch: attempt to extract covers and populate cache for books lacking one. */
    private void prefetchCovers(final List<BookItem> books) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                // lower thread priority to minimize impact on UI
                try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); } catch (Exception ignore) {}
                for (BookItem item : books) {
                    if (isCancelled()) break;
                    try {
                        File cache = getCoverCacheFile(item);
                        File marker = getNoCoverMarkerFile(item);
                        if ((cache != null && cache.exists()) || (marker != null && marker.exists())) continue;
                        if (TextUtils.isEmpty(item.getLocation())) {
                            if (marker != null) marker.createNewFile();
                            continue;
                        }
                        Bitmap bm = null;
                        try {
                            bm = loadEmbeddedCoverFromFile(new File(item.getLocation()));
                        } catch (Exception ignore) {}
                        if (isCancelled()) break;
                        if (bm != null) {
                            try {
                                if (cache != null) {
                                    File dir = cache.getParentFile(); if (dir!=null && !dir.exists()) dir.mkdirs();
                                    java.io.FileOutputStream fos = new java.io.FileOutputStream(cache);
                                    bm.compress(Bitmap.CompressFormat.PNG, 90, fos);
                                    fos.close();
                                }
                            } catch (Exception ignore) {}
                        } else {
                            try { if (marker != null) marker.createNewFile(); } catch (Exception ignore) {}
                        }
                    } catch (Exception ignore) {}
                    // throttle: short sleep between items to reduce IO contention
                    try { Thread.sleep(250); } catch (InterruptedException ie) { break; }
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    private void loadApps() {
        new AsyncTask<Void, Void, List<AppItem>>() {
            @Override
            protected List<AppItem> doInBackground(Void... voids) {
                List<AppItem> list = new ArrayList<>();
                PackageManager pm = getPackageManager();
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);
                if (resolved != null) {
                    for (ResolveInfo ri : resolved) {
                        if (getPackageName().equals(ri.activityInfo.packageName)) continue;
                        list.add(new AppItem(ri.activityInfo.packageName,
                                ri.activityInfo.name,
                                ri.loadLabel(pm).toString(),
                                ri.loadIcon(pm)));
                    }
                }
                Collections.sort(list, new Comparator<AppItem>() {
                    @Override public int compare(AppItem a, AppItem b) {
                        return a.getLabel().compareToIgnoreCase(b.getLabel());
                    }
                });
                return list;
            }

            @Override
            protected void onPostExecute(List<AppItem> apps) {
                if (apps.isEmpty()) {
                    tvEmptyApps.setVisibility(View.VISIBLE);
                    gvApps.setVisibility(View.GONE);
                } else {
                    tvEmptyApps.setVisibility(View.GONE);
                    gvApps.setVisibility(View.VISIBLE);
                    appsAdapter.setData(apps);
                }
            }
        }.execute();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Opening books
    // ──────────────────────────────────────────────────────────────────────────

    /** Opens a book directly in the reader app it was last used with (no chooser dialog). */
    private void openBookInLastReader(final BookItem item) {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                return queryLastReaderPackage(item.getMd5());
            }

            @Override
            protected void onPostExecute(String readerPackage) {
                openBookWithPackage(item, readerPackage);
            }
        }.execute();
    }

    /** Query library_history for the most recently used reader package for this MD5. */
    private String queryLastReaderPackage(String md5) {
        Cursor c = null;
        try {
            c = getContentResolver().query(
                    CMS_HISTORY_URI,
                    new String[]{"Application"},
                    "MD5 = ?",
                    new String[]{md5},
                    "EndTime DESC LIMIT 1");
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Exception ignore) {
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    private void openBookWithPackage(BookItem item, String readerPackage) {
        File file = new File(item.getLocation());
        if (!file.exists()) {
            Toast.makeText(this, "File not found: " + file.getName(), Toast.LENGTH_SHORT).show();
            return;
        }

        String mime = getMimeType(item.getLocation());
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(file), mime);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (!TextUtils.isEmpty(readerPackage)) {
            intent.setPackage(readerPackage);
        }

        try {
            startActivity(intent);
            overridePendingTransition(0, 0);
        } catch (ActivityNotFoundException e) {
            // Fall back to chooser
            openBookWithChooser(item);
        }
    }

    /** Opens the system app chooser for a book. */
    private void openBookWithChooser(BookItem item) {
        File file = new File(item.getLocation());
        if (!file.exists()) {
            Toast.makeText(this, "File not found: " + file.getName(), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(file), getMimeType(item.getLocation()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(Intent.createChooser(intent, "Open with…"));
            overridePendingTransition(0, 0);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No reader app found", Toast.LENGTH_LONG).show();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Favorites
    // ──────────────────────────────────────────────────────────────────────────

    private void confirmFavoriteToggle(final BookItem item, final int position) {
        boolean willFavorite = !item.isFavorite();
        String msg = willFavorite
                ? "Mark \"" + item.getTitle() + "\" as favorite?"
                : "Remove \"" + item.getTitle() + "\" from favorites?";

        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setFavorite(item, position, !item.isFavorite());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setFavorite(final BookItem item, final int position, final boolean favorite) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    ContentValues values = new ContentValues();
                    values.put("Favorite", favorite ? 1 : 0);
                    int rows = getContentResolver().update(
                            CMS_METADATA_URI, values,
                            "MD5 = ?", new String[]{item.getMd5()});
                    return rows > 0;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    item.setFavorite(favorite);
                    booksAdapter.updateFavorite(position, favorite);
                } else {
                    Toast.makeText(MainActivity.this,
                            "Could not update favorite status", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void setRating(final BookItem item, final int position, final int rating) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    ContentValues values = new ContentValues();
                    values.put("Rating", rating);
                    int rows = getContentResolver().update(
                            CMS_METADATA_URI, values,
                            "MD5 = ?", new String[]{item.getMd5()});
                    return rows > 0;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    item.setRating(rating);
                    Toast.makeText(MainActivity.this,
                            "Rating updated", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this,
                            "Could not update rating", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Long-press details dialog
    // ──────────────────────────────────────────────────────────────────────────

    private void showBookDetailsDialog(final BookItem item, final int position) {
        // Inflate dialog view
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_book_details, null);

        // Populate basic metadata
        ((TextView) dialogView.findViewById(R.id.tv_detail_title)).setText(item.getTitle());
        ((TextView) dialogView.findViewById(R.id.tv_detail_author))
                .setText(TextUtils.isEmpty(item.getAuthor()) ? getString(R.string.unknown_author) : item.getAuthor());

        String fmt = item.getType();
        ((TextView) dialogView.findViewById(R.id.tv_detail_format))
                .setText("Format: " + (TextUtils.isEmpty(fmt) ? "?" : fmt.toUpperCase(Locale.US)));

        String progress = item.getProgress();
        ((TextView) dialogView.findViewById(R.id.tv_detail_progress))
                .setText("Progress: " + (TextUtils.isEmpty(progress) ? "—" : progress));

        long ts = normalizeLastAccessTimestamp(item.getLastAccess());
        String lastAccessStr = ts > 0
                ? new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    .format(new Date(ts))
                : "—";
        TextView lastAccessView = dialogView.findViewById(R.id.tv_detail_last_access);
        if (ts <= 0L || item.getLastAccess() < 0L) {
            lastAccessView.setText("Last opened: —");
        } else {
            lastAccessView.setText("Last opened: " + lastAccessStr);
        }

        // Series & tags
        TextView tvSeries = dialogView.findViewById(R.id.tv_detail_series);
        if (!TextUtils.isEmpty(item.getSeries())) {
            tvSeries.setText("Series: " + item.getSeries());
            tvSeries.setVisibility(View.VISIBLE);
        } else {
            tvSeries.setVisibility(View.GONE);
        }

        TextView tvTags = dialogView.findViewById(R.id.tv_detail_tags);
        if (!TextUtils.isEmpty(item.getTags())) {
            tvTags.setText("Tags: " + item.getTags());
            tvTags.setVisibility(View.VISIBLE);
        } else {
            tvTags.setVisibility(View.GONE);
        }

        // Favorite icon state (heart glyph) — match list behavior
        final android.widget.TextView btnFavIcon = dialogView.findViewById(R.id.btn_toggle_favorite_icon);
        btnFavIcon.setText(item.isFavorite() ? "\u2665" : "\u2661");

        // Rating bar — allow 1..5 stars, confirmation on change
        final RatingBar ratingBar = dialogView.findViewById(R.id.rating_bar);
        final int[] prevRating = new int[] { item.getRating() };
        ratingBar.setNumStars(5);
        ratingBar.setStepSize(1f);
        ratingBar.setRating(prevRating[0]);
        ratingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(final RatingBar rb, final float rating, boolean fromUser) {
                if (!fromUser) return;
                final int newRating = (int) rating;
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("Set rating to " + newRating + " star(s)?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialogInterface, int which) {
                                setRating(item, position, newRating);
                                prevRating[0] = newRating;
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialogInterface, int which) {
                                rb.setRating(prevRating[0]);
                            }
                        }).show();
            }
        });

        // Reader label — will be filled async
        final TextView tvReader = dialogView.findViewById(R.id.tv_detail_reader);
        tvReader.setText("Last reader: …");

        // Cover image — loaded async, shown only if not null
        final ImageView ivCover = dialogView.findViewById(R.id.iv_cover);

        // Build dialog first so it can be dismissed by actions inside
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(dialogView);

        final AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(scrollView)
            .create();

        // Close icon
        dialogView.findViewById(R.id.btn_close_dialog).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        // Action buttons
        dialogView.findViewById(R.id.btn_read).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                openBookInLastReader(item);
            }
        });

        dialogView.findViewById(R.id.btn_open_with).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                openBookWithChooser(item);
            }
        });

        btnFavIcon.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                confirmFavoriteToggle(item, position);
            }
        });

        dialogView.findViewById(R.id.btn_delete_icon).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                confirmDeleteBook(item, position);
            }
        });

        dialog.show();

        // Async: load last reader name + cover
        new AsyncTask<Void, Void, String[]>() {
            @Override
            protected String[] doInBackground(Void... voids) {
                String pkg = queryLastReaderPackage(item.getMd5());
                String friendly = pkg != null
                        ? (READER_NAMES.containsKey(pkg) ? READER_NAMES.get(pkg) : pkg)
                        : "—";
                return new String[]{friendly};
            }

            @Override
            protected void onPostExecute(String[] result) {
                tvReader.setText("Last reader: " + result[0]);
                // Load cover only if dialog is still shown
                if (dialog.isShowing()) {
                    loadCoverAsync(item, ivCover);
                }
            }
        }.execute();
    }

    /** Try cached cover, embedded cover in ebook files, otherwise generate a placeholder. */
    private void loadCoverAsync(final BookItem item, final ImageView ivCover) {
        // Show placeholder and spinner immediately
        final View root = ivCover.getRootView();
        final ProgressBar pb = root.findViewById(R.id.pb_cover_loading);
        final Bitmap placeholder = generatePlaceholderCover(item.getTitle());
        ivCover.setImageBitmap(placeholder);
        ivCover.setVisibility(View.VISIBLE);
        if (pb != null) pb.setVisibility(View.VISIBLE);

        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... voids) {
                try {
                    File markerFile = getNoCoverMarkerFile(item);
                    if (markerFile != null && markerFile.exists()) {
                        return null;
                    }
                    // 1) Try disk cache
                    File cache = getCoverCacheFile(item);
                    if (cache != null && cache.exists()) {
                        Bitmap cached = BitmapFactory.decodeFile(cache.getAbsolutePath());
                        if (cached != null) return cached;
                    }

                    // 2) Try embedded cover
                    if (!TextUtils.isEmpty(item.getLocation())) {
                        Bitmap embedded = loadEmbeddedCoverFromFile(new File(item.getLocation()));
                        if (embedded != null) {
                            // Save to cache (best-effort)
                            try {
                                if (cache != null) {
                                    File dir = cache.getParentFile();
                                    if (dir != null && !dir.exists()) dir.mkdirs();
                                    java.io.FileOutputStream fos = new java.io.FileOutputStream(cache);
                                    embedded.compress(Bitmap.CompressFormat.PNG, 90, fos);
                                    fos.close();
                                }
                            } catch (Exception ignore) {}
                            return embedded;
                        }
                    }
                } catch (Exception ignore) {}
                // mark as no cover to avoid repeated attempts
                try { File marker = getNoCoverMarkerFile(item); if (marker!=null) marker.createNewFile(); } catch (Exception ignored) {}
                return null; // keep placeholder
            }

            @Override
            protected void onPostExecute(Bitmap bmp) {
                if (pb != null) pb.setVisibility(View.GONE);
                if (bmp != null) {
                    ivCover.setImageBitmap(bmp);
                    ivCover.setVisibility(View.VISIBLE);
                } else {
                    // leave placeholder shown
                    ivCover.setVisibility(View.VISIBLE);
                }
            }
        }.execute();
    }

    private File getCoverCacheFile(BookItem item) {
        try {
            String key = item.getMd5();
            if (TextUtils.isEmpty(key)) {
                String loc = item.getLocation();
                key = Integer.toHexString(loc == null ? 0 : loc.hashCode());
            }
            String safe = "cover_" + key + ".png";
            File dir = getCacheDir();
            return new File(dir, safe);
        } catch (Exception e) {
            return null;
        }
    }

    private File getNoCoverMarkerFile(BookItem item) {
        try {
            String key = item.getMd5();
            if (TextUtils.isEmpty(key)) {
                String loc = item.getLocation();
                key = Integer.toHexString(loc == null ? 0 : loc.hashCode());
            }
            String safe = "no_cover_" + key + ".marker";
            File dir = getCacheDir();
            return new File(dir, safe);
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap loadEmbeddedCoverFromFile(File file) {
        if (file == null || !file.exists()) return null;
        String path = file.getAbsolutePath().toLowerCase(Locale.US);
        try {
            if (path.endsWith(".epub")) {
                return extractCoverFromEpub(file);
            }
            if (path.endsWith(".fb2")) {
                return extractCoverFromFb2(file);
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private Bitmap extractCoverFromEpub(File file) throws Exception {
        java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file);
        try {
            String opfPath = findEpubOpfPath(zip);
            if (opfPath != null) {
                String coverHref = findEpubCoverHref(zip, opfPath);
                if (coverHref != null) {
                    java.util.zip.ZipEntry coverEntry = zip.getEntry(coverHref);
                    if (coverEntry != null) {
                        Bitmap bm = decodeImageFromZipEntry(zip, coverEntry);
                        if (bm != null) return bm;
                    }
                }
            }

            java.util.ArrayList<java.util.zip.ZipEntry> candidates = new java.util.ArrayList<java.util.zip.ZipEntry>();
            java.util.Enumeration<? extends java.util.zip.ZipEntry> allEntries = zip.entries();
            while (allEntries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = allEntries.nextElement();
                String name = entry.getName().toLowerCase(Locale.US);
                if (isImageFile(name) && looksLikeCoverName(name)) {
                    candidates.add(entry);
                }
            }
            for (java.util.zip.ZipEntry entry : candidates) {
                Bitmap bm = decodeImageFromZipEntry(zip, entry);
                if (bm != null) return bm;
            }

            allEntries = zip.entries();
            while (allEntries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = allEntries.nextElement();
                String name = entry.getName().toLowerCase(Locale.US);
                if (isImageFile(name) && !isObviousNonCoverName(name)) {
                    Bitmap bm = decodeImageFromZipEntry(zip, entry);
                    if (bm != null) return bm;
                }
            }
            return null;
        } finally {
            zip.close();
        }
    }

    private String findEpubOpfPath(java.util.zip.ZipFile zip) throws Exception {
        java.util.zip.ZipEntry containerEntry = zip.getEntry("META-INF/container.xml");
        if (containerEntry == null) return null;

        byte[] bytes = readZipEntryBytes(zip, containerEntry);
        String xml = new String(bytes, "UTF-8");
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "<rootfile[^>]*full-path=['\"]([^'\"]+)['\"][^>]*>",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1).replace('\\', '/');
        }
        return null;
    }

    private String findEpubCoverHref(java.util.zip.ZipFile zip, String opfPath) throws Exception {
        java.util.zip.ZipEntry opfEntry = zip.getEntry(opfPath);
        if (opfEntry == null) return null;

        byte[] bytes = readZipEntryBytes(zip, opfEntry);
        String xml = new String(bytes, "UTF-8");

        java.util.regex.Pattern coverPattern = java.util.regex.Pattern.compile(
                "<item[^>]*\b(?:id=['\"]cover['\"]|properties=['\"][^'\"]*cover-image[^'\"]*['\"]) [^>]*\bhref=['\"]([^'\"]+)['\"][^>]*>",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = coverPattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1).replace('\\', '/');
        }

        java.util.regex.Pattern fallbackPattern = java.util.regex.Pattern.compile(
                "<item[^>]*\bhref=['\"]([^'\"]+)['\"][^>]*>",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
        matcher = fallbackPattern.matcher(xml);
        while (matcher.find()) {
            String href = matcher.group(1).replace('\\', '/');
            String lower = href.toLowerCase(Locale.US);
            if (looksLikeCoverName(lower) && !isObviousNonCoverName(lower)) {
                return href;
            }
        }
        return null;
    }

    private boolean isImageFile(String name) {
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp");
    }

    private boolean looksLikeCoverName(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.contains("logo") || lower.contains("author") || lower.contains("portrait")
                || lower.contains("photo") || lower.contains("editorial") || lower.contains("publisher")
                || lower.contains("branding") || lower.contains("sponsor") || lower.contains("banner")) {
            return false;
        }
        return lower.contains("cover") || lower.contains("titlepage")
                || lower.contains("front") || lower.contains("thumbnail")
                || lower.contains("book") || lower.contains("page");
    }

    private boolean isObviousNonCoverName(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.contains("logo") || lower.contains("author") || lower.contains("portrait")
                || lower.contains("photo") || lower.contains("editorial") || lower.contains("publisher")
                || lower.contains("branding") || lower.contains("sponsor") || lower.contains("banner")
                || lower.contains("staff") || lower.contains("team");
    }

    private Bitmap decodeImageFromZipEntry(java.util.zip.ZipFile zip, java.util.zip.ZipEntry entry) throws Exception {
        java.io.InputStream is = null;
        try {
            // First pass: read bounds
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            is = zip.getInputStream(entry);
            BitmapFactory.decodeStream(is, null, options);
            try { is.close(); } catch (Exception ignore) {}

            // Compute sample size to limit memory; target max dimension ~800px
            int req = 800;
            options.inSampleSize = calculateInSampleSize(options, req, req);
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            // Second pass: decode with sample size
            is = zip.getInputStream(entry);
            Bitmap bm = BitmapFactory.decodeStream(is, null, options);
            return bm;
        } finally {
            if (is != null) try { is.close(); } catch (Exception ignore) {}
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private Bitmap extractCoverFromFb2(File file) throws Exception {
        FileInputStream stream = null;
        final int MAX_BASE64_CHARS = 2 * 1024 * 1024; // 2 MB of base64 text ~= ~1.5MB binary
        try {
            stream = new FileInputStream(file);
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(stream, "UTF-8");

            String coverId = null;
            boolean readingBinary = false;
            String pendingMime = null;
            StringBuilder pendingData = null;
            boolean inCoverpage = false;

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();

                if (eventType == XmlPullParser.START_TAG) {
                    if ("coverpage".equalsIgnoreCase(tagName)) {
                        inCoverpage = true;
                    } else if ("image".equalsIgnoreCase(tagName)) {
                        if (inCoverpage) {
                            String href = parser.getAttributeValue(null, "href");
                            if (TextUtils.isEmpty(href)) {
                                href = parser.getAttributeValue("http://www.w3.org/1999/xlink", "href");
                            }
                            if (!TextUtils.isEmpty(href)) {
                                String normalized = normalizeFb2Reference(href);
                                if (!TextUtils.isEmpty(normalized)) {
                                    coverId = normalized;
                                }
                            }
                        }
                    } else if ("binary".equalsIgnoreCase(tagName)) {
                        String id = parser.getAttributeValue(null, "id");
                        String mime = parser.getAttributeValue(null, "content-type");
                        if (!TextUtils.isEmpty(mime) && mime.toLowerCase(Locale.US).startsWith("image/")) {
                            if (coverId != null && (coverId.equalsIgnoreCase(id) || coverId.equalsIgnoreCase(normalizeFb2Reference(id)))) {
                                readingBinary = true;
                                pendingMime = mime;
                                pendingData = new StringBuilder();
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    if (readingBinary && pendingData != null) {
                        String txt = parser.getText();
                        if (txt != null) {
                            if (pendingData.length() + txt.length() > MAX_BASE64_CHARS) {
                                // Too large — abort this binary to avoid OOM
                                readingBinary = false;
                                pendingData = null;
                                pendingMime = null;
                            } else {
                                pendingData.append(txt);
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("coverpage".equalsIgnoreCase(tagName)) {
                        inCoverpage = false;
                    }
                    if ("binary".equalsIgnoreCase(tagName) && readingBinary && pendingData != null) {
                        Bitmap bm = decodeBase64Bitmap(pendingData.toString());
                        if (bm != null) return bm;
                        readingBinary = false;
                        pendingData = null;
                        pendingMime = null;
                    }
                }

                eventType = parser.next();
            }

            // Fallback scan for binary names that look like cover assets if no coverpage reference was found.
            stream.close();
            stream = new FileInputStream(file);
            parser = factory.newPullParser();
            parser.setInput(stream, "UTF-8");

            eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName2 = parser.getName();
                if (eventType == XmlPullParser.START_TAG && "binary".equalsIgnoreCase(tagName2)) {
                    String id = parser.getAttributeValue(null, "id");
                    String mime = parser.getAttributeValue(null, "content-type");
                    if (!TextUtils.isEmpty(mime) && mime.toLowerCase(Locale.US).startsWith("image/")) {
                        if (id != null && !isObviousNonCoverName(id) && (looksLikeCoverName(id) || !id.contains("."))) {
                            StringBuilder data = new StringBuilder();
                            int inner = parser.next();
                            boolean aborted = false;
                            while (inner != XmlPullParser.END_DOCUMENT) {
                                if (inner == XmlPullParser.TEXT) {
                                    String t = parser.getText();
                                    if (t != null) {
                                        if (data.length() + t.length() > MAX_BASE64_CHARS) {
                                            aborted = true;
                                            break;
                                        }
                                        data.append(t);
                                    }
                                } else if (inner == XmlPullParser.END_TAG && "binary".equalsIgnoreCase(parser.getName())) {
                                    if (!aborted) {
                                        Bitmap bm = decodeBase64Bitmap(data.toString());
                                        if (bm != null) return bm;
                                    }
                                    break;
                                }
                                inner = parser.next();
                            }
                        }
                    }
                }
                eventType = parser.next();
            }
            return null;
        } finally {
            if (stream != null) stream.close();
        }
    }

    private String normalizeFb2Reference(String ref) {
        if (TextUtils.isEmpty(ref)) return null;
        String normalized = ref.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        int hashIndex = normalized.indexOf('#');
        if (hashIndex >= 0) {
            normalized = normalized.substring(hashIndex + 1);
        }
        return normalized;
    }

    private Bitmap decodeBase64Bitmap(String base64) {
        if (TextUtils.isEmpty(base64)) return null;
        try {
            byte[] imageBytes = Base64.decode(base64.replaceAll("\\s+", ""), Base64.DEFAULT);
            if (imageBytes == null || imageBytes.length == 0) return null;
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        } catch (Exception ignore) {
            return null;
        }
    }

    private String extractAttribute(String attrs, String attributeName) {
        if (TextUtils.isEmpty(attrs)) return null;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                attributeName + "=['\"]([^'\"]+)['\"]",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(attrs);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private byte[] readZipEntryBytes(java.util.zip.ZipFile zip, java.util.zip.ZipEntry entry) throws Exception {
        java.io.InputStream is = null;
        try {
            is = zip.getInputStream(entry);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toByteArray();
        } finally {
            if (is != null) is.close();
        }
    }

    private byte[] readFileBytes(File file) throws Exception {
        java.io.InputStream is = null;
        try {
            is = new java.io.FileInputStream(file);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toByteArray();
        } finally {
            if (is != null) is.close();
        }
    }

    private Bitmap generatePlaceholderCover(String title) {
        int width = 240;
        int height = 320;
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        int[] palette = new int[] {
                Color.parseColor("#3F51B5"),
                Color.parseColor("#009688"),
                Color.parseColor("#673AB7"),
                Color.parseColor("#E91E63"),
                Color.parseColor("#FF9800")
        };
        int color = palette[Math.abs((title == null ? 0 : title.hashCode())) % palette.length];

        Paint bg = new Paint();
        bg.setColor(color);
        canvas.drawRect(0, 0, width, height, bg);

        Paint overlay = new Paint();
        overlay.setColor(0x66000000);
        canvas.drawRect(0, 0, width, height, overlay);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(52f);
        textPaint.setFakeBoldText(true);

        String initials = "B";
        if (!TextUtils.isEmpty(title)) {
            String cleaned = title.trim();
            String[] parts = cleaned.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                if (part.length() > 0) {
                    sb.append(part.substring(0, 1).toUpperCase(Locale.US));
                    if (sb.length() >= 2) break;
                }
            }
            if (sb.length() > 0) initials = sb.toString();
        }

        canvas.drawText(initials.length() > 2 ? initials.substring(0, 2) : initials, width / 2f, height / 2f + 18f, textPaint);
        return bmp;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────────

    private void confirmDeleteBook(final BookItem item, final int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete book")
                .setMessage("Delete \"" + item.getTitle() + "\" from the device?\nThis cannot be undone.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteBook(item, position);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteBook(final BookItem item, final int position) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                        // Delete file from disk
                        File f = new File(item.getLocation());
                        boolean fileDeleted = !f.exists() || f.delete();

                        // Remove from provider
                        getContentResolver().delete(
                            CMS_METADATA_URI, "MD5 = ?", new String[]{item.getMd5()});

                        // Remove cached cover if any
                        try {
                        File cache = getCoverCacheFile(item);
                        if (cache != null && cache.exists()) cache.delete();
                        } catch (Exception ignore) {}

                        return fileDeleted;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    Toast.makeText(MainActivity.this,
                            "\"" + item.getTitle() + "\" deleted.", Toast.LENGTH_SHORT).show();
                    loadBooks(); // full refresh
                } else {
                    Toast.makeText(MainActivity.this,
                            "Delete failed. Check permissions.", Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Apps
    // ──────────────────────────────────────────────────────────────────────────

    private void openApp(AppItem item) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(item.getPackageName());
            if (intent == null) {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setComponent(new ComponentName(item.getPackageName(), item.getActivityName()));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            overridePendingTransition(0, 0);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open " + item.getLabel(), Toast.LENGTH_SHORT).show();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private String getMimeType(String path) {
        String lower = path.toLowerCase(Locale.US);
        if (lower.endsWith(".epub"))   return "application/epub+zip";
        if (lower.endsWith(".fb2"))    return "application/x-fictionbook+xml";
        if (lower.endsWith(".pdf"))    return "application/pdf";
        if (lower.endsWith(".mobi") || lower.endsWith(".prc")) return "application/x-mobipocket-ebook";
        if (lower.endsWith(".txt"))    return "text/plain";
        if (lower.endsWith(".zip"))    return "application/zip";
        if (lower.endsWith(".cbr"))    return "application/x-cbr";
        if (lower.endsWith(".cbz"))    return "application/x-cbz";
        String ext = MimeTypeMap.getFileExtensionFromUrl(path);
        if (ext != null) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
            if (mime != null) return mime;
        }
        return "*/*";
    }

    @Override
    public void onBackPressed() {
        if (isAppsTabVisible) {
            showBooksTab();
        }
        // Do not finish() — launcher stays alive
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        try {
            getMenuInflater().inflate(R.menu.main_menu, menu);
        } catch (Exception ignore) {}
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            try {
                startActivity(new android.content.Intent(this, SettingsActivity.class));
            } catch (Exception ignore) {}
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
