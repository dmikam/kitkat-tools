#!/usr/bin/env bash

set -e

APK_FILE="KitKatDownloader-debug.apk"

echo "=== Uploading and Installing ${APK_FILE} ==="

# Check if the APK file exists locally
if [ ! -f "$APK_FILE" ]; then
    echo "Error: $APK_FILE not found. Run ./build.sh first."
    exit 1
fi

# Check if adb is installed on the host
if ! command -v adb &> /dev/null; then
    echo "Error: adb command not found on host. Please install android-tools or adb."
    exit 1
fi

# Wait for a device/emulator to be connected
echo "Waiting for device..."
adb wait-for-device

# Install/Re-install the APK (-r replaces existing app, -t allows test APKs)
echo "Installing APK..."
adb install -r -t "$APK_FILE"

echo ""
echo "Success! ${APK_FILE} installed successfully."
