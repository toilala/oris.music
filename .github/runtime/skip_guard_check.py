#!/usr/bin/env python3
"""Skip-walk guard service-path check on deterministic local files, no network involved.
Queue driven by SkipGuardCheckActivity: bad1 bad2 good1 bad3 bad4 bad5 good2, where bad
items are missing local files and good items are a generated tone.

Expected evidence: bad1/bad2 fail and auto-advance (streak 1, 2, no pause); good1 starts
and resets the nonzero streak; bad3/bad4 fail and advance WITHOUT an early pause; bad5's
failure pauses instead of advancing to good2; a real banner Retry tap in the app UI (not a
programmatic playWhenReady) retries bad5, which fails once more and advances to good2."""
import re
import time
from playback_check import adb, screen, tree, wait_label, PKG

COMPONENT = PKG + '/com.orismusic.client.debug.SkipGuardCheckActivity'
MAIN = PKG + '/com.orismusic.client.MainActivity'
PAUSE_MSG = 'Playback paused: 3 songs failed in a row'


def pause_count(log):
    """Count canonical guard-pause report lines: logcat lines carrying the pause message,
    excluding the warn-level 'failure active:' mirror that reportFailure emits for the
    same event (one real pause logs one line of each kind)."""
    return sum(1 for line in log.splitlines() if PAUSE_MSG in line and 'failure active' not in line)


def resumed_activity():
    out = adb('shell', 'dumpsys', 'activity', 'activities').decode()
    return out.split('mResumedActivity:')[-1].splitlines()[0]


def resume_main():
    """Bring MainActivity forward and verify the resumed activity before touching anything.
    Uses mResumedActivity (the same dumpsys parse as playback_check.still_open), never a
    focus-window regex, and never force-stops the app: the live audio session under test
    must survive navigation."""
    for _ in range(3):
        # CLEAR_TOP pops the check activity sitting above MainActivity in the one task.
        adb('shell', 'am', 'start', '-n', MAIN, '-f', '0x04000000')
        for _ in range(40):
            resumed = resumed_activity()
            if PKG in resumed and 'MainActivity' in resumed:
                return
            time.sleep(0.5)
    screen('failure')
    raise AssertionError('MainActivity never resumed to the foreground')


def tap_clickable(label):
    """Tap the clickable ancestor of the node carrying label, so taps never land on a
    plain Text sibling of the real control."""
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


def status_text():
    for n in tree().iter('node'):
        t = n.get('text') or ''
        if t.startswith('item=sg-'):
            return t
    return ''


def wait_status(fragment, seconds):
    end = time.monotonic() + seconds
    last = ''
    while time.monotonic() < end:
        last = status_text()
        if fragment in last:
            print('STATUS ' + last, flush=True)
            return last
        time.sleep(1)
    screen('failure')
    raise AssertionError('skip-guard status never showed ' + repr(fragment) + ' (last: ' + repr(last) + ')')


def diag_log():
    return adb('logcat', '-d', '-s', 'OrisDiag').decode()


adb('shell', 'am', 'start', '-n', COMPONENT)
wait_status('item=sg-', 60)

# Phase 1: two failures auto-advance, then the good tone starts playing. This is the
# success-after-nonzero-streak case: the reset is proven by phase 2 not pausing early.
wait_status('item=sg-good-1 playing=true', 150)
log = diag_log()
assert 'playback started: sg-good-1' in log, 'good-1 start not logged'
assert PAUSE_MSG not in log, 'guard paused before any three-failure streak'
screen('skip_guard_after_success_reset')

# Phase 2: three more failures. No pause at streak 1 or 2 (success reset held); the third
# failure pauses instead of advancing to good2.
wait_status('item=sg-bad-5', 180)
wait_status('pwr=false', 60)
screen('skip_guard_paused')
# Bounded no-advance observation: the guard must HOLD the pause, not resume or skip on.
time.sleep(20)
held = status_text()
print('STATUS after 20s hold: ' + held, flush=True)
log = diag_log()
pauses = pause_count(log)
assert pauses == 1, 'expected exactly one guard pause, got ' + str(pauses)
assert 'item=sg-bad-5' in held and 'pwr=false' in held, 'guard pause did not hold for 20s: ' + held
assert 'playback started: sg-good-2' not in log, 'queue advanced past the guard pause'

# Phase 3: a real banner Retry tap in the app UI recovers. The mini player pill opens Now
# Playing; the banner's Retry button prepares and plays the failed item, which exhausts
# once more (streak 1 after the pause-time reset) and advances to good2.
resume_main()
tap_clickable('SkipGuard bad 5')
tap_clickable('Retry')
end = time.monotonic() + 150
recovered = False
while time.monotonic() < end:
    if 'playback started: sg-good-2' in diag_log():
        recovered = True
        break
    time.sleep(2)
screen('skip_guard_recovered')
log = diag_log()
open('runtime-evidence/skip-guard-logcat.txt', 'w').write(log)
assert recovered, 'banner Retry never recovered to sg-good-2'
assert log.count(PAUSE_MSG) == 1, 'the retried failure must not re-pause: the pause resets the streak'
print('PASS: skip-walk guard (advance x2, success reset, no early pause, pause at 3, banner Retry recovery)', flush=True)
