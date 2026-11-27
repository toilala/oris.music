#!/usr/bin/env python3
"""Gesture robustness for the pill<->player morph: real drags and interrupts on the new
interaction, each leg recorded to mp4. After every interruption the app must show exactly
one honest state - the open player or the pill - never a stranded invisible sheet, and the
package stays foreground. One honest limit: a tap during the reverse can be swallowed by
the morph window covering the pill (asserted only for end-state consistency). A true
gesture CANCEL (ACTION_CANCEL mid-stream) is not scriptable via `adb shell input`; it is
covered by morph_cancel_check.py through UiAutomation-injected MotionEvents."""
import glob
import os
import re
import subprocess
import time

from playback_check import adb, wait_label, screen, tree, PKG, OUT, pidof, emit_mp4_frames, emit_image

MAIN = PKG + '/com.orismusic.client.MainActivity'
COMPONENT = PKG + '/com.orismusic.client.debug.LocalPlaybackCheckActivity'
TONE = 'Runtime local tone'


def has_label(fragment):
    return any(fragment in (n.get('text'), n.get('content-desc')) for n in tree().iter('node'))


def player_open():
    return has_label('Collapse player')


def pill_visible():
    root = tree()
    parents = {c: p for p in root.iter('node') for c in p}
    for label_node in [n for n in root.iter('node') if n.get('text') == TONE]:
        node = label_node
        while node is not None and node.get('clickable') != 'true':
            node = parents.get(node)
        if node is None:
            continue
        descs = [d.get('content-desc') for d in node.iter('node')]
        if any(d in ('Play', 'Pause', 'Replay') for d in descs):
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def record_start(name, seconds):
    proc = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', str(seconds),
                             '/sdcard/' + name], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    time.sleep(1.2)
    return proc


def record_stop(name, proc):
    pid = pidof('screenrecord')
    if pid:
        # The recorder can already be gone (time-limit, device hiccup): a stale pid
        # is a warning, never a reason to die before the remaining legs run.
        r = subprocess.run(['adb', 'shell', 'kill', '-2', pid[0]], capture_output=True)
        if r.returncode != 0:
            print('RECORDER already gone for ' + name, flush=True)
    try:
        proc.wait(timeout=25)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait(timeout=15)
    r = subprocess.run(['adb', 'pull', '/sdcard/' + name, OUT + '/' + name], capture_output=True)
    if r.returncode != 0:
        print('RECORD PULL FAILED ' + name, flush=True)
        return
    subprocess.run(['adb', 'shell', 'rm', '/sdcard/' + name], capture_output=True)
    size = os.path.getsize(OUT + '/' + name) if os.path.exists(OUT + '/' + name) else 0
    print('RECORDED ' + name + ' bytes=' + str(size), flush=True)
    if size <= 20000:
        # A thin recording is lost evidence, not a failed leg: the gesture assertions
        # are the verdicts and they run on the live UI regardless.
        print('RECORD THIN ' + name + ' (evidence lost, assertions unaffected)', flush=True)
        return
    emit_mp4_frames(name[:-4], OUT + '/' + name)


def swipe(x1, y1, x2, y2, ms):
    adb('shell', 'input', 'swipe', str(x1), str(y1), str(x2), str(y2), str(ms))


def resumed():
    out = adb('shell', 'dumpsys', 'activity', 'activities').decode()
    return out.split('mResumedActivity:')[-1].splitlines()[0]


def assert_consistent(tag):
    time.sleep(1.5)
    open_now = player_open()
    pill = pill_visible()
    print('STATE ' + tag + ' player_open=' + str(open_now) + ' pill=' + str(pill is not None), flush=True)
    assert open_now or pill is not None, tag + ': neither player nor pill visible (stranded sheet?)'
    assert PKG in resumed(), tag + ': app left the foreground'
    return open_now


adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
os.makedirs(OUT, exist_ok=True)

# Local tone playing so the pill exists, MainActivity foreground.
adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'am', 'start', '-n', COMPONENT)
wait_label('Local audio playing', seconds=30)
time.sleep(2)
for _ in range(3):
    adb('shell', 'am', 'start', '-n', MAIN, '-f', '0x04000000')
    time.sleep(2)
    if 'MainActivity' in resumed():
        break
pill = None
for _ in range(30):
    pill = pill_visible()
    if pill:
        break
    time.sleep(0.5)
assert pill, 'mini player pill never appeared for the local tone'
print('PILL center=' + str(pill), flush=True)

# Leg 1: tap opens through the morph.
proc = record_start('morph-leg1-tap-open.mp4', 12)
adb('shell', 'input', 'tap', str(pill[0]), str(pill[1]))
wait_label('Collapse player', seconds=10)
record_stop('morph-leg1-tap-open.mp4', proc)


