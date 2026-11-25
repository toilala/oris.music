#!/usr/bin/env python3
"""Reduced-motion path, tested through the real user-facing setting. The OS "Remove
animations" accessibility toggle writes Settings.Global.ANIMATOR_DURATION_SCALE (AOSP
DisableAnimationsPreferenceController); the app reads that namespace (Components.kt
rememberReducedMotion, Global first with a System fallback for older devices). The
harness writes the Global key, verifies the read-back, proves the production control
toggles, and captures a screencap burst across the flips. A burst shows state changes;
it cannot prove the absence of short animations if frame timing is inadequate."""
import re
import time

from playback_check import adb, wait_label, screen, tree, PKG

adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '0')
read_global = adb('shell', 'settings', 'get', 'global', 'animator_duration_scale').decode().strip()
read_system = adb('shell', 'settings', 'get', 'system', 'animator_duration_scale').decode().strip()
print(f'NAMESPACE READ-BACK global={read_global} system={read_system}', flush=True)
assert read_global in ('0', '0.0'), 'global namespace did not take the 0 write'
adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'am', 'start', '-n', PKG + '/com.orismusic.client.debug.ControlStylesCheckActivity')
wait_label('Player styles')


def tap_clickable(label):
    wait_label(label)
    root = tree()
    parents = {c: p for p in root.iter('node') for c in p}
    target = next(n for n in root.iter('node') if label in (n.get('text'), n.get('content-desc')))
    node = target
    while node is not None and node.get('clickable') != 'true':
        node = parents.get(node)
    if node is None:
        node = target
    print('TAP ' + label + ' bounds=' + str(node.get('bounds')) + ' clickable=' + str(node.get('clickable')), flush=True)
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1)


def burst(tag, count=8):
    for i in range(count):
        screen(f'reduced_motion_{tag}_{i:02d}')


wait_label('Preview state: paused')
tap_clickable('Soft pebble')
burst('playing')
wait_label('Preview state: playing', seconds=15)
tap_clickable('Soft pebble')
burst('paused')
wait_label('Preview state: paused', seconds=15)
screen('reduced_motion_after_toggles')
adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '1')
left = adb('shell', 'settings', 'get', 'global', 'animator_duration_scale').decode().strip()
assert left in ('1', '1.0'), 'animator scale was not restored: ' + left
adb('shell', 'am', 'force-stop', PKG)
print('PASS: production control toggled under the OS reduced-motion setting, burst captured, scale restored', flush=True)
