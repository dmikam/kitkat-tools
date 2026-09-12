package com.dmikam.kitkatdownloader;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
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

/**
 * Main activity acting as an invisible handler for intent-based downloads.
 * Receives URLs via ACTION_VIEW or ACTION_SEND, runs the download in the background,
 * and updates system notifications with real-time progress and completion shortcuts.
 */
public class MainActivity extends Activity {

    private static final String TAG = "KitKatDownloader";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();

            // Process direct link passed via ACTION_VIEW
            if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
                startCustomDownload(intent);

            // Process plain text or shared links via Android's native share sheet (ACTION_SEND)
            } else if (Intent.ACTION_SEND.equals(action)) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                String extractedUrl = extractUrl(sharedText);

                if (extractedUrl != null) {
                    Intent sendIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(extractedUrl));
                    startCustomDownload(sendIntent);
                } else {
                    Toast.makeText(this, "No valid URL found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else {
                finish();
            }
        } else {
            finish();
        }
    }

    /**
     * Extracts the first valid HTTP/HTTPS URL found within a block of plain text.
     */
    private String extractUrl(String text) {
        if (TextUtils.isEmpty(text)) return null;
        Pattern pattern = Pattern.compile("https?://\\S+");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    /**
     * Shows a toast notification and starts the asynchronous network download task.
     */
    private void startCustomDownload(Intent intent) {
        Toast.makeText(this, "Starting download...", Toast.LENGTH_SHORT).show();
        new DownloadTask(intent).execute();
    }

    /**
     * Asynchronous task handling HTTP/HTTPS connections, TLS 1.2 security patch,
     * header-based filename extraction, byte streaming, and notification updates.
     */
    private class DownloadTask extends AsyncTask<Void, Integer, Boolean> {
        private final Intent downloadIntent;
        private String fileName = "";
        private File outputFile;
        private NotificationManager notificationManager;
        private Notification.Builder notificationBuilder;

        public DownloadTask(Intent intent) {
            this.downloadIntent = intent;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            // Initialize persistent notification using KitKat native API
            notificationBuilder = new Notification.Builder(MainActivity.this)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Downloading file...")
                    .setContentText("Connecting...")
                    .setOngoing(true) // Prevents user from dismissing during download
                    .setProgress(100, 0, true); // Indeterminate progress bar initially

            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            String urlString = downloadIntent.getDataString();
            if (urlString == null) return false;

            // Extract optional parameters passed from the browser
            String passedUserAgent = downloadIntent.getStringExtra("EXTRA_USER_AGENT");
            String passedContentDisposition = downloadIntent.getStringExtra("EXTRA_CONTENT_DISPOSITION");

            InputStream input = null;
            FileOutputStream output = null;
            HttpURLConnection connection = null;

            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();

                // Inject explicit TLS 1.2 for modern HTTPS compatibility on KitKat
                if (connection instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) connection).setSSLSocketFactory(new TLSSocketFactory());
                }

                // Replicate passed User-Agent or apply fallback to prevent blocks
                if (!TextUtils.isEmpty(passedUserAgent)) {
                    connection.setRequestProperty("User-Agent", passedUserAgent);
                } else {
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 4.4.2)");
                }

                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                // Abort if server response is anything other than HTTP 200 OK
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage());
                    return false;
                }

                int fileLength = connection.getContentLength();

                // 1. Inspect actual headers returned by the server
                String serverDisposition = connection.getHeaderField("Content-Disposition");
                String mimeType = connection.getContentType();

                // 2. Use intent extras as fallback if server omitted Content-Disposition
                String finalDisposition = !TextUtils.isEmpty(serverDisposition)
                        ? serverDisposition
                        : passedContentDisposition;

                // 3. Resolve true filename post-redirect using final URL and URLUtil
                String finalUrl = connection.getURL().toString();
                fileName = URLUtil.guessFileName(finalUrl, finalDisposition, mimeType);

                // Ensure public Downloads directory exists
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }

                outputFile = new File(downloadsDir, fileName);
                Log.d(TAG, "Saving to: " + outputFile.getAbsolutePath());

                input = connection.getInputStream();
                output = new FileOutputStream(outputFile);

                byte[] data = new byte[4096];
                long total = 0;
                int count;

                // Stream byte data and update progress percentage
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        publishProgress(progress);
                    }
                    output.write(data, 0, count);
                }

                output.flush();
                return true;

            } catch (Exception e) {
                Log.e(TAG, "Error during download: ", e);
                return false;
            } finally {
                // Close streams and release network connections
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                } catch (Exception ignored) {}
                if (connection != null) connection.disconnect();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            int progress = values[0];

            // Update notification with real accumulated progress percentage
            notificationBuilder
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Downloading: " + fileName)
                    .setContentText(progress + "%")
                    .setProgress(100, progress, false);

            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success && outputFile != null) {
                Toast.makeText(MainActivity.this, "Downloaded: " + fileName, Toast.LENGTH_LONG).show();

                // Intent 1: Tapping the notification directly opens the downloaded file
                Intent openFileIntent = new Intent(Intent.ACTION_VIEW);
                String mimeType = getMimeType(outputFile.getAbsolutePath());
                openFileIntent.setDataAndType(Uri.fromFile(outputFile), mimeType != null ? mimeType : "*/*");
                openFileIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

                PendingIntent filePendingIntent = PendingIntent.getActivity(
                        MainActivity.this, 0, openFileIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                // Intent 2: Additional action button to open the downloads folder
                Intent openFolderIntent = new Intent(Intent.ACTION_VIEW);
                Uri folderUri = Uri.fromFile(outputFile.getParentFile());
                openFolderIntent.setDataAndType(folderUri, "resource/folder");
                openFolderIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                PendingIntent folderPendingIntent = PendingIntent.getActivity(
                        MainActivity.this, 1, openFolderIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                // Build dismissible success notification
                Notification completionNotification = new Notification.Builder(MainActivity.this)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("Download complete")
                        .setContentText(fileName)
                        .setOngoing(false) // Allows swipe to dismiss
                        .setAutoCancel(true) // Dismisses automatically on tap
                        .setContentIntent(filePendingIntent)
                        .addAction(android.R.drawable.ic_menu_more, "Open Folder", folderPendingIntent)
                        .build();

                notificationManager.notify(NOTIFICATION_ID, completionNotification);

            } else {
                Toast.makeText(MainActivity.this, "Connection or SSL error", Toast.LENGTH_LONG).show();

                // Notification in case of network or HTTP failure
                Notification errorNotification = new Notification.Builder(MainActivity.this)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("Download failed")
                        .setContentText("Could not download " + fileName)
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .build();

                notificationManager.notify(NOTIFICATION_ID, errorNotification);
            }

            // Finish transparent activity to free memory after processing task
            finish();
        }

        /**
         * Resolves the MIME type of a local file based on its file extension.
         */
        private String getMimeType(String filePath) {
            String extension = MimeTypeMap.getFileExtensionFromUrl(filePath);
            if (extension != null) {
                return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            }
            return "*/*";
        }
    }
}