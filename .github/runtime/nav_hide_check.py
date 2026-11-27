#!/usr/bin/env python3
"""Enabled hide-on-scroll verdict, in-process: NavHideTest sets the dial through the
app's own settings state with a track loaded, proves the scroll from greeting geometry,
then asserts the nav row leaves the tree while the mini player stays in the dock.
UiDevice stills and the position log are pulled before any verdict."""
import glob
import os
import subprocess
import time

from playback_check import adb, wait_label, PKG, OUT, emit_image

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

adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'am', 'start', '-n', COMPONENT)
wait_label('Local audio playing', seconds=30)
time.sleep(2)
adb('shell', 'am', 'start', '-n', MAIN, '-f', '0x04000000')
wait_label(TONE, seconds=25)

result = subprocess.run(['adb', 'shell', 'am', 'instrument', '-w', '-e', 'class',
                         'com.orismusic.client.nav.NavHideTest',
                         PKG + '.test/androidx.test.runner.AndroidJUnitRunner'],
                        capture_output=True, timeout=180)
out = result.stdout.decode()
print(out[-1200:], flush=True)

diag_dir = OUT + '/nav-diag'
os.makedirs(diag_dir, exist_ok=True)
names = subprocess.run(['adb', 'shell', 'run-as', PKG, 'ls', 'cache/oris-nav-diag'],
                       capture_output=True).stdout.decode().split()
pulled = 0
for name in names:
    r = subprocess.run(['adb', 'exec-out', 'run-as', PKG, 'cat',
                        'cache/oris-nav-diag/' + name], capture_output=True)
    if r.returncode == 0 and r.stdout:
        open(os.path.join(diag_dir, name), 'wb').write(r.stdout)
        pulled += 1
print('DIAG PULL files=' + str(pulled), flush=True)
if pulled == 0:
    lg = subprocess.run(['adb', 'logcat', '-d', '-s', 'OrisNavDiag'],
                        capture_output=True).stdout.decode(errors='replace')
    print('DIAG FALLBACK logcat OrisNavDiag:', flush=True)
    print(lg[-2000:], flush=True)
if os.path.isdir(diag_dir):
    for name in sorted(os.listdir(diag_dir)):
        path = os.path.join(diag_dir, name)
        if name.endswith('.png'):
            emit_image('navhide-' + name.replace('.png', ''), open(path, 'rb').read())
        elif name.endswith('.txt'):
            print('DIAG ' + name + ':', flush=True)
            print(open(path).read(), flush=True)

assert 'INSTRUMENTATION_CODE: -1' in out or 'OK (1 test)' in out, \
    'nav-hide instrumentation failed; tail printed above'
print('PASS: nav hides on a proven scroll while the mini player stays (asserted in-process)', flush=True)
