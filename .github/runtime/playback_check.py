#!/usr/bin/env python3
"""Oris emulator playback runtime check.

Boots the app on the emulator, waits for the home feed, taps the first song
card, and verifies real playback through the media session and logcat.
Diagnostics (screen text, window focus, logcat excerpts) print to stdout so
the job log itself carries the evidence. Artifacts also land in
runtime-evidence/ for upload.
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


def visible_texts(root, limit=40):
    out = []
    for n in root.iter("node"):
        t = (n.get("text") or "").strip() or (n.get("content-desc") or "").strip()
        if t:
            out.append(t[:60])
    return out[:limit]


def clickable_nodes(root):
    out = []
    for n in root.iter("node"):
        if n.get("clickable") == "true" and n.get("bounds"):
            nums = [int(x) for x in re.findall(r"\d+", n.get("bounds"))]
            if len(nums) == 4:
                out.append(((nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2,
                            nums[1], n.get("text") or n.get("content-desc") or ""))
    return out


def focus():
    r = adb("shell", "dumpsys", "window", check=False)
    m = re.search(r"mCurrentFocus=.*?\s([\w.]+)/", r.stdout)
    return m.group(1) if m else "?"


result = {"feed_loaded": False, "feed_failed_text": None, "tap": None,
          "state": None, "positions": [], "resolved_log": False, "ok": False}
logcat = ""
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

    root = None
    for i in range(12):
        time.sleep(5)
        root = dump_ui()
        texts = visible_texts(root) if root is not None else []
        clicks = clickable_nodes(root) if root is not None else []
        print(f"POLL {i}: focus={focus()} clickables={len(clicks)} texts={texts}",
              flush=True)
        if root is not None:
            xml_text = ET.tostring(root, encoding="unicode")
            if "Could not load" in xml_text or "went wrong" in xml_text:
                m = re.search(r"Could not load[^\"]*", xml_text)
                result["feed_failed_text"] = (m.group(0)[:120] if m
                                              else "error state")
                print("FEED ERROR STATE:", result["feed_failed_text"], flush=True)
                break
            if len(clicks) > 4:
                result["feed_loaded"] = True
                break
    screenshot("1_home.png")

    if not result["feed_loaded"]:
        print("FEED DID NOT LOAD")
    else:
        # Tap a real song card: find a feed text node (song title) below the
        # app bar and above the bottom nav, then tap its bounds center.
        chrome = {"OrisMusic", "Refresh home feed", "Search", "Settings",
                  "Home", "Library", "Queue"}
        h = max((y for _, y, _, _ in clickable_nodes(root)), default=2000)
        # Song titles render twice in the tree (card + a11y ghost); section
        # headers render once. Tap the first duplicated in-feed text.
        counts = {}
        for n in root.iter("node"):
            t = (n.get("text") or "").strip() or (n.get("content-desc") or "").strip()
            if t:
                counts[t] = counts.get(t, 0) + 1
        target = None
        for n in root.iter("node"):
            t = (n.get("text") or "").strip() or (n.get("content-desc") or "").strip()
            if not t or t in chrome or len(t) < 4 or counts.get(t, 0) < 2:
                continue
            nums = [int(x) for x in re.findall(r"\d+", n.get("bounds") or "")]
            if len(nums) != 4:
                continue
            cy = (nums[1] + nums[3]) // 2
            if 260 < cy < h - 260:
                target = ((nums[0] + nums[2]) // 2, cy, t)
                break
        if target is None:
            # fallback: first clickable inside the card band (below app bar,
            # above bottom nav)
            band = [c for c in clickable_nodes(root) if 400 < c[1] < h - 260]
            band.sort(key=lambda c: c[1])
            if band:
                c = band[0]
                target = (c[0], c[1], c[3] or "clickable card")
        if target is None:
            print("NO TAPPABLE CARD FOUND")
        else:
            print("TAPPING:", target[2], "at", target[0], target[1], flush=True)
            result["tap"] = [target[0], target[1]]
            adb("shell", "input", "tap", str(target[0]), str(target[1]))
            time.sleep(20)
            screenshot("2_after_tap.png")

            sess = adb("shell", "dumpsys", "media_session", check=False).stdout
            with open(os.path.join(EVIDENCE, "media_session.txt"), "w") as f:
                f.write(sess)
            m = re.search(r"state=(\d+)", sess)
            result["state"] = int(m.group(1)) if m else None
            for _ in range(2):
                m = re.search(r"position=(\d+)", sess)
                result["positions"].append(int(m.group(1)) if m else -1)
                time.sleep(5)
                sess = adb("shell", "dumpsys", "media_session",
                           check=False).stdout
            m = re.search(r"position=(\d+)", sess)
            result["positions"].append(int(m.group(1)) if m else -1)

    logcat = adb("logcat", "-d", check=False).stdout
    with open(os.path.join(EVIDENCE, "logcat.txt"), "w") as f:
        f.write(logcat)
    interesting = [l for l in logcat.splitlines()
                   if re.search(r"FATAL|AndroidRuntime|OrisMedia|InnerTube|"
                                r"innertube|Resolving|Resolved|YouTube", l)]
    print("LOGCAT INTERESTING (last 40):", flush=True)
    for l in interesting[-40:]:
        print(l[:200], flush=True)
    result["resolved_log"] = ("Resolving stream" in logcat) or \
        ("Resolved" in logcat)

    playing = result["state"] == 3
    advancing = len(result["positions"]) >= 2 and \
        result["positions"][-1] > result["positions"][0] >= 0
    result["ok"] = bool(result["feed_loaded"] and (playing or advancing)
                        and result["resolved_log"])
finally:
    with open(os.path.join(EVIDENCE, "result.txt"), "w") as f:
        for k, v in result.items():
            f.write(f"{k}={v}\n")
    print("RESULT:", result)

sys.exit(0 if result["ok"] else 1)
