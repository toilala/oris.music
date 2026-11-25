#!/usr/bin/env python3
"""Bounded save-for-offline product slice, LOCAL FIXTURE evidence only: a generated
tone served over file:// drives the real cache write, state lifecycle, restart
validation, source-deleted offline playback with progression, the real Library Saved
row/actions and removal. Nothing here is proof that a YouTube download works; live
resolution stays out of scope. Radios are cut and dumpsys connectivity is the OS
evidence before and after. Cancel/retry are explicitly untested end to end here: a
32KB local fixture finishes before a cancel can land, and the unit tests added with
this slice cover status derivation only, not CacheWriter cancellation or retry
execution."""
import glob
import re
import subprocess
import time

from playback_check import adb, tap, wait_label, screen, tree, OUT, PKG

MAIN = PKG + '/com.orismusic.client.MainActivity'
OFFLINE = PKG + '/com.orismusic.client.debug.OfflineCheckActivity'
TONE = 'Offline fixture tone'
SRC = 'cache/offline-fixture-tone.wav'


def connected_lines():
    conn = adb('shell', 'dumpsys', 'connectivity').decode()
    return conn, [line for line in conn.splitlines()
                  if re.search(r'\bCONNECTED\b', line) and 'DISCONNECTED' not in line]


def source_present():
    r = subprocess.run(['adb', 'shell', 'run-as', PKG, 'ls', SRC], capture_output=True)
    return r.returncode == 0


def gone(label, seconds=25):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        labels = [n.get('text') for n in tree().iter('node')] + \
                 [n.get('content-desc') for n in tree().iter('node')]
        if label not in labels:
            return
        time.sleep(1)
    screen('failure')
    raise AssertionError('UI still showed ' + repr(label))


def session_position(fail=True):
    """State and position of the media session whose metadata names the fixture tone.
    The PlaybackState line sits inside the same session record a few lines above the
    metadata line. Sampling any other session would prove nothing about this player,
    so a missing match fails rather than falling back."""
    dump = adb('shell', 'dumpsys', 'media_session').decode()
    lines = dump.splitlines()
    for i, line in enumerate(lines):
        if TONE in line:
            for j in range(i, max(-1, i - 30), -1):
                m = re.search(r'state=(\d+), position=(\d+)', lines[j])
                if m:
                    return int(m.group(1)), int(m.group(2))
    if fail:
        open(OUT + '/media-session-dump.txt', 'w').write(dump)
        raise AssertionError('no media session owns the fixture tone; dump saved to evidence')
    return None


adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
adb('shell', 'svc', 'wifi', 'disable')
adb('shell', 'svc', 'data', 'disable')
time.sleep(3)
conn, live = connected_lines()
open(OUT + '/connectivity-before.txt', 'w').write(conn)
assert not live, 'network still live, refusing to run: ' + repr(live[:2])
print('NETWORK-DISABLED: no connected network before the check (local-fixture evidence)',
      flush=True)
adb('install', '-r', glob.glob('app/build/outputs/apk/foss/debug/*.apk')[0])

# B. Real cache write and state lifecycle through the real CacheWriter over file://.
adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'am', 'start', '-n', OFFLINE)
wait_label('Offline state: NOT SAVED', seconds=30)
tap('Download fixture')
wait_label('Offline state: READY', seconds=60)
screen('offline_ready')
print('CACHE-WRITE: download reached READY over the local fixture', flush=True)

# C. Restart validation: READY must come back from the persisted spans alone.
adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'am', 'start', '-n', OFFLINE)
wait_label('Offline state: READY', seconds=60)
screen('offline_ready_after_restart')
print('RESTART: READY revalidated from the cache after a process restart', flush=True)

# D. Source-deleted playback with progression. The activity's onCreate rewrites the
# tone file, so nothing may relaunch it between the deletion and this proof.
subprocess.run(['adb', 'shell', 'run-as', PKG, 'rm', SRC], capture_output=True)
assert not source_present(), 'fixture source still present after deletion'
print('SOURCE-ABSENT before playback: the original tone file is gone; only the cache '
      'can serve playback', flush=True)
# The activity's status ticker rewrites its line every 400ms, so a PLAYING text would
# be raced away; the owned-session state and advancing position are the real evidence.
tap('Play offline')
# Progression from the owned session's position, which needs no state-name mapping:
# two samples 800ms apart must advance, or the 2s fixture may legitimately have played
# to its end between samples, which a position near the full length shows.
found = None
end = time.monotonic() + 30
while time.monotonic() < end:
    found = session_position(fail=False)
    if found and found[1] > 0:
        break
    time.sleep(1)
if not found:
    session_position()  # raises with the dump saved
state1, pos1 = found
time.sleep(0.8)
state2, pos2 = session_position()
if pos2 > pos1:
    print('PLAYBACK: fixture tone session position %d -> %d ms (state %d -> %d) with '
          'the source deleted and the network off' % (pos1, pos2, state1, state2), flush=True)
else:
    assert pos2 >= 1900, \
        'playback neither progressed nor completed: state %s -> %s, position %s -> %s' \
        % (state1, state2, pos1, pos2)
    print('PLAYBACK: fixture tone played to its end (%d ms of the 2s fixture) between '
          'samples, source deleted and network off; mid-play progression not sampled'
          % pos2, flush=True)
screen('offline_playing')
assert not source_present(), 'fixture source reappeared during playback'
print('SOURCE-ABSENT after playback: playback did not regenerate the source', flush=True)

# E. Real Library Saved row and overflow actions, separate from the debug activity.
adb('shell', 'am', 'start', '-n', MAIN, '-f', '0x04000000')
wait_label('Library', seconds=30)
tap('Library')
tap('Saved (1)')
wait_label(TONE)
wait_label('Saved for offline')
screen('library_saved_ready')
tap('Add to playlist')
wait_label('Remove from offline')
labels = [n.get('text') for n in tree().iter('node')]
assert 'Cancel download' not in labels and 'Retry download' not in labels, \
    'READY row must not offer Cancel or Retry: ' + repr(labels)
screen('library_menu_ready')
print('LIBRARY: Saved row shows the ready icon and only Remove for a READY copy',
      flush=True)
tap('Remove from offline')
# The tone may keep playing from already-decoded audio, so its title can stay in the
# mini player; the removal evidence is the saved-state icon leaving the row.
gone('Saved for offline')
screen('library_removed')
print('LIBRARY: removal cleared the row saved-state', flush=True)

# F. Removal is real: the debug surface agrees the copy is gone.
adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'am', 'start', '-n', OFFLINE)
wait_label('Offline state: NOT SAVED', seconds=30)
screen('offline_removed')
print('REMOVAL: cache and saved state gone after Remove from offline', flush=True)

conn, live = connected_lines()
open(OUT + '/connectivity-after.txt', 'w').write(conn)
assert not live, 'network RECONNECTED during the session: ' + repr(live[:2])
print('NETWORK-DISABLED: still no connected network after the check', flush=True)
print('PASS: save-for-offline slice on the local fixture (cache write, restart '
      'validation, source-deleted progressing playback, Library row/actions, removal); '
      'cancel/retry not exercised end to end by design', flush=True)
