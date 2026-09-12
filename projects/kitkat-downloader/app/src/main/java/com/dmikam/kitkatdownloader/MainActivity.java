package com.dmikam.kitkatdownloader;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.URLUtil;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity {

    private static final String TAG = "KitKatDownloader";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();

            if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
                startCustomDownload(intent);
            } else if (Intent.ACTION_SEND.equals(action)) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                String extractedUrl = extractUrl(sharedText);
                if (extractedUrl != null) {
                    Intent sendIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(extractedUrl));
                    startCustomDownload(sendIntent);
                } else {
                    Toast.makeText(this, "No se encontro URL valida", Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else {
                finish();
            }
        } else {
            finish();
        }
    }

    private String extractUrl(String text) {
        if (TextUtils.isEmpty(text)) return null;
        Pattern pattern = Pattern.compile("https?://\\S+");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private void startCustomDownload(Intent intent) {
        Toast.makeText(this, "Iniciando descarga directa...", Toast.LENGTH_SHORT).show();
        new DownloadTask(intent).execute();
    }

    private class DownloadTask extends AsyncTask<Void, Void, Boolean> {
        private final Intent downloadIntent;
        private String fileName = "";

        public DownloadTask(Intent intent) {
            this.downloadIntent = intent;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            String urlString = downloadIntent.getDataString();
            if (urlString == null) return false;

            String passedUserAgent = downloadIntent.getStringExtra("EXTRA_USER_AGENT");
            String passedContentDisposition = downloadIntent.getStringExtra("EXTRA_CONTENT_DISPOSITION");

            InputStream input = null;
            FileOutputStream output = null;
            HttpURLConnection connection = null;

            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();

                // Enable TLS 1.2 explicitly for Android KitKat HTTPS connections
                if (connection instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) connection).setSSLSocketFactory(new TLSSocketFactory());
                }

                // Apply passed User-Agent if available, otherwise fallback
                if (!TextUtils.isEmpty(passedUserAgent)) {
                    connection.setRequestProperty("User-Agent", passedUserAgent);
                } else {
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 4.4.2)");
                }

                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage());
                    return false;
                }

                // 1. Check live HTTP response headers from server
                String serverDisposition = connection.getHeaderField("Content-Disposition");
                String mimeType = connection.getContentType();

                // 2. Fallback to passed Content-Disposition extra if server omitted response header
                String finalDisposition = !TextUtils.isEmpty(serverDisposition)
                        ? serverDisposition
                        : passedContentDisposition;

                // 3. Resolve actual filename from connection URL (post-redirect) + headers
                String finalUrl = connection.getURL().toString();
                fileName = URLUtil.guessFileName(finalUrl, finalDisposition, mimeType);

                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }

                File outputFile = new File(downloadsDir, fileName);
                Log.d(TAG, "Guardando en: " + outputFile.getAbsolutePath());

                input = connection.getInputStream();
                output = new FileOutputStream(outputFile);

                byte[] data = new byte[4096];
                int count;
                while ((count = input.read(data)) != -1) {
                    output.write(data, 0, count);
                }

                output.flush();
                return true;

            } catch (Exception e) {
                Log.e(TAG, "Error durante la descarga: ", e);
                return false;
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                } catch (Exception ignored) {}
                if (connection != null) connection.disconnect();
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                Toast.makeText(MainActivity.this, "Descargado: " + fileName, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this, "Error de conexion o SSL", Toast.LENGTH_LONG).show();
            }
            finish();
        }
    }
}