#!/usr/bin/env python3
"""Enabled hide-on-scroll capture: with the dial on, a proven scroll must tuck the nav row.
The scroll itself is verified from the greeting's position before any frame counts,
so a dead swipe reads as a setup failure, not as nav staying visible."""
from playback_check import adb, back, tap, wait_label, screen, tree, PKG
import re
import time
import subprocess

def greeting_top():
    root = tree()
    for node in root.iter('node'):
        t = node.get('text') or ''
        if t.startswith('Good ') or t.startswith('Late-night') or t.startswith('Your late-night'):
            m = re.match(r'\[(\d+),(\d+)\]', node.get('bounds') or '')
            if m:
                return int(m.group(2))
    return None

def wait_greeting_top(seconds=60):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        top = greeting_top()
        if top is not None:
            return top
        time.sleep(1)
    screen('failure')
    raise AssertionError('UI never showed the home greeting')

nav_on = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>"
          '<bool name="hide_on_scroll" value="true" /></map>')
adb('shell', 'am', 'force-stop', PKG)
subprocess.run(['adb', 'shell', f"run-as {PKG} sh -c 'mkdir -p shared_prefs && cat > shared_prefs/navigation.xml'"],
               input=nav_on.encode(), check=True)
adb('shell', 'am', 'start', '-n', PKG + '/com.orismusic.client.MainActivity')
wait_greeting_top()
time.sleep(3)
before = greeting_top()
adb('shell', 'input', 'swipe', '540', '1200', '540', '400', '350')
time.sleep(0.4)
screen('nav_hide_enabled_scrolled')
after = greeting_top()
time.sleep(1.2)
screen('nav_hide_enabled_settled')
scrolled = after is None or (before is not None and after < before - 300)
if not scrolled:
    adb('shell', 'input', 'swipe', '540', '1400', '540', '300', '450')
    time.sleep(0.5)
    screen('nav_hide_enabled_retry')
    retry = greeting_top()
    scrolled = retry is None or (before is not None and retry < before - 300)
nav_off = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>"
           '<bool name="hide_on_scroll" value="false" /></map>')
adb('shell', 'am', 'force-stop', PKG)
subprocess.run(['adb', 'shell', f"run-as {PKG} sh -c 'cat > shared_prefs/navigation.xml'"],
               input=nav_off.encode(), check=True)
if not scrolled:
    raise AssertionError('SETUP FAILURE: home feed never scrolled; nav-hide capture inconclusive')
print('PASS: nav hide capture (scroll proven; nav verdict read from the frames)', flush=True)
