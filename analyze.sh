#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 <package.name.or.pid>"
  exit 2
fi

target="$1"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found in PATH"
  exit 1
fi

# ensure a device is connected
devcount=$(adb devices | awk 'NR>1 && $2=="device" {count++} END{print count+0}')
if [ "$devcount" -eq 0 ]; then
  echo "No device/emulator connected. Run 'adb devices' to check."
  exit 1
fi

pkg=""
pid=""
if [[ "$target" =~ ^[0-9]+$ ]]; then
  pid="$target"
else
  pkg="$target"
  pid=$(adb shell pidof "$pkg" 2>/dev/null | tr -d '\r' || true)
  if [ -z "$pid" ]; then
    pid=$(adb shell ps | tr -d '\r' | awk -v pkg="$pkg" '$0 ~ pkg {for(i=2;i<=NF;i++) if ($i ~ /^[0-9]+$/){print $i; break}}' | head -n1)
  fi
fi

if [ -z "${pid:-}" ] && [ -n "$pkg" ]; then
  echo "Cannot find PID for package $pkg"
fi

target_display=${pkg:-"PID $pid"}

echo "Collecting memory info for: $target_display"

memfile=$(mktemp)
adb shell dumpsys meminfo "${pkg:-$pid}" > "$memfile" 2>/dev/null || adb shell dumpsys meminfo "$pid" > "$memfile" 2>/dev/null || true

echo
echo "==== Summary ===="
# Try to extract a sensible total PSS value
total=$(awk 'BEGIN{IGNORECASE=1} /TOTAL/ && /PSS/ {for(i=1;i<=NF;i++) if ($i ~ /^[0-9]+$/){print $i; exit}}' "$memfile")
if [ -z "$total" ]; then
  total=$(awk 'BEGIN{IGNORECASE=1} /TOTAL/ {for(i=1;i<=NF;i++) if ($i ~ /^[0-9]+$/){print $i; exit}}' "$memfile")
fi
if [ -n "$total" ]; then
  printf "Total (approx PSS): %s KB\n" "$total"
fi

echo
echo "Key memory sections (if present):"
awk 'BEGIN{IGNORECASE=1}
  /native heap/ {print "Native Heap:",$0}
  /dalvik heap/ {print "Dalvik Heap:",$0}
  /java heap/ {print "Java Heap:",$0}
  /code\>/ {print "Code:",$0}
  /stack/ {print "Stack:",$0}
  /graphics/ {print "Graphics:",$0}
  /private other/ {print "Private Other:",$0}
' "$memfile"

echo
echo "==== Raw dumpsys meminfo (truncated) ===="
sed -n '1,200p' "$memfile"

# procrank if available on device
if adb shell which procrank >/dev/null 2>&1; then
  echo
  echo "==== procrank ===="
  if [ -n "$pkg" ]; then
    adb shell procrank | tr -d '\r' | grep "$pkg" || true
  else
    adb shell procrank | tr -d '\r' | grep " $pid " || true
  fi
fi

echo
echo "==== top (snapshot) ===="
adb shell top -n 1 | tr -d '\r' | grep -E "${pkg:-$pid}" || adb shell top -n 1 | tr -d '\r' | head -n 20

rm -f "$memfile"

echo
echo "Notes: 'Total (approx PSS)' is the primary indicator of memory footprint. Use procrank for PSS breakdown if available."
