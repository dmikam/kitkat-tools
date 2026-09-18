package com.dmikam.kitkatcerts;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        Button installButton = findViewById(R.id.installButton);

        installButton.setOnClickListener(v -> performInstallAttempt());
        statusText.setText("Ready. This app packages common root certificates for a KitKat browser and opens the normal Android certificate-install flow.\n\nOn Android 4.4, these are user certificates, not system-root trust entries.");
    }

    private void performInstallAttempt() {
        StringBuilder result = new StringBuilder();
        int[] certResources = new int[] {
                R.raw.ca_digicert_global_root_g2,
                R.raw.ca_digicert_high_assurance_ev_root_ca,
                R.raw.ca_geotrust_global_ca,
                R.raw.ca_globalsign_root_ca,
                R.raw.ca_isrg_root_x1
        };

        result.append("Bundled certificate entries:\n");
        for (int resId : certResources) {
            result.append("- ").append(getResources().getResourceEntryName(resId)).append("\n");
        }

        boolean staged = CertInstaller.stageUserCertificates(this, certResources);
        if (!staged) {
            result.append("\nCertificate staging failed: the app could not create a readable cert file in a world-readable temp folder.");
            statusText.setText(result.toString());
            return;
        }

        result.append("\nThe user-certificate installer will now open for each CA bundle.");
        statusText.setText(result.toString());
        CertInstaller.launchUserCertificateInstaller(this, certResources);
    }
}
