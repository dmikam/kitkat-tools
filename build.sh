#!/usr/bin/env bash

set -e

TARGET="${1:-KitKatDownloader}"
OUTPUT_DIR="output"

export CURRENT_UID="$(id -u)"
export CURRENT_GID="$(id -g)"

mkdir -p "$OUTPUT_DIR"

APP_NAME="$TARGET"
PROJECT_PATH="projects/${TARGET}"
TARGET_APK="${OUTPUT_DIR}/${APP_NAME}-debug.apk"

echo "=== Building ${APP_NAME} (${PROJECT_PATH}) ==="

PROJECT_DIR="$PROJECT_PATH" docker compose up --build android-builder

# Ruta para KitKatDownloader o Lightning Browser con sabores (productFlavors)
if [ -f "${PROJECT_PATH}/app/build/outputs/apk/lightningPlus/debug/app-lightningPlus-debug.apk" ]; then
    GENERATED_APK="${PROJECT_PATH}/app/build/outputs/apk/lightningPlus/debug/app-lightningPlus-debug.apk"
else
    GENERATED_APK="${PROJECT_PATH}/app/build/outputs/apk/debug/app-debug.apk"
fi

if [ -f "$GENERATED_APK" ]; then
    mv "$GENERATED_APK" "$TARGET_APK"
    echo "Success: Saved to ${TARGET_APK}"
else
    echo "Error: Build failed for ${APP_NAME}. APK not found."
    exit 1
fi
