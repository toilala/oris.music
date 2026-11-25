#!/usr/bin/env python3
"""Reduced-motion path. The app reads Settings.System.ANIMATOR_DURATION_SCALE
(Components.kt rememberReducedMotion); the harness writes the same setting the OS
accessibility toggle controls, and verifies the namespace correspondence on-device.
The check proves the production control actually toggled (on-screen state line) and
records the sequence; snap-vs-spring is judged on the recorded frames."""
import re
import subprocess
import time

from playback_check import adb, wait_label, screen, tree, PKG

adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
adb('shell', 'settings', 'put', 'system', 'animator_duration_scale', '0')
read_system = adb('shell', 'settings', 'get', 'system', 'animator_duration_scale').decode().strip()
read_global = adb('shell', 'settings', 'get', 'global', 'animator_duration_scale').decode().strip()
print(f'NAMESPACE READ-BACK system={read_system} global={read_global}', flush=True)
assert read_system in ('0', '0.0'), 'system namespace did not take the 0 write'
if read_global not in ('0', '0.0'):
    print('MISMATCH: app reads system namespace but global namespace kept ' + read_global, flush=True)
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


wait_label('Preview state: paused')
rec = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '10', '/sdcard/reduced-motion.mp4'],
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
time.sleep(1)
tap_clickable('Soft pebble')
wait_label('Preview state: playing', seconds=10)
time.sleep(1.5)
tap_clickable('Soft pebble')
wait_label('Preview state: paused', seconds=10)
time.sleep(1)
rec.communicate(timeout=25)
adb('pull', '/sdcard/reduced-motion.mp4', 'runtime-evidence/reduced-motion.mp4')
screen('reduced_motion_after_toggles')
adb('shell', 'settings', 'put', 'system', 'animator_duration_scale', '1')
left = adb('shell', 'settings', 'get', 'system', 'animator_duration_scale').decode().strip()
assert left in ('1', '1.0'), 'animator scale was not restored: ' + left
adb('shell', 'am', 'force-stop', PKG)
print('PASS: production control toggled under system reduced motion, sequence recorded, scale restored', flush=True)
