package com.dmikam.kitkatebooklauncher;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final Uri CMS_METADATA_URI = Uri.parse("content://com.onyx.android.sdk.OnyxCmsProvider/library_metadata");

    private TextView tvClock;
    private Button btnRefresh;
    private Button tabBooks;
    private Button tabApps;
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
        lvBooks.setAdapter(booksAdapter);

        appsAdapter = new AppsAdapter(this);
        gvApps.setAdapter(appsAdapter);

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        overridePendingTransition(0, 0);
        updateClock();
        loadBooks();
    }

    private void initViews() {
        tvClock = findViewById(R.id.tv_clock);
        btnRefresh = findViewById(R.id.btn_refresh);
        tabBooks = findViewById(R.id.tab_books);
        tabApps = findViewById(R.id.tab_apps);
        containerBooks = findViewById(R.id.container_books);
        containerApps = findViewById(R.id.container_apps);
        lvBooks = findViewById(R.id.lv_books);
        gvApps = findViewById(R.id.gv_apps);
        tvEmptyBooks = findViewById(R.id.tv_empty_books);
        tvEmptyApps = findViewById(R.id.tv_empty_apps);
    }

    private void setupListeners() {
        tabBooks.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBooksTab();
            }
        });

        tabApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAppsTab();
            }
        });

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateClock();
                loadBooks();
                loadApps();
            }
        });

        lvBooks.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BookItem item = booksAdapter.getItem(position);
                openBook(item);
            }
        });

        gvApps.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppItem item = appsAdapter.getItem(position);
                openApp(item);
            }
        });
    }

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

    private void updateClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        tvClock.setText(sdf.format(new Date()));
    }

    private void loadBooks() {
        new AsyncTask<Void, Void, List<BookItem>>() {
            @Override
            protected List<BookItem> doInBackground(Void... voids) {
                List<BookItem> list = new ArrayList<>();
                Cursor cursor = null;
                try {
                    String[] projection = new String[] {
                        "_id",
                        "Title",
                        "Authors",
                        "Progress",
                        "Location",
                        "Type",
                        "MD5",
                        "LastAccess"
                    };

                    String selection = "Title IS NOT NULL AND Location IS NOT NULL";
                    String sortOrder = "LastAccess DESC LIMIT 100";

                    cursor = getContentResolver().query(CMS_METADATA_URI, projection, selection, null, sortOrder);
                    if (cursor != null && cursor.moveToFirst()) {
                        int idIdx = cursor.getColumnIndex("_id");
                        int titleIdx = cursor.getColumnIndex("Title");
                        int authorIdx = cursor.getColumnIndex("Authors");
                        int progressIdx = cursor.getColumnIndex("Progress");
                        int locationIdx = cursor.getColumnIndex("Location");
                        int typeIdx = cursor.getColumnIndex("Type");
                        int md5Idx = cursor.getColumnIndex("MD5");
                        int lastAccessIdx = cursor.getColumnIndex("LastAccess");

                        do {
                            long id = cursor.getLong(idIdx);
                            String title = cursor.getString(titleIdx);
                            String author = cursor.getString(authorIdx);
                            String progress = cursor.getString(progressIdx);
                            String location = cursor.getString(locationIdx);
                            String type = cursor.getString(typeIdx);
                            String md5 = cursor.getString(md5Idx);
                            long lastAccess = cursor.getLong(lastAccessIdx);

                            // Only include if title is meaningful
                            if (!TextUtils.isEmpty(title) && !"NULL".equalsIgnoreCase(title)) {
                                list.add(new BookItem(id, title, author, progress, location, type, md5, lastAccess));
                            }
                        } while (cursor.moveToNext());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
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
                Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

                List<ResolveInfo> resolvedList = pm.queryIntentActivities(mainIntent, 0);
                if (resolvedList != null) {
                    for (ResolveInfo ri : resolvedList) {
                        String pkgName = ri.activityInfo.packageName;
                        // Avoid showing the launcher itself in the drawer
                        if (getPackageName().equals(pkgName)) {
                            continue;
                        }
                        String label = ri.loadLabel(pm).toString();
                        list.add(new AppItem(pkgName, ri.activityInfo.name, label, ri.loadIcon(pm)));
                    }
                }

                Collections.sort(list, new Comparator<AppItem>() {
                    @Override
                    public int compare(AppItem o1, AppItem o2) {
                        return o1.getLabel().compareToIgnoreCase(o2.getLabel());
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

    private void openBook(BookItem item) {
        String path = item.getLocation();
        if (TextUtils.isEmpty(path)) {
            Toast.makeText(this, "Book path not found", Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(path);
        if (!file.exists()) {
            Toast.makeText(this, "File not found: " + file.getName(), Toast.LENGTH_SHORT).show();
            return;
        }

        String mimeType = getMimeType(path);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(file), mimeType);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(intent);
            overridePendingTransition(0, 0);
        } catch (ActivityNotFoundException e) {
            // Try with generic mime type
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW);
                fallback.setDataAndType(Uri.fromFile(file), "*/*");
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
                overridePendingTransition(0, 0);
            } catch (Exception ex) {
                Toast.makeText(this, "No application found to open this book", Toast.LENGTH_LONG).show();
            }
        }
    }

    private String getMimeType(String path) {
        String lower = path.toLowerCase(Locale.US);
        if (lower.endsWith(".epub")) return "application/epub+zip";
        if (lower.endsWith(".fb2") || lower.endsWith(".fb2.zip")) return "application/x-fictionbook+xml";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".mobi") || lower.endsWith(".prc")) return "application/x-mobipocket-ebook";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".cbr")) return "application/x-cbr";
        if (lower.endsWith(".cbz")) return "application/x-cbz";

        String extension = MimeTypeMap.getFileExtensionFromUrl(path);
        if (extension != null) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            if (mime != null) return mime;
        }
        return "*/*";
    }

    private void openApp(AppItem item) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(item.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                overridePendingTransition(0, 0);
            } else {
                // Try component name
                Intent compIntent = new Intent(Intent.ACTION_MAIN);
                compIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                compIntent.setComponent(new ComponentName(item.getPackageName(), item.getActivityName()));
                compIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(compIntent);
                overridePendingTransition(0, 0);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not open " + item.getLabel(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (isAppsTabVisible) {
            showBooksTab();
        }
        // As a launcher, do not exit when Home/Back is pressed on the root screen
    }
}

