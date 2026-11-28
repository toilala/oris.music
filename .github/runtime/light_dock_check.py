#!/usr/bin/env python3
"""Light-theme dock evidence: full-frame captures (status bar and system nav included)
of Home idle and the playing mini player in the paper theme. Decides whether the black
bottom band in the earlier light_home capture was a capture artifact or a real
layout/system-bar defect."""
import subprocess
import time
from playback_check import adb, wait_label, screen, PKG

MAIN = PKG + '/com.orismusic.client.MainActivity'
TONE_ACTIVITY = PKG + '/com.orismusic.client.debug.LocalPlaybackCheckActivity'

light_xml = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>"
             '<string name="theme">Light</string></map>')
adb('shell', 'am', 'force-stop', PKG)
subprocess.run(['adb', 'shell', f"run-as {PKG} sh -c 'mkdir -p shared_prefs && cat > shared_prefs/appearance.xml'"],
               input=light_xml.encode(), check=True)
adb('shell', 'am', 'start', '-n', MAIN)
wait_label('Home', seconds=60)
# Capture at rest, not mid-load: wait for real feed content so the shot is not the
# shimmer/skeleton window that made the earlier idle capture read as dimmed.
wait_label('Quick picks', seconds=60)
time.sleep(4)
screen('light_dock_idle')

# Playing: the debug local tone behind the real MainActivity, mini player visible.
adb('shell', 'am', 'start', '-n', TONE_ACTIVITY)
wait_label('Local audio playing', seconds=30)
adb('shell', 'am', 'start', '-n', MAIN, '-f', '0x04000000')
time.sleep(4)
screen('light_dock_playing')

# Restore dark so later checks photograph the default theme.
dark_xml = light_xml.replace('Light', 'Dark')
adb('shell', 'am', 'force-stop', PKG)
subprocess.run(['adb', 'shell', f"run-as {PKG} sh -c 'cat > shared_prefs/appearance.xml'"],
               input=dark_xml.encode(), check=True)
# Theme switch must also settle: photograph the dark theme at rest after switching back.
adb('shell', 'am', 'start', '-n', MAIN)
wait_label('Quick picks', seconds=60)
time.sleep(4)
screen('dark_after_switch')
adb('shell', 'am', 'start', '-n', TONE_ACTIVITY)
wait_label('Local audio playing', seconds=30)
adb('shell', 'am', 'start', '-n', MAIN, '-f', '0x04000000')
time.sleep(4)
screen('dark_dock_playing')
print('PASS: light dock captures (idle + playing) and dark after the switch back', flush=True)
