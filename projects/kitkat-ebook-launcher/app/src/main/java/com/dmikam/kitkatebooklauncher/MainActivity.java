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
import android.net.Uri;
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
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.text.SimpleDateFormat;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        setContentView(R.layout.activity_main);
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

        // Single tap → confirm then open in last used reader
        lvBooks.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final BookItem item = booksAdapter.getItem(position);
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("Open \"" + item.getTitle() + "\"?")
                        .setPositiveButton("Open", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                openBookInLastReader(item);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        // Long press → details dialog
        lvBooks.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                BookItem item = booksAdapter.getItem(position);
                showBookDetailsDialog(item, position);
                return true;
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
                    String[] projection = {"_id","Title","Authors","Progress",
                            "Location","Type","MD5","LastAccess","Favorite","Tags","Series"};
                    cursor = getContentResolver().query(
                            CMS_METADATA_URI, projection,
                            "Title IS NOT NULL AND Location IS NOT NULL",
                            null, "LastAccess DESC LIMIT 100");

                    if (cursor != null && cursor.moveToFirst()) {
                        int colId       = cursor.getColumnIndex("_id");
                        int colTitle    = cursor.getColumnIndex("Title");
                        int colAuthor   = cursor.getColumnIndex("Authors");
                        int colProgress = cursor.getColumnIndex("Progress");
                        int colLocation = cursor.getColumnIndex("Location");
                        int colType     = cursor.getColumnIndex("Type");
                        int colMd5      = cursor.getColumnIndex("MD5");
                        int colAccess   = cursor.getColumnIndex("LastAccess");
                        int colFav      = cursor.getColumnIndex("Favorite");
                        int colTags     = cursor.getColumnIndex("Tags");
                        int colSeries   = cursor.getColumnIndex("Series");

                        do {
                            String title = cursor.getString(colTitle);
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
                                    tags, series, favorite
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
                }
            }
        }.execute();
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

        long ts = item.getLastAccess();
        String lastAccessStr = ts > 0
                ? new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(ts * 1000L))
                : "—";
        ((TextView) dialogView.findViewById(R.id.tv_detail_last_access))
                .setText("Last opened: " + lastAccessStr);

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
                    loadCoverAsync(item.getMd5(), ivCover);
                }
            }
        }.execute();
    }

    /** Load cover thumbnail lazily, downsample to avoid heap pressure. */
    private void loadCoverAsync(final String md5, final ImageView ivCover) {
        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... voids) {
                Cursor c = null;
                try {
                    c = getContentResolver().query(
                            CMS_THUMBNAIL_URI,
                            new String[]{"Thumbnail"},
                            "MD5 = ?",
                            new String[]{md5},
                            null);
                    if (c != null && c.moveToFirst()) {
                        byte[] blob = c.getBlob(0);
                        if (blob != null && blob.length > 0) {
                            // Decode with inSampleSize=2 to halve dimensions and memory
                            BitmapFactory.Options opts = new BitmapFactory.Options();
                            opts.inSampleSize = 2;
                            return BitmapFactory.decodeByteArray(blob, 0, blob.length, opts);
                        }
                    }
                } catch (Exception ignore) {
                } finally {
                    if (c != null) c.close();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Bitmap bmp) {
                if (bmp != null) {
                    ivCover.setImageBitmap(bmp);
                    ivCover.setVisibility(View.VISIBLE);
                }
            }
        }.execute();
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
}
