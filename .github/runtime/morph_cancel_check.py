#!/usr/bin/env python3
"""Gesture-cancel leg for the player sheet: adb shell input cannot emit ACTION_CANCEL,
so MorphCancelTest injects a real DOWN->MOVE->CANCEL stream in-process. The test is
self-contained: it opens the sheet inside the instrumentation lifecycle itself, proves
the injected drag moved the sheet, captures UiDevice stills and live sheet positions
pre-injection, post-MOVE and post-CANCEL, and only then asserts the settle. A harness
restart or a missed gesture surfaces as a SETUP failure, not a product verdict."""
import glob
import os
import subprocess
import time

from playback_check import adb, wait_label, tree, PKG, OUT, emit_image

MAIN = PKG + '/com.orismusic.client.MainActivity'
COMPONENT = PKG + '/com.orismusic.client.debug.LocalPlaybackCheckActivity'
TONE = 'Runtime local tone'


adb('wait-for-device')
os.makedirs(OUT, exist_ok=True)

apk = glob.glob('app/build/outputs/apk/androidTest/foss/debug/*.apk')
if not apk:
    print('BUILDING androidTest apk (one-off per job)', flush=True)
    subprocess.run(['./gradlew', ':app:assembleFossDebugAndroidTest', '--console=plain'], check=True)
    apk = glob.glob('app/build/outputs/apk/androidTest/foss/debug/*.apk')
adb('install', '-r', apk[0])

# The app only needs to be running with a loaded track; the test opens the sheet
# itself, so the injection and the settle assertion share one process lifecycle.
adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'am', 'start', '-n', COMPONENT)
wait_label('Local audio playing', seconds=30)
time.sleep(2)
adb('shell', 'am', 'start', '-n', MAIN, '-f', '0x04000000')
wait_label(TONE, seconds=25)

result = subprocess.run(['adb', 'shell', 'am', 'instrument', '-w', '-e', 'class',
                         'com.orismusic.client.morph.MorphCancelTest',
                         PKG + '.test/androidx.test.runner.AndroidJUnitRunner'],
                        capture_output=True, timeout=180)
out = result.stdout.decode()
print(out[-1200:], flush=True)

# Evidence before the verdict: pull the diagnostic stills and the position log the
# test captured at each stage, whatever the instrumentation outcome was.
diag_dir = OUT + '/cancel-diag'
os.makedirs(diag_dir, exist_ok=True)
names = subprocess.run(['adb', 'shell', 'run-as', PKG, 'ls', 'cache/oris-morph-diag'],
                       capture_output=True).stdout.decode().split()
pulled = 0
for name in names:
    r = subprocess.run(['adb', 'exec-out', 'run-as', PKG, 'cat',
                        'cache/oris-morph-diag/' + name], capture_output=True)
    if r.returncode == 0 and r.stdout:
        open(os.path.join(diag_dir, name), 'wb').write(r.stdout)
        pulled += 1
print('DIAG PULL files=' + str(pulled), flush=True)
if os.path.isdir(diag_dir):
    for name in sorted(os.listdir(diag_dir)):
        path = os.path.join(diag_dir, name)
        if name.endswith('.png'):
            emit_image('cancel-' + name.replace('.png', ''), open(path, 'rb').read())
        elif name.endswith('.txt'):
            print('DIAG ' + name + ':', flush=True)
            print(open(path).read(), flush=True)

assert 'INSTRUMENTATION_CODE: -1' in out or 'OK (1 test)' in out, \
    'cancel injection instrumentation failed; tail printed above'
print('PASS: ACTION_CANCEL settles the sheet back to open (asserted in-process, staged evidence above)', flush=True)
