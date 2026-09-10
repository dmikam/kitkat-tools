package com.dmikam.kitkatdownloader;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
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
                startCustomDownload(intent.getData().toString());
            } else if (Intent.ACTION_SEND.equals(action)) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                String extractedUrl = extractUrl(sharedText);
                if (extractedUrl != null) {
                    startCustomDownload(extractedUrl);
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

    private void startCustomDownload(String urlString) {
        Toast.makeText(this, "Iniciando descarga directa...", Toast.LENGTH_SHORT).show();
        new DownloadTask().execute(urlString);
    }

    private class DownloadTask extends AsyncTask<String, Void, Boolean> {
        private String fileName = "";

        @Override
        protected Boolean doInBackground(String... params) {
            String urlString = params[0];
            InputStream input = null;
            FileOutputStream output = null;
            HttpURLConnection connection = null;

            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();

                // Habilitar TLS 1.2 explícitamente en conexiones HTTPS para Android KitKat
                if (connection instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) connection).setSSLSocketFactory(new TLSSocketFactory());
                }

                // User-Agent para evitar bloqueos de servidores
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 4.4.2)");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage());
                    return false;
                }

                Uri uri = Uri.parse(urlString);
                fileName = uri.getLastPathSegment();
                if (TextUtils.isEmpty(fileName) || !fileName.contains(".")) {
                    fileName = "download_" + System.currentTimeMillis();
                }

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
