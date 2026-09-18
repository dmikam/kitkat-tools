# kitkat-certs

This project packages a small set of common root certificates for an Android 4.4 browser and opens the device's normal user-certificate installation flow.

Important detail:

- Android 4.4 does not allow a normal, non-rooted app to write into `/system/etc/security/cacerts`.
- The supported path on an unrooted device is a user certificate, installed through the Android Settings / certificate UI.
- This app stages the bundled CA certificates into a world-readable temporary directory (`/data/local/tmp/kitkat-certs` with `/sdcard/tmp` fallback) and then launches the Android certificate installer for each one.
- This is important because the stock Android 4.4 certificate installer refuses files that are not readable by the system UI.

Project structure:

- `app/src/main/java/com/dmikam/kitkatcerts/MainActivity.java` – UI and install trigger
- `app/src/main/java/com/dmikam/kitkatcerts/CertInstaller.java` – user-certificate staging and launch logic
- `app/src/main/res/raw/*.crt` – bundled CA certificate files

Recommended workflow:

1. Build the APK in the Android toolchain used by this repository.
2. Install it on the target KitKat device.
3. Tap the button in the app.
4. Android will open the certificate-install flow for each root CA.
5. Accept the user-certificate prompts to add them to the trusted-user store for the browser.

This bundle focuses on commonly used public roots that older KitKat browsers often need for HTTPS navigation.
