#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${1:-.}"
adb wait-for-device

echo "==> Restore app data"
if [ -f "$BACKUP_DIR/app-data.ab" ]; then
  adb restore "$BACKUP_DIR/app-data.ab"
else
  echo "No app-data.ab found; skipping adb restore"
fi

echo "==> Reinstall APKs"
if [ -d "$BACKUP_DIR/apks" ]; then
  find "$BACKUP_DIR/apks" -name '*.apk' -print0 | while IFS= read -r -d '' apk; do
    adb install -r "$apk"
  done
fi

echo "==> Restore tarred user files"
if [ -f "$BACKUP_DIR/user-files.tar.gz" ]; then
  tmpdir="$(mktemp -d)"
  tar -xzf "$BACKUP_DIR/user-files.tar.gz" -C "$tmpdir"
  find "$tmpdir/user-storage" -mindepth 1 -maxdepth 1 -type d | while IFS= read -r storage_dir; do
    target_name="$(basename "$storage_dir")"
    case "$target_name" in
      sdcard|sdcard1|emulated_0|storage_emulated_0|storage_emulated_legacy)
        adb push "$storage_dir" "/sdcard/"
        ;;
      *)
        adb push "$storage_dir" "/storage/"
        ;;
    esac
  done
  rm -rf "$tmpdir"
else
  echo "No user-files.tar.gz found; skipping archive restore"
fi

echo "==> Restore ebook state snapshots"
if [ -f "$BACKUP_DIR/ebook-state.tar.gz" ]; then
  tmpdir="$(mktemp -d)"
  tar -xzf "$BACKUP_DIR/ebook-state.tar.gz" -C "$tmpdir"
  mkdir -p "$tmpdir/ebook-state"
  echo "The Onyx CMS provider raw export was saved at: $BACKUP_DIR/ebook-state"
  echo "It cannot be restored automatically without the original Onyx provider or a custom importer app."
  rm -rf "$tmpdir"
else
  echo "No ebook-state.tar.gz found; skipping ebook snapshot restore"
fi

echo "Recovery finished"
