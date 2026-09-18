#!/usr/bin/env bash
set -euo pipefail

OUT="${1:-kitkat-backup-$(date +%F-%H%M%S)}"
mkdir -p "$OUT"/sdcard "$OUT"/apks "$OUT"/logs "$OUT"/ebook-state "$OUT"/user-storage

echo "==> Device check"
adb wait-for-device
adb devices

echo "==> Installed packages"
adb shell 'pm list packages -f' > "$OUT/logs/packages.txt"

echo "==> Disabled packages"
adb shell 'pm list packages -d' > "$OUT/logs/disabled-packages.txt"

echo "==> APK list"
adb shell 'pm list packages -f' | sed 's/^package://g' > "$OUT/logs/apk-list.txt"

echo "==> Save APKs"
while IFS= read -r line; do
  pkg="${line#package:}"
  pkg="${pkg%%=*}"
  apk_path="$(adb shell "pm path $pkg" 2>/dev/null | sed 's/^package://g' | tr -d '\r')"
  if [ -n "$apk_path" ] && [ "$apk_path" != "package:" ]; then
    adb pull "$apk_path" "$OUT/apks/${pkg//./_}.apk" 2>/dev/null || true
  fi
done < <(adb shell 'pm list packages -f' 2>/dev/null)

echo "==> Detect mounted storage paths"
adb shell 'for p in /sdcard /mnt/sdcard /storage/emulated/0 /storage/emulated/legacy /storage/sdcard0 /storage/extSdCard; do [ -d "$p" ] && echo "$p"; done' > "$OUT/logs/storage-paths.txt"

if [ ! -s "$OUT/logs/storage-paths.txt" ]; then
  echo "No writable storage paths found via ADB. Continuing with limited backup only." > "$OUT/logs/storage-paths.txt"
fi

echo "==> Pull user files from the detected storage paths"
while IFS= read -r p; do
  [ -n "$p" ] || continue
  stem="$(printf '%s' "$p" | tr '/ ' '__' | sed 's#^_##; s#_$##')"
  mkdir -p "$OUT/user-storage/$stem"
  echo "Pulling $p -> $OUT/user-storage/$stem"
  adb pull "$p" "$OUT/user-storage/$stem" 2>/dev/null || true
done < "$OUT/logs/storage-paths.txt"

echo "==> Package visible user files in tar.gz"
tar -czf "$OUT/user-files.tar.gz" -C "$OUT" user-storage 2>/dev/null || true

echo "==> Backup Onyx/Tagus recent books and reading progress"
adb shell "content query --uri content://com.onyx.android.sdk.OnyxCmsProvider/library_metadata --projection Title:Authors:Progress:LastAccess:Location:MD5:ExtraAttributes --sort 'LastAccess DESC'" > "$OUT/ebook-state/library_metadata.txt" 2>/dev/null || true
adb shell "content query --uri content://com.onyx.android.sdk.OnyxCmsProvider/library_history --projection MD5:Application:Progress:StartTime:EndTime --sort '_id DESC'" > "$OUT/ebook-state/library_history.txt" 2>/dev/null || true
adb shell "content query --uri content://com.onyx.android.sdk.OnyxCmsProvider/library_position --projection MD5:Location:UpdateTime:Application --sort 'UpdateTime DESC'" > "$OUT/ebook-state/library_position.txt" 2>/dev/null || true

tar -czf "$OUT/ebook-state.tar.gz" -C "$OUT" ebook-state 2>/dev/null || true

echo "==> Export app data (best effort)"
adb backup -all -apk -shared -f "$OUT/app-data.ab" || echo "adb backup did not complete or is unsupported on this device"

echo

echo "Backup complete in: $OUT"
echo "Archive created: $OUT/user-files.tar.gz"
echo "Ebook state snapshot: $OUT/ebook-state.tar.gz"
echo "Important:"
echo " - if adb backup prompts for a password, record it"
echo " - app-private data and keystore cannot be restored reliably without root"
