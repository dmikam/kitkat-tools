package com.dmikam.kitkatebooklauncher;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.io.File;

public class SettingsActivity extends Activity {

    private TextView tvCacheInfo;
    private Button btnClearCache;
    private CheckBox chkShowCovers;
    private CheckBox chkAutoLoadCovers;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        tvCacheInfo = findViewById(R.id.tv_cache_info);
        btnClearCache = findViewById(R.id.btn_clear_cache);
        chkShowCovers = findViewById(R.id.chk_show_covers);

        chkShowCovers.setChecked(prefs.getBoolean("pref_show_covers", true));
        chkShowCovers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putBoolean("pref_show_covers", chkShowCovers.isChecked()).apply();
            }
        });

        chkAutoLoadCovers = findViewById(R.id.chk_auto_load_covers);
        chkAutoLoadCovers.setChecked(prefs.getBoolean("pref_auto_load_covers", false));
        chkAutoLoadCovers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putBoolean("pref_auto_load_covers", chkAutoLoadCovers.isChecked()).apply();
            }
        });

        btnClearCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearCoverCache();
                updateCacheInfo();
            }
        });

        updateCacheInfo();
    }

    private void updateCacheInfo() {
        long total = 0L;
        File dir = getCacheDir();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName() != null && f.getName().startsWith("cover_")) total += f.length();
            }
        }
        tvCacheInfo.setText("Thumbnails cache: " + humanReadableByteCount(total));
    }

    private void clearCoverCache() {
        File dir = getCacheDir();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName() != null && f.getName().startsWith("cover_")) f.delete();
            }
        }
    }

    private static String humanReadableByteCount(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
