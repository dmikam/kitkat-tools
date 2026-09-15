# Onyx / Tagus Recent Books & Reading Progress Data Source

This document describes how recent books, metadata, and reading progress are stored, updated, and retrieved by the Onyx launcher and reading applications on Onyx / Tagus e-readers running Android 4.4.4 (KitKat, API 19).

---

## 1. Overview & Architecture

On Onyx-based e-readers (such as the **Tagus PokeP**), reading statistics, recently opened documents, and library metadata are managed via an Onyx system Content Provider rather than private launcher databases or local text files.

### Key Components

| Role | Package / Path | Description |
| :--- | :--- | :--- |
| **Data Provider Service** | `com.onyx.android.data` (`/system/app/OnyxData-release.apk`) | Implements and hosts the CMS Content Provider (`OnyxCmsProvider`). |
| **Launcher / Home App** | `com.onyx` (`/system/priv-app/ContentBrowser-release.apk`) | The launcher (`com.onyx.content.browser.activity.HomeActivity`) queries the provider to render the bookshelf and reading progress. |
| **Reader Applications** | `com.onyx.reader` (Onyx Neo Reader)<br>`com.neverland.oreader` (AlReader) | Native and integrated reading apps that update progress and reading history via the CMS provider. |

### Content Authority

```
content://com.onyx.android.sdk.OnyxCmsProvider
```

* **Target SDK**: 17 (Android 4.2) / runs on Android 4.4.4.
* **Access Control**: The provider is exported (`exported=true`) and does not enforce custom signature or dangerous permissions, allowing other local apps to query it with standard storage permissions.

---

## 2. Content Provider Endpoints

The `OnyxCmsProvider` exposes several sub-paths:

### 1. `content://com.onyx.android.sdk.OnyxCmsProvider/library_metadata`
**Primary table used for the bookshelf and recent books.**

Contains cataloged books, current reading progress, file paths, and reader configuration.

#### Key Columns:
* `_id` (`integer`): Primary key.
* `Title` (`text`): Book title (extracted from metadata or filename).
* `Authors` (`text`): Book author(s).
* `Progress` (`text`): Current reading progress formatted as `"<current_page>/<total_pages>"` (e.g., `92/855`, `179/303`).
* `LastAccess` (`integer`): Unix timestamp (seconds or milliseconds depending on source) of when the book was last opened. Used for `ORDER BY LastAccess DESC`.
* `LastModified` (`integer`): File modification timestamp.
* `Location` (`text`): Absolute file path (e.g., `/storage/emulated/0/Download/book.epub`).
* `NativeAbsolutePath` (`text`): Physical file path on storage.
* `Name` (`text`): Filename with extension.
* `Type` (`text`): File extension/type (e.g., `epub`, `fb2`, `zip`, `acsm`).
* `Size` (`integer`): File size in bytes.
* `MD5` (`text`): 32-character MD5 hash of the file, used as foreign key across Onyx tables.
* `ExtraAttributes` (`text` / JSON): JSON payload containing layout and reader state (e.g. `current_page`, `total_page`, `font_size`, `zoom`, `layout_type`, `location` internal anchor).
* `Favorite` (`integer`): 0 or 1 indicator.
* `Rating` (`integer`): Rating value.

---

### 2. `content://com.onyx.android.sdk.OnyxCmsProvider/library_history`
**Session-based reading log.**

Tracks every reading session for each book across reader applications.

#### Key Columns:
* `_id` (`integer`): Primary key.
* `MD5` (`text`): Foreign key matching the book file's MD5.
* `StartTime` (`integer`): Session start timestamp.
* `EndTime` (`integer`): Session end timestamp.
* `Progress` (`text`): Progress at the end of the session (e.g. `92/855`).
* `Application` (`text`): Package name of the reader used (e.g. `com.neverland.oreader`, `com.onyx.reader`).

---

### 3. `content://com.onyx.android.sdk.OnyxCmsProvider/library_position`
**Fine-grained reading anchor positions.**

Saves the exact internal position inside the EPUB / document.

#### Key Columns:
* `_id` (`integer`): Primary key.
* `MD5` (`text`): Book file MD5.
* `Location` (`text`): Internal document anchor (e.g. `OEBPS/Text/part0043.html#point(/1/4/2/50/1:84)`).
* `UpdateTime` (`integer`): Timestamp of position update.
* `Application` (`text`): Reader package name.

---

### 4. `content://com.onyx.android.sdk.OnyxCmsProvider/library_thumbnail`
**Cover thumbnails.**

Stores cached cover thumbnails rendered for books in the library.

---

## 3. Querying via ADB

To inspect the recent books list directly from the command line:

### Query Recent Books (Sorted by Last Opened)
```bash
adb shell "content query --uri content://com.onyx.android.sdk.OnyxCmsProvider/library_metadata \
  --projection Title:Authors:Progress:LastAccess:Location \
  --sort 'LastAccess DESC'"
```

### Query Reading History Sessions
```bash
adb shell "content query --uri content://com.onyx.android.sdk.OnyxCmsProvider/library_history \
  --projection MD5:Application:Progress:StartTime:EndTime \
  --sort '_id DESC'"
```

---

## 4. Querying from Android Code (Java / Android 4.4+)

In an Android application (such as a custom launcher), query `library_metadata` via `ContentResolver`:

```java
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

public class RecentBooksLoader {

    public static final Uri CMS_METADATA_URI =
        Uri.parse("content://com.onyx.android.sdk.OnyxCmsProvider/library_metadata");

    public static void loadRecentBooks(ContentResolver resolver) {
        String[] projection = new String[] {
            "_id",
            "Title",
            "Authors",
            "Progress",
            "LastAccess",
            "Location",
            "MD5"
        };

        // Filter out entries without a title or path if desired
        String selection = "Title IS NOT NULL AND Location IS NOT NULL";
        String sortOrder = "LastAccess DESC LIMIT 10";

        Cursor cursor = null;
        try {
            cursor = resolver.query(CMS_METADATA_URI, projection, selection, null, sortOrder);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String title = cursor.getString(cursor.getColumnIndex("Title"));
                    String author = cursor.getString(cursor.getColumnIndex("Authors"));
                    String progress = cursor.getString(cursor.getColumnIndex("Progress"));
                    String path = cursor.getString(cursor.getColumnIndex("Location"));
                    String md5 = cursor.getString(cursor.getColumnIndex("MD5"));
                    long lastAccess = cursor.getLong(cursor.getColumnIndex("LastAccess"));

                    // Use recent book entry...
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
```

### Manifest Permissions

Add storage read permission in `AndroidManifest.xml` (required to resolve file paths on external storage):
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

