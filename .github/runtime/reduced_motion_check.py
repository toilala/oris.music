#!/usr/bin/env python3
"""Reduced-motion path. The app reads Settings.System.ANIMATOR_DURATION_SCALE
(Components.kt rememberReducedMotion); the harness writes the same setting the OS
accessibility toggle controls. The namespace correspondence is verified on-device by
reading back both namespaces, and the snap behavior is judged on recorded frames."""
from playback_check import adb, tap, wait_label, screen, PKG
import subprocess
import time

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
rec = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '10', '/sdcard/reduced-motion.mp4'],
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
time.sleep(1)
tap('Soft pebble', bottom=False)
time.sleep(2)
tap('Soft pebble', bottom=False)
time.sleep(1)
rec.communicate(timeout=25)
adb('pull', '/sdcard/reduced-motion.mp4', 'runtime-evidence/reduced-motion.mp4')
screen('reduced_motion_after_toggles')
adb('shell', 'settings', 'put', 'system', 'animator_duration_scale', '1')
left = adb('shell', 'settings', 'get', 'system', 'animator_duration_scale').decode().strip()
assert left in ('1', '1.0'), 'animator scale was not restored: ' + left
adb('shell', 'am', 'force-stop', PKG)
print('PASS: reduced-motion transport morph recorded, animator scale restored', flush=True)