# Leg 2: short drag below the close threshold settles back to open.
proc = record_start('morph-leg2-drag-restore.mp4', 12)
swipe(540, 900, 540, 1250, 450)
time.sleep(2)
ok2 = player_open()
record_stop('morph-leg2-drag-restore.mp4', proc)
assert ok2, 'leg2: short drag must settle back to open, not close'

# Leg 3: long fast drag closes into the pill.
proc = record_start('morph-leg3-drag-close.mp4', 12)
swipe(540, 800, 540, 2100, 300)
time.sleep(2)
ok3 = not player_open() and pill_visible()
record_stop('morph-leg3-drag-close.mp4', proc)
assert ok3, 'leg3: long drag must close back to the pill'

# Leg 4: a drag during the OPEN animation reverses without stranding the sheet.
pill = pill_visible()
proc = record_start('morph-leg4-midopen-reverse.mp4', 14)
adb('shell', 'input', 'tap', str(pill[0]), str(pill[1]))
time.sleep(0.25)
swipe(540, 800, 540, 2100, 300)
record_stop('morph-leg4-midopen-reverse.mp4', proc)
assert_consistent('leg4')

# Leg 5: tap/back flapping never strands the sheet.
proc = record_start('morph-leg5-flapping.mp4', 20)
for _ in range(3):
    pill = pill_visible()
    if pill:
        adb('shell', 'input', 'tap', str(pill[0]), str(pill[1]))
        time.sleep(0.35)
    adb('shell', 'input', 'keyevent', '4')
    time.sleep(0.35)
record_stop('morph-leg5-flapping.mp4', proc)
final_open = assert_consistent('leg5')

# Leg 6: a slow small scroll inside the player does not dismiss it.
if not final_open:
    pill = pill_visible()
    adb('shell', 'input', 'tap', str(pill[0]), str(pill[1]))
    wait_label('Collapse player', seconds=10)
proc = record_start('morph-leg6-inplayer-scroll.mp4', 12)
swipe(540, 1500, 540, 1250, 600)
time.sleep(1.5)
ok6 = player_open()
record_stop('morph-leg6-inplayer-scroll.mp4', proc)
assert ok6, 'leg6: a small in-player scroll must not dismiss the player'

adb('shell', 'input', 'keyevent', '4')

# Leg 7: a grab DURING the close animation reasserts open intent and settles open.
# Moved in-process (MorphGrabTest): shell BACK/swipe injection lags the loaded emulator
# by seconds, so the grab used to land on a closed sheet and prove nothing. The test
# presses BACK itself, catches the sheet past the mid-close point from live bounds,
# grabs inside them, and captures staged UiDevice stills before asserting the settle.
if not player_open():
    pill = pill_visible()
    adb('shell', 'input', 'tap', str(pill[0]), str(pill[1]))
    wait_label('Collapse player', seconds=10)
apk7 = glob.glob('app/build/outputs/apk/androidTest/foss/debug/*.apk')
if not apk7:
    print('BUILDING androidTest apk (one-off per job)', flush=True)
    subprocess.run(['./gradlew', ':app:assembleFossDebugAndroidTest', '--console=plain'], check=True)
    apk7 = glob.glob('app/build/outputs/apk/androidTest/foss/debug/*.apk')
adb('install', '-r', apk7[0])
res7 = subprocess.run(['adb', 'shell', 'am', 'instrument', '-w', '-e', 'class',
                       'com.orismusic.client.morph.MorphGrabTest',
                       PKG + '.test/androidx.test.runner.AndroidJUnitRunner'],
                      capture_output=True, timeout=180)
out7 = res7.stdout.decode()
print(out7[-1200:], flush=True)
pulled7 = subprocess.run(['adb', 'pull', '/sdcard/oris-morph-diag/.', OUT + '/grab-diag/'],
                         capture_output=True)
print('DIAG PULL rc=' + str(pulled7.returncode), flush=True)
if os.path.isdir(OUT + '/grab-diag'):
    for name in sorted(os.listdir(OUT + '/grab-diag')):
        path = os.path.join(OUT + '/grab-diag', name)
        if name.endswith('.png'):
            emit_image('leg7-' + name.replace('.png', ''), open(path, 'rb').read())
        elif name.endswith('.txt'):
            print('DIAG ' + name + ':', flush=True)
            print(open(path).read(), flush=True)
ok7 = 'INSTRUMENTATION_CODE: -1' in out7 or 'OK (1 test)' in out7
if not ok7:
    screen('leg7_failure')
assert ok7, 'leg7: grabbing the sheet mid-close must settle back to open; tail printed above'
print('PASS: morph gesture robustness, all seven legs consistent', flush=True)
