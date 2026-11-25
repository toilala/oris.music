#!/usr/bin/env python3
"""Terminal-stop service-path check on a deterministic local file, no network involved.
A single missing local file (sg-bad-6) is queued via SkipGuardCheckActivity --es queue
single. With no next item there is nothing to walk to: after its retries the player must
pause on the failed song with the terminal-stop reason, never the three-in-a-row guard
message, and a real banner Retry tap must run the explicit retry and stop the same way."""
import re
import time
from playback_check import adb, screen, tree, wait_label, PKG

COMPONENT = PKG + '/com.orismusic.client.debug.SkipGuardCheckActivity'
MAIN = PKG + '/com.orismusic.client.MainActivity'
TERMINAL_MSG = 'Playback stopped: this song failed to load'
PAUSE_MSG = 'Playback paused: 3 songs failed in a row'


def count_msg(log, msg):
    """Count canonical report lines carrying msg, excluding the warn-level 'failure
    active:' mirror that reportFailure emits for the same event."""
    return sum(1 for line in log.splitlines() if msg in line and 'failure active' not in line)


def resumed_activity():
    out = adb('shell', 'dumpsys', 'activity', 'activities').decode()
    return out.split('mResumedActivity:')[-1].splitlines()[0]


def resume_main():
    """Bring MainActivity forward and verify the resumed activity before touching anything.
    Same mResumedActivity parse as playback_check.still_open; never force-stops the app."""
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


def tap_mini_player(title):
    """Tap the mini-player pill: the clickable ancestor of the title text whose subtree
    owns a transport button (content-desc Play/Pause/Replay). Home's 'Continue listening'
    row carries the same title but no transport buttons, so title alone is ambiguous."""
    end = time.monotonic() + 30
    while time.monotonic() < end:
        root = tree()
        parents = {c: p for p in root.iter('node') for c in p}
        for label_node in [n for n in root.iter('node') if n.get('text') == title]:
            node = label_node
            while node is not None and node.get('clickable') != 'true':
                node = parents.get(node)
            if node is None:
                continue
            descs = [d.get('content-desc') for d in node.iter('node')]
            if any(d in ('Play', 'Pause', 'Replay') for d in descs):
                x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
                print('TAP mini player ' + title + ' bounds=' + node.get('bounds'), flush=True)
                adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
                return
        time.sleep(1)
    screen('failure')
    raise AssertionError('mini player pill for ' + title + ' never found')


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
    raise AssertionError('terminal-stop status never showed ' + repr(fragment) + ' (last: ' + repr(last) + ')')


def diag_log():
    return adb('logcat', '-d', '-s', 'OrisDiag').decode()


adb('shell', 'am', 'start', '-n', COMPONENT, '--es', 'queue', 'single')
wait_status('item=sg-bad-6', 60)
base_log = diag_log()
term0 = count_msg(base_log, TERMINAL_MSG)
pause0 = count_msg(base_log, PAUSE_MSG)

# Phase 1: the single failing song must end paused with its own reason, bounded.
wait_status('pwr=false', 90)
screen('terminal_stop_paused')
time.sleep(10)
held = status_text()
print('STATUS after 10s hold: ' + held, flush=True)
screen('terminal_stop_held')
log = diag_log()
assert 'item=sg-bad-6' in held and 'pwr=false' in held, 'terminal stop did not hold for 10s: ' + held
assert count_msg(log, TERMINAL_MSG) == term0 + 1, 'expected exactly one terminal-stop reason'
assert count_msg(log, PAUSE_MSG) == pause0, 'a one-song queue must not claim a three-in-a-row streak'

# Phase 2: a real banner Retry tap is the explicit retry: it fails again and stops with
# the same reason, proving the stop stays recoverable.
resume_main()
tap_mini_player('SkipGuard bad 6')
wait_label('Collapse player', seconds=30)
tap_clickable('Retry')
end = time.monotonic() + 90
retried = False
while time.monotonic() < end:
    if count_msg(diag_log(), TERMINAL_MSG) >= term0 + 2:
        retried = True
        break
    time.sleep(2)
log = diag_log()
open('runtime-evidence/terminal-stop-logcat.txt', 'w').write(log)
screen('terminal_stop_retried')
assert retried, 'banner Retry never re-ran the failing song to a second terminal stop'
assert count_msg(log, PAUSE_MSG) == pause0, 'the retried failure must not claim a streak'
print('PASS: terminal stop (single song pauses with its own reason, no streak, banner Retry retries)', flush=True)
