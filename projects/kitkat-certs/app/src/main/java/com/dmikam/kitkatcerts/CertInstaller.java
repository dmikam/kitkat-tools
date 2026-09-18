package com.dmikam.kitkatcerts;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class CertInstaller {
    private static final String TAG = "CertInstaller";

    private CertInstaller() {
    }

    public static boolean stageUserCertificates(Context context, int[] certResources) {
        File certDir = getWorldReadableCertDir();
        if (certDir == null) {
            Log.e(TAG, "Unable to create a world-readable cert directory");
            return false;
        }

        for (int resourceId : certResources) {
            String resourceName = context.getResources().getResourceEntryName(resourceId);
            File target = new File(certDir, resourceName + ".crt");
            if (!writeRawCertificate(context, resourceId, target)) {
                return false;
            }
        }

        return true;
    }

    public static void launchUserCertificateInstaller(Activity activity, int[] certResources) {
        File certDir = getWorldReadableCertDir();
        if (certDir == null) {
            Log.e(TAG, "No cert directory available");
            return;
        }

        for (int resourceId : certResources) {
            String resourceName = activity.getResources().getResourceEntryName(resourceId);
            File certFile = new File(certDir, resourceName + ".crt");
            if (!certFile.exists()) {
                Log.w(TAG, "Certificate file not staged: " + certFile.getAbsolutePath());
                continue;
            }

            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(Uri.fromFile(certFile), "application/x-x509-ca-cert");
            viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                activity.startActivity(viewIntent);
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "No cert installer handler for x509-ca-cert; trying x509-user-cert", e);
                Intent fallbackIntent = new Intent(Intent.ACTION_VIEW);
                fallbackIntent.setDataAndType(Uri.fromFile(certFile), "application/x-x509-user-cert");
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    activity.startActivity(fallbackIntent);
                } catch (ActivityNotFoundException ignored) {
                    Log.e(TAG, "No certificate installer available on this device", ignored);
                }
            }
        }
    }

    private static File getWorldReadableCertDir() {
        String[] candidates = new String[] {
                "/data/local/tmp",
                "/tmp",
                "/sdcard/tmp",
                "/storage/emulated/0/tmp",
                "/storage/emulated/legacy/tmp",
                new File(Environment.getExternalStorageDirectory(), "tmp").getAbsolutePath()
        };

        for (String path : candidates) {
            File dir = new File(path);
            Log.e(TAG, "writing to " + dir.getAbsolutePath());
            if (dir.exists() || dir.mkdirs()) {
                if (dir.setReadable(true, false) && dir.setWritable(true, false) && dir.setExecutable(true, false)) {
                    Log.e(TAG, "SUCCESS");
                    return dir;
                }
            }
            Log.e(TAG, "FAILED");
        }

        return null;
    }

    private static boolean writeRawCertificate(Context context, int resourceId, File target) {
        try (InputStream in = context.getResources().openRawResource(resourceId);
             OutputStream out = new FileOutputStream(target)) {

            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            target.setReadable(true, false);
            target.setWritable(true, false);
            target.setExecutable(true, false);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to stage certificate to cert dir", e);
            return false;
        }
    }
}
