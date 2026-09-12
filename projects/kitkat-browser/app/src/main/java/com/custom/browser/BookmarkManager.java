package com.dmikam.kitkatbrowser;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class BookmarkManager {

    private static final String PREF_NAME = "bookmarks_pref";
    private static final String KEY_BOOKMARKS = "bookmarks_list";

    public static class Bookmark {
        public String title;
        public String url;

        public Bookmark(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static List<Bookmark> getBookmarks(Context context) {
        List<Bookmark> list = new ArrayList<>();
        String jsonStr = getPrefs(context).getString(KEY_BOOKMARKS, "[]");
        try {
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                list.add(new Bookmark(obj.optString("title", "Bookmark"), obj.optString("url")));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static boolean isBookmarked(Context context, String url) {
        if (url == null) return false;
        for (Bookmark b : getBookmarks(context)) {
            if (url.equalsIgnoreCase(b.url)) return true;
        }
        return false;
    }

    public static void addBookmark(Context context, String title, String url) {
        if (url == null || isBookmarked(context, url)) return;
        List<Bookmark> bookmarks = getBookmarks(context);
        bookmarks.add(new Bookmark(title != null && !title.isEmpty() ? title : url, url));
        saveBookmarks(context, bookmarks);
    }

    public static void removeBookmark(Context context, String url) {
        if (url == null) return;
        List<Bookmark> bookmarks = getBookmarks(context);
        for (int i = 0; i < bookmarks.size(); i++) {
            if (url.equalsIgnoreCase(bookmarks.get(i).url)) {
                bookmarks.remove(i);
                break;
            }
        }
        saveBookmarks(context, bookmarks);
    }

    private static void saveBookmarks(Context context, List<Bookmark> bookmarks) {
        JSONArray array = new JSONArray();
        for (Bookmark b : bookmarks) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("title", b.title);
                obj.put("url", b.url);
                array.put(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        getPrefs(context).edit().putString(KEY_BOOKMARKS, array.toString()).apply();
    }
}
