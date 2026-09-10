#!/usr/bin/env bash

set -e

APP_NAME="${1:-KitKatDownloader}"
APK_FILE="output/${APP_NAME}-debug.apk"

echo "=== Uploading and Installing ${APK_FILE} ==="

if [ ! -f "$APK_FILE" ]; then
    echo "Error: $APK_FILE not found. Run ./build.sh first."
    exit 1
fi

if ! command -v adb &> /dev/null; then
    echo "Error: adb command not found on host."
    exit 1
fi

echo "Waiting for device..."
adb wait-for-device

echo "Installing APK..."
adb install -r -t "$APK_FILE"

echo ""
echo "Success! ${APK_FILE} installed successfully."
