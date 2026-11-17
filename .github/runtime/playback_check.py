#!/usr/bin/env python3
"""Oris emulator playback runtime check.

Boots the app on the emulator, waits for the home feed, taps the first song
card, and verifies real playback through the media session and logcat.
Evidence (screenshots, ui dump, logcat, result summary) lands in
runtime-evidence/ for the workflow to upload.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

EVIDENCE = "runtime-evidence"
PKG = "com.orismusic.client.debug"
ACTIVITY = "com.orismusic.client.MainActivity"
APK_GLOB = "app/build/outputs/apk/foss/debug/*.apk"

os.makedirs(EVIDENCE, exist_ok=True)
log = open(os.path.join(EVIDENCE, "logcat.txt"), "w")


def sh(cmd, check=True, capture=True):
    print("+", " ".join(cmd), flush=True)
    r = subprocess.run(cmd, capture_output=capture, text=True)
    if check and r.returncode != 0:
        print(r.stdout)
        print(r.stderr)
        raise SystemExit(f"command failed: {' '.join(cmd)}")
    return r


def adb(*args, check=True):
    return sh(["adb", *args], check=check)


def screenshot(name):
    sh(["adb", "exec-out", "screencap", "-p"], capture=False) if False else None
    r = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True)
    with open(os.path.join(EVIDENCE, name), "wb") as f:
        f.write(r.stdout)


def dump_ui():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
    adb("pull", "/sdcard/ui.xml", os.path.join(EVIDENCE, "ui.xml"), check=False)
    path = os.path.join(EVIDENCE, "ui.xml")
    if not os.path.exists(path):
        return None
    try:
        return ET.parse(path).getroot()
    except ET.ParseError:
        return None


def clickable_nodes(root):
    out = []
    for n in root.iter("node"):
        if n.get("clickable") == "true" and n.get("bounds"):
            nums = [int(x) for x in re.findall(r"\d+", n.get("bounds"))]
            if len(nums) == 4:
                out.append(((nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2,
                            nums[1], n.get("text") or n.get("content-desc") or ""))
    return out


result = {"feed_loaded": False, "tap": None, "state": None,
          "positions": [], "resolved_log": False, "ok": False}
try:
    adb("wait-for-device")
    adb("shell", "input", "keyevent", "82", check=False)  # unlock
    import glob
    apks = sorted(glob.glob(APK_GLOB))
    if not apks:
        raise SystemExit("no APK found at " + APK_GLOB)
    adb("install", "-r", apks[0])
    adb("logcat", "-c", check=False)
    adb("shell", "am", "start", "-n", f"{PKG}/{ACTIVITY}")

    # Wait for home feed (up to ~60s)
    root = None
    for _ in range(12):
        time.sleep(5)
        root = dump_ui()
        if root is not None:
            xml_text = ET.tostring(root, encoding="unicode")
            if "Could not load" not in xml_text and len(clickable_nodes(root)) > 4:
                result["feed_loaded"] = True
                break
    screenshot("1_home.png")

    if not result["feed_loaded"]:
        print("FEED DID NOT LOAD")
    else:
        nodes = clickable_nodes(root)
        h = max((y for _, y, _, _ in nodes), default=2000)
        candidates = [n for n in nodes if n[2] < h - 250]  # exclude bottom nav
        candidates.sort(key=lambda n: n[2])
        # skip the first row of clickables only if it is a header chip row; tap
        # the first large card candidate instead (cards have no text attr)
        card = next((n for n in candidates if not n[3] or len(n[3]) > 3),
                    candidates[0] if candidates else None)
        if card is None:
            print("NO TAPPABLE CARD FOUND")
        else:
            result["tap"] = [card[0], card[1]]
            adb("shell", "input", "tap", str(card[0]), str(card[1]))

    # Wait for resolve + buffer
    time.sleep(20)
    screenshot("2_after_tap.png")

    sess = adb("shell", "dumpsys", "media_session", check=False).stdout
    with open(os.path.join(EVIDENCE, "media_session.txt"), "w") as f:
        f.write(sess)
    m = re.search(r"state=PlaybackStateCompat \{state=(\d+)", sess) or \
        re.search(r"state=(\d+)", sess)
    result["state"] = int(m.group(1)) if m else None

    for _ in range(2):
        m = re.search(r"position=(\d+)", sess)
        result["positions"].append(int(m.group(1)) if m else -1)
        time.sleep(5)
        sess = adb("shell", "dumpsys", "media_session", check=False).stdout
    m = re.search(r"position=(\d+)", sess)
    result["positions"].append(int(m.group(1)) if m else -1)

    cat = adb("logcat", "-d", check=False).stdout
    log.write(cat)
    result["resolved_log"] = ("Resolving stream" in cat) or ("Resolved" in cat)

    playing = result["state"] == 3
    advancing = len(result["positions"]) >= 2 and \
        result["positions"][-1] > result["positions"][0] >= 0
    result["ok"] = bool(result["feed_loaded"] and (playing or advancing)
                        and result["resolved_log"])
finally:
    log.close()
    with open(os.path.join(EVIDENCE, "result.txt"), "w") as f:
        for k, v in result.items():
            f.write(f"{k}={v}\n")
    print("RESULT:", result)

sys.exit(0 if result["ok"] else 1)
