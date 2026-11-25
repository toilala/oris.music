#!/usr/bin/env python3
"""Status-bar inset evidence under network-disabled testing. Captures Home, Search,
Queue, Library and Settings at normal and large text, and reports the MEASURED top
edge of each screen's first text-bearing node from the live a11y tree. Measurements
and screenshots are evidence for review, not pass/fail assertions on expected pixel
positions: the review decides whether the spacing is right."""
import re
import time

from playback_check import adb, screen, tree, wait_label, tap, PKG

MAIN = PKG + '/com.orismusic.client.MainActivity'


def measure(screen_name):
    """Top edge of the first text-bearing node, plus what that node is, from a fresh
    tree. The app window dump excludes the status bar, so this is the content start."""
    root = tree()
    best = None
    for n in root.iter('node'):
        text = n.get('text') or n.get('content-desc')
        bounds = n.get('bounds')
        if not text or not bounds:
            continue
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', bounds))
        if best is None or y1 < best[0]:
            best = (y1, text[:40])
    print('INSET-MEASURE ' + screen_name + ' first-content-top='
          + (str(best[0]) if best else 'none') + ' ('
          + (best[1] if best else '') + ')', flush=True)


def capture(name):
    screen(name)
    measure(name)


def sweep(suffix):
    wait_label('Search', seconds=60)  # the nav bar labels every tab; app is up
    time.sleep(1)
    capture('inset_home' + suffix)
    tap('Search')
    time.sleep(2)
    capture('inset_search' + suffix)
    tap('Library')
    time.sleep(2)
    capture('inset_library' + suffix)
    tap('Queue')
    wait_label('Back from queue', seconds=30)
    time.sleep(1)
    capture('inset_queue' + suffix)
    adb('shell', 'input', 'keyevent', '4')
    time.sleep(1)
    tap('Settings', bottom=False)
    wait_label('Appearance', seconds=30)
    time.sleep(1)
    capture('inset_settings' + suffix)
    adb('shell', 'input', 'keyevent', '4')
    time.sleep(1)
    tap('Home')
    time.sleep(1)


adb('shell', 'am', 'start', '-n', MAIN, '-f', '0x04000000')
# A previous check can leave Now Playing open over the dock: collapse it by its own label
# so the sweeps always start from the plain tab screens.
for _ in range(5):
    if any(n.get('content-desc') == 'Collapse player' for n in tree().iter('node')):
        tap('Collapse player')
        time.sleep(1)
        break
    time.sleep(1)
sweep('')
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
time.sleep(2)
try:
    sweep('_large')
finally:
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
print('PASS: inset captures and measurements collected (evidence only, no position assertions)', flush=True)
