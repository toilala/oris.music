#!/usr/bin/env python3
"""Real playback-failure UI check: cut networking, tap a quick pick, capture the
failure banner and the diagnostic history dialog with real pixels."""
import re
import time
from playback_check import adb, screen, tree, wait_label, still_open, PKG

FAIL_FRAGMENTS = ['refused', 'stream error', 'playable stream', 'unable', 'resolve']


def wait_failure(seconds=150):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        for n in tree().iter('node'):
            t = (n.get('text') or '').lower()
            if any(f in t for f in FAIL_FRAGMENTS):
                return n
        time.sleep(2)
    screen('diag_no_failure')
    raise AssertionError('failure banner never appeared')


def tap_node(n):
    b = list(map(int, re.findall(r'\d+', n.get('bounds'))))
    adb('shell', 'input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
    time.sleep(2)


adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
import glob
adb('install', '-r', glob.glob('app/build/outputs/apk/foss/debug/*.apk')[0])
adb('shell', 'pm', 'grant', PKG, 'android.permission.POST_NOTIFICATIONS')
adb('shell', 'am', 'start', '-n', PKG + '/com.orismusic.client.MainActivity')
wait_label('Quick picks', seconds=120)
time.sleep(5)
screen('diag_home_online')

# Cut connectivity so the stream resolve really fails.
try:
    adb('root')
    time.sleep(2)
except Exception:
    pass
adb('shell', 'svc', 'wifi', 'disable')
adb('shell', 'svc', 'data', 'disable')
time.sleep(3)

# Tap the first quick-pick chip (same structural pick as home_picks_check).
root = tree()
header = next((n for n in root.iter('node') if (n.get('text') or '') == 'Quick picks'), None)
assert header is not None, 'Quick picks header missing'
hy = int(re.findall(r'\d+', header.get('bounds'))[1])
cands = []
for n in root.iter('node'):
    if n.get('clickable') != 'true':
        continue
    b = list(map(int, re.findall(r'\d+', n.get('bounds'))))
    if b[1] > hy + 100:
        cands.append((b[1], b[0], n))
cands.sort()
assert cands, 'no quick pick chip found'
tap_node(cands[0][2])

wait_label('Lyrics', seconds=60)   # player opened
banner = wait_failure()
screen('diag_banner')
tap_node(banner)                    # banner tap opens the history dialog
wait_label('Playback diagnostic', seconds=30)
time.sleep(1)
screen('diag_dialog')

# Back online and healthy.
adb('shell', 'svc', 'wifi', 'enable')
adb('shell', 'svc', 'data', 'enable')
from playback_check import tap
tap('Close')
logcat = adb('shell', 'logcat', '-d')
open('runtime-evidence/diag-logcat.txt', 'wb').write(logcat)
assert b'FATAL EXCEPTION' not in logcat, 'app crashed during diagnostic check'
still_open()
print('PASS: failure banner and history dialog rendered and captured', flush=True)
