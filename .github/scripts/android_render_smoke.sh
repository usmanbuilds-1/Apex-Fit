#!/usr/bin/env bash
set +e
chmod +x gradlew
./gradlew connectedDebugAndroidTest --no-daemon
result=$?
mkdir -p render-screenshots

echo "=== Installed APK and rendering diagnostics ==="
adb shell pm list packages | grep apexfit || true
for page in train nutrition progress; do
  adb exec-out run-as com.apexfit.app cat "files/render-$page.png" > "render-screenshots/$page.png" 2>/dev/null
  if [ ! -s "render-screenshots/$page.png" ]; then
    adb pull "/sdcard/Android/data/com.apexfit.app/files/render-$page.png" "render-screenshots/$page.png" >/dev/null 2>&1
  fi
  if [ ! -s "render-screenshots/$page.png" ]; then rm -f "render-screenshots/$page.png"; fi

  adb exec-out run-as com.apexfit.app cat "files/render-debug-$page.txt" > "render-screenshots/$page.txt" 2>/dev/null
  if [ ! -s "render-screenshots/$page.txt" ]; then
    adb pull "/sdcard/Android/data/com.apexfit.app/files/render-debug-$page.txt" "render-screenshots/$page.txt" >/dev/null 2>&1
  fi
  if [ -s "render-screenshots/$page.txt" ]; then cat "render-screenshots/$page.txt"; else rm -f "render-screenshots/$page.txt"; fi
done
adb exec-out screencap -p > render-screenshots/emulator-last-screen.png 2>/dev/null || true
echo "Test exit code: $result"
exit "$result"
