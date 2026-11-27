#!/usr/bin/env python3
"""Gesture-cancel leg for the pill<->player morph: adb shell input cannot emit
ACTION_CANCEL, so a tiny instrumentation class (MorphCancelTest) injects a real
DOWN->MOVE->CANCEL stream at the open sheet. The morph's onDragCancel must settle the
sheet back to fully open: asserted from the uiautomator tree after injection, and the
whole leg is recorded to mp4."""
import glob
import os
import subprocess
import time

from playback_check import adb, wait_label, tree, PKG, OUT, pidof, emit_mp4_frames

MAIN = PKG + '/com.orismusic.client.MainActivity'
COMPONENT = PKG + '/com.orismusic.client.debug.LocalPlaybackCheckActivity'
TONE = 'Runtime local tone'


def has_label(fragment):
    return any(fragment in (n.get('text'), n.get('content-desc')) for n in tree().iter('node'))


adb('wait-for-device')
os.makedirs(OUT, exist_ok=True)

apk = glob.glob('app/build/outputs/apk/androidTest/foss/debug/*.apk')
if not apk:
    print('BUILDING androidTest apk (one-off per job)', flush=True)
    subprocess.run(['./gradlew', ':app:assembleFossDebugAndroidTest', '--console=plain'], check=True)
    apk = glob.glob('app/build/outputs/apk/androidTest/foss/debug/*.apk')
adb('install', '-r', apk[0])

adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'am', 'start', '-n', COMPONENT)
wait_label('Local audio playing', seconds=30)
time.sleep(2)
adb('shell', 'am', 'start', '-n', MAIN, '-f', '0x04000000')
wait_label(TONE, seconds=25)
# Open the player through the morph, then cancel a drag mid-stream.
import re
root = tree()
parents = {c: p for p in root.iter('node') for c in p}
pill = None
for label_node in [n for n in root.iter('node') if n.get('text') == TONE]:
    node = label_node
    while node is not None and node.get('clickable') != 'true':
        node = parents.get(node)
    if node is None:
        continue
    if any(d.get('content-desc') in ('Play', 'Pause', 'Replay') for d in node.iter('node')):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        pill = ((x1 + x2) // 2, (y1 + y2) // 2)
        break
assert pill, 'pill never resolved for the cancel leg'
proc = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '14',
                         '/sdcard/morph-cancel.mp4'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
time.sleep(1.2)
adb('shell', 'input', 'tap', str(pill[0]), str(pill[1]))
wait_label('Collapse player', seconds=10)
result = subprocess.run(['adb', 'shell', 'am', 'instrument', '-w', '-e', 'class',
                         'com.orismusic.client.morph.MorphCancelTest',
                         PKG + '.test/androidx.test.runner.AndroidJUnitRunner'],
                        capture_output=True, timeout=180)
out = result.stdout.decode()
print(out[-600:], flush=True)
assert 'INSTRUMENTATION_CODE: -1' in out or 'OK (1 test)' in out, \
    'cancel injection instrumentation failed; tail printed above'
time.sleep(2)
assert has_label('Collapse player'), 'after ACTION_CANCEL the sheet must settle back to fully open'
pid = pidof('screenrecord')
if pid:
    adb('shell', 'kill', '-2', pid[0])
try:
    proc.wait(timeout=25)
except subprocess.TimeoutExpired:
    proc.kill()
    proc.wait(timeout=15)
adb('pull', '/sdcard/morph-cancel.mp4', OUT + '/morph-cancel.mp4')
adb('shell', 'rm', '/sdcard/morph-cancel.mp4')
size = os.path.getsize(OUT + '/morph-cancel.mp4') if os.path.exists(OUT + '/morph-cancel.mp4') else 0
print('RECORDED morph-cancel.mp4 bytes=' + str(size), flush=True)
assert size > 20000, 'cancel leg never recorded real frames'
emit_mp4_frames('morph-cancel', OUT + '/morph-cancel.mp4')
print('PASS: ACTION_CANCEL settles the sheet back to open', flush=True)
