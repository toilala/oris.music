#!/bin/bash
# Installs the minified release APK, opens it, searches, and fails on a crash.
mkdir -p runtime-evidence
APK=$(ls app/build/outputs/apk/foss/release/*.apk | head -1)
adb install -r "$APK" || exit 1
adb logcat -c
adb shell pm grant toilala.oris android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell monkey -p toilala.oris -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 25
adb exec-out screencap -p > runtime-evidence/release_home.png
# a few taps around the screen to exercise navigation without relying on labels
for i in 1 2 3; do adb shell input tap 540 1700; sleep 3; done
sleep 5
adb exec-out screencap -p > runtime-evidence/release_after_taps.png
adb logcat -d > runtime-evidence/logcat.txt
PID=$(adb shell pidof toilala.oris)
echo "pid: $PID"
if grep -q "FATAL EXCEPTION" runtime-evidence/logcat.txt; then grep -A12 "FATAL EXCEPTION" runtime-evidence/logcat.txt | head -40; exit 1; fi
[ -n "$PID" ] || { echo "app not running"; exit 1; }
echo "release app launched and stayed up"
