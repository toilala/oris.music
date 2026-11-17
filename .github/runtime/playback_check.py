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



def node_center(n):
    nums = [int(x) for x in re.findall(r"\d+", n.get("bounds") or "")]
    if len(nums) != 4:
        return None
    return (nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2


def tap_content_desc(root, desc):
    for n in root.iter("node"):
        if (n.get("content-desc") or "").strip() == desc:
            c = node_center(n)
            if c:
                adb("shell", "input", "tap", str(c[0]), str(c[1]))
                return True
    return False


def find_song_target(root):
    """First clickable in-feed card. Song cards are clickable Compose nodes;
    playlist cards, headers and chrome are not (or are excluded by label),
    so this skips playlist-only shelves. None if no song card is present."""
    chrome = {"OrisMusic", "Refresh home feed", "Search", "Settings",
              "Home", "Library", "Queue"}
    for n in root.iter("node"):
        if n.get("clickable") != "true":
            continue
        texts = set()
        for d in n.iter("node"):
            for attr in ("text", "content-desc"):
                v = (d.get(attr) or "").strip()
                if v:
                    texts.add(v)
        if texts & chrome:
            continue
        c = node_center(n)
        if c:
            label = sorted(texts)[0] if texts else "?"
            return c[0], c[1], label
    return None

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
            # chrome alone (app bar + bottom nav) is ~8 texts; any real feed
            # (songs or playlist-only) shows many more
            if len(texts) >= 12:
                result["feed_loaded"] = True
                break
    screenshot("1_home.png")

    if not result["feed_loaded"]:
        print("FEED DID NOT LOAD")
    else:
        # YouTube rotates anonymous home shelves; some rounds have no song
        # cards. Refresh up to 3 times looking for a playable song card.
        for attempt in range(5):
            if find_song_target(root) is not None:
                break
            print("NO SONG CARDS YET, refreshing (attempt", attempt + 1, ")",
                  flush=True)
            tap_content_desc(root, "Refresh home feed")
            time.sleep(10)
            root = dump_ui()
        # Tap a real song card: find a feed text node (song title) below the
        # app bar and above the bottom nav, then tap its bounds center.
        target = find_song_target(root)
        if target is None:
            print("NO SONG CARDS IN FEED after refreshes", flush=True)
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
                   if re.search(r"FATAL|AndroidRuntime|OrisMedia|OrisTap|InnerTube|MainActivity|"
                                r"PlayerConnection|ServiceConnection|Binding|"
                                r"innertube|Resolving|Resolved|YouTube", l)]
    print("LOGCAT INTERESTING (last 80):", flush=True)
    for l in interesting[-80:]:
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
