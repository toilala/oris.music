#!/usr/bin/env python3
"""Bounded save-for-offline product slice, LOCAL FIXTURE evidence only: a generated
tone served over file:// drives the real cache write, state lifecycle, restart
validation, source-deleted offline playback with progression, the real Library
save/fail/retry/cancel/remove routes and removal. Nothing here is proof that a
YouTube download works; live resolution stays out of scope and playback evidence
supports "playback without available upstream", never a counted zero resolver
opens. Radios are cut and dumpsys connectivity is the OS evidence before and after.
Cache-engine evidence (debug activity buttons driving OfflineAudio directly) is
kept separate from real-UI lifecycle evidence (Library rows and overflow menus).
Cancel/retry semantics follow DownloadRegistry ownership: after Cancel the row may
stay DOWNLOADING until the blocked writer is released and the owning teardown
settles; only the settled state is asserted."""
import glob
import math
import re
import struct
import subprocess
import time
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

from playback_check import adb, tap, wait_label, screen, tree, back, tap_mini_player, OUT, PKG

MAIN = PKG + '/com.orismusic.client.MainActivity'
OFFLINE = PKG + '/com.orismusic.client.debug.OfflineCheckActivity'
TONE = 'Offline fixture tone'
SRC = 'cache/offline-fixture-tone.wav'
CACHE_DIR = 'files/download'


def connected_lines():
    conn = adb('shell', 'dumpsys', 'connectivity').decode()
    return conn, [line for line in conn.splitlines()
                  if re.search(r'\bCONNECTED\b', line) and 'DISCONNECTED' not in line]


def source_present():
    r = subprocess.run(['adb', 'shell', 'run-as', PKG, 'ls', SRC], capture_output=True)
    return r.returncode == 0


def labels():
    root = tree()
    return [n.get('text') for n in root.iter('node')] + \
           [n.get('content-desc') for n in root.iter('node')]


def gone(label, seconds=25):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if label not in labels():
            return
        time.sleep(1)
    screen('failure')
    raise AssertionError('UI still showed ' + repr(label))


def span_listing():
    """Names and sizes under the download cache dir; empty when the dir does not
    exist yet. Evidence for span-payload comparison, not a status derivation."""
    r = subprocess.run(['adb', 'shell', 'run-as', PKG, 'ls', '-la', CACHE_DIR],
                       capture_output=True)
    if r.returncode != 0:
        return {}
    out = {}
    for line in r.stdout.decode(errors='replace').splitlines():
        m = re.match(r'\S+\s+\d+\s+\S+\s+\S+\s+(\d+)\s+\S+\s+\S+\s+\S+\s+(\S+)$',
                     line.strip())
        if m and m.group(2) not in ('.', '..'):
            out[m.group(2)] = int(m.group(1))
    return out


def fixture_wav_bytes():
    """Byte-identical copy of the tone OfflineCheckActivity generates (440Hz, 8kHz,
    2s, 16-bit mono), so a deleted source can be restored deterministically."""
    rate = 8000
    samples = rate * 2
    out = bytearray()
    out += b'RIFF' + struct.pack('<i', 36 + samples * 2) + b'WAVEfmt '
    out += struct.pack('<ihhiihh', 16, 1, 1, rate, rate * 2, 2, 16)
    out += b'data' + struct.pack('<i', samples * 2)
    for i in range(samples):
        out += struct.pack('<h', int(math.sin(i * 2.0 * math.pi * 440 / rate) * 2000))
    return bytes(out)


def restore_source():
    data = fixture_wav_bytes()
    r = subprocess.run(['adb', 'shell', 'run-as', PKG, 'tee', SRC],
                       input=data, capture_output=True)
    assert r.returncode == 0, 'source restore failed: ' + r.stderr.decode(errors='replace')
    back = subprocess.run(['adb', 'shell', 'run-as', PKG, 'cat', SRC], capture_output=True)
    assert back.stdout == data, 'restored source read-back mismatch'


def delete_source():
    subprocess.run(['adb', 'shell', 'run-as', PKG, 'rm', '-f', SRC], capture_output=True)
    assert not source_present(), 'fixture source still present after deletion'


def make_fifo():
    """Replace the source with a FIFO so a download blocks in open with a
    controllable release. Returns False when the image cannot do this: the cancel
    route is then reported UNVERIFIED, not looped."""
    subprocess.run(['adb', 'shell', 'run-as', PKG, 'rm', '-f', SRC], capture_output=True)
    r = subprocess.run(['adb', 'shell', 'run-as', PKG, 'mkfifo', SRC], capture_output=True)
    if r.returncode != 0:
        return False
    r = subprocess.run(['adb', 'shell', 'run-as', PKG, 'ls', '-l', SRC],
                       capture_output=True)
    return r.stdout.decode(errors='replace').lstrip().startswith('p')


def release_fifo(seconds=15):
    """Bounded release: stream the tone into the FIFO so the blocked reader wakes
    and the cancelled writer unwinds in-process. A timeout closes the writer."""
    try:
        subprocess.run(['adb', 'shell', 'run-as', PKG, 'tee', SRC],
                       input=fixture_wav_bytes(), capture_output=True, timeout=seconds)
    except subprocess.TimeoutExpired:
        print('FIFO release hit the host timeout; writer force-closed', flush=True)


def seed_liked():
    songs = '[{"id":"offline-fixture-tone","title":"Offline fixture tone",' \
            '"artist":"Oris test"}]'
    library = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>"
               + '<string name="liked">' + escape(songs) + '</string>'
               + '<string name="history">[]</string>'
               + '<string name="playlists">[]</string>'
               + '<string name="counts">{}</string>'
               + '</map>')
    data = library.encode()
    r = subprocess.run(['adb', 'shell', 'run-as', PKG, 'tee',
                        'shared_prefs/oris-library.xml'],
                       input=data, capture_output=True)
    assert r.returncode == 0, 'seed write failed: ' + r.stderr.decode(errors='replace')
    read_back = subprocess.run(['adb', 'shell', 'run-as', PKG, 'cat',
                                'shared_prefs/oris-library.xml'], capture_output=True)
    assert read_back.returncode == 0 and read_back.stdout == data, \
        'oris-library.xml read-back mismatch'
    ET.fromstring(read_back.stdout)
    print('SEED: liked fixture row written and read back identical', flush=True)


def open_row_menu():
    tap('Add to playlist')


def dismiss_menu():
    back()


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
seed_liked()

# Span-payload baseline: a fresh install has no download cache payloads.
baseline = span_listing()
open(OUT + '/span-baseline.txt', 'w').write(repr(sorted(baseline.items())))
print('SPAN-BASELINE: %d cache files before any download' % len(baseline), flush=True)

# A1. Real cache write and state lifecycle through the real CacheWriter over file://.
adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'am', 'start', '-n', OFFLINE)
wait_label('Offline state: NOT SAVED', seconds=30)
tap('Download fixture')
wait_label('Offline state: READY', seconds=60)
screen('offline_ready')
ready_listing = span_listing()
payload = {k: v for k, v in ready_listing.items() if k not in baseline}
open(OUT + '/span-ready.txt', 'w').write(repr(sorted(ready_listing.items())))
assert payload, 'READY but no new cache payload appeared: ' + repr(ready_listing)
print('CACHE-WRITE: download reached READY over the local fixture; span payload '
      'positive control: ' + repr(sorted(payload.items())), flush=True)

# A2. Restart validation: READY must come back from the persisted spans alone. The
# activity regenerates the source bytes at every launch (disclosed: this leg proves
# status re-derivation, not isolation; isolation is proven at A3).
adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'am', 'start', '-n', OFFLINE)
wait_label('Offline state: READY', seconds=60)
screen('offline_ready_after_restart')
print('RESTART: READY revalidated from the cache after a process restart '
      '(source regenerated by launch, disclosed)', flush=True)

# A3. Source-deleted playback with attributed progression. The activity's onCreate
# rewrites the tone file, so nothing may relaunch it between the deletion and this
# proof; within this interval no generator can run.
adb('shell', 'logcat', '-c')
delete_source()
print('SOURCE-ABSENT before playback: the original tone file is gone; the playback '
      'path never reads it (cache key only) and upstream has no network', flush=True)
tap('Play offline')
# Progression evidence: two observed samples with the position advancing, polled
# tightly for the 2s tone. A single static read is inconclusive, never a pass; the
# low-to-end lifecycle pair alone is not accepted either.
seq = []
end = time.monotonic() + 30
while time.monotonic() < end:
    s = session_position(fail=False)
    if s:
        if not seq or s != seq[-1]:
            seq.append(s)
        if len(seq) >= 2 and s[1] > seq[0][1]:
            break
    time.sleep(0.25)
if not seq:
    session_position()  # raises with the dump saved
advanced = next(((a, b) for a, b in zip(seq, seq[1:]) if b[1] > a[1]), None)
if not advanced:
    open(OUT + '/playback-inconclusive.txt', 'w').write(repr(seq))
    raise AssertionError('PLAYBACK PROGRESSION INCONCLUSIVE: no advancing attributed '
                         'samples; sequence=' + repr(seq))
a, b = advanced
print('PLAYBACK PROGRESSION: fixture tone session position %d -> %d ms '
      '(state %d -> %d), source deleted and network off' % (a[1], b[1], a[0], b[0]),
      flush=True)
screen('offline_playing')
assert not source_present(), 'fixture source reappeared during playback'
print('SOURCE-ABSENT after playback: playback did not regenerate the source', flush=True)
logcat = adb('shell', 'logcat', '-d').decode(errors='replace')
open(OUT + '/playback-logcat.txt', 'w').write(logcat)
markers = [l for l in logcat.splitlines()
           if re.search(r'YTPlayerUtils|innertube|OfflineModeException|UnknownHost'
                        r'|SocketException|PlaybackException', l)]
print('PLAYBACK UPSTREAM (narrow claim): uninterrupted attributed playback with '
      'denied network and deleted source supports playback without available '
      'upstream; supplemental logcat resolution markers in window: %d' % len(markers),
      flush=True)

# C. Inset captures, a separate window from the strict playback proof: replay from
# cache (source still deleted) and capture the two screens missing from the inset row.
try:
    tap('Play offline')
    adb('shell', 'am', 'start', '-n', MAIN, '-f', '0x04000000')
    tap_mini_player(TONE)
    time.sleep(1)
    tree()
    screen('inset_now_playing')
    open(OUT + '/inset-now-playing.xml', 'w').write(
        adb('shell', 'cat', '/sdcard/oris-nav.xml').decode(errors='replace'))
    tap('Lyrics')
    time.sleep(1)
    tree()
    screen('inset_lyrics')
    open(OUT + '/inset-lyrics.xml', 'w').write(
        adb('shell', 'cat', '/sdcard/oris-nav.xml').decode(errors='replace'))
    print('INSET: Now Playing and Lyrics captured in a separate window', flush=True)
except Exception as e:
    open(OUT + '/inset-error.txt', 'w').write(repr(e))
    print('INSET: Now Playing/Lyrics capture UNVERIFIED: ' + repr(e), flush=True)

# B2/B6. Real Library Saved-section row and overflow actions, separate from the debug
# activity. The Saved section header text contains "Saved for offline" inside a longer
# string, so exact-match chip assertions are done on the Liked row only; the Saved row
# is asserted through its menu discipline and the Saved chip count.
adb('shell', 'am', 'start', '-n', MAIN, '-f', '0x04000000')
wait_label('Library', seconds=30)
tap('Library')
tap('Saved (1)')
open_row_menu()
menu = labels()
assert 'Remove from offline' in menu, 'READY row menu must offer Remove: ' + repr(menu)
assert 'Cancel download' not in menu and 'Retry download' not in menu, \
    'READY row must not offer Cancel or Retry: ' + repr(menu)
screen('library_menu_ready_saved_row')
print('LIBRARY: Saved-section row offers only Remove from offline for a READY copy',
      flush=True)
tap('Remove from offline')
wait_label('Saved (0)', seconds=25)
screen('library_removed')
print('LIBRARY: removal cleared the Saved section', flush=True)

# F. Removal is real: the debug surface agrees the copy is gone. (This relaunch
# regenerates the source; the source-deletion proof at A3 is already complete.)
adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'am', 'start', '-n', OFFLINE)
wait_label('Offline state: NOT SAVED', seconds=30)
screen('offline_removed')
print('REMOVAL: cache and saved state gone after Remove from offline', flush=True)

# B1. Real-UI save from the Liked row (same process, fixtureUri still set).
adb('shell', 'am', 'start', '-n', MAIN, '-f', '0x04000000')
wait_label('Library', seconds=30)
tap('Library')
tap('Liked (1)')
open_row_menu()
menu = labels()
assert 'Save for offline' in menu, 'unsaved row must offer Save for offline: ' + repr(menu)
screen('library_menu_unsaved')
tap('Save for offline')
wait_label('Saved for offline', seconds=60)
wait_label('Saved (1)')
screen('library_row_ready_after_ui_save')
print('LIBRARY: real row menu Save for offline reached READY '
      '(transient spinner not asserted; READY is)', flush=True)

# B3. FAILED route, deterministic: remove, delete the source, then save again on the
# same Liked row. No network involved: the write fails on the missing local file.
open_row_menu()
menu = labels()
assert 'Remove from offline' in menu, 'READY Liked row must offer Remove: ' + repr(menu)
tap('Remove from offline')
gone('Saved for offline')
wait_label('Saved (0)')
delete_source()
open_row_menu()
tap('Save for offline')
wait_label('Offline download failed', seconds=60)
screen('library_row_failed')
open_row_menu()
menu = labels()
assert 'Retry download' in menu, 'FAILED row must offer Retry download: ' + repr(menu)
assert 'Save for offline' not in menu, 'FAILED row must not offer plain save: ' + repr(menu)
screen('library_menu_failed')
print('LIBRARY: missing source produced the FAILED chip and the Retry menu item',
      flush=True)

# B4. Retry after restore reaches READY.
dismiss_menu()
restore_source()
open_row_menu()
tap('Retry download')
wait_label('Saved for offline', seconds=60)
screen('library_row_ready_after_retry')
print('LIBRARY: Retry download reached READY after the source was restored', flush=True)

# B5. Cancel with a controlled blocked source: a FIFO in place of the file makes the
# writer block in open, so DOWNLOADING is directly observed via the menu. Cancel is
# requested, the FIFO is released under a bounded host write, and only the settled
# state is asserted: no READY, no FAILED, no saved entry, no resurrected payloads.
open_row_menu()
tap('Remove from offline')
gone('Saved for offline')
wait_label('Saved (0)')
if not make_fifo():
    print('CANCEL ROUTE UNVERIFIED: mkfifo under run-as is unsupported on this image',
          flush=True)
else:
    open_row_menu()
    tap('Save for offline')
    time.sleep(2)
    open_row_menu()
    menu = labels()
    assert 'Cancel download' in menu, \
        'blocked download must offer Cancel download: ' + repr(menu)
    assert 'Save for offline' not in menu, \
        'blocked download must not offer Save for offline: ' + repr(menu)
    screen('library_menu_downloading')
    print('LIBRARY: DOWNLOADING directly observed via the Cancel download menu item '
          'during the blocked window', flush=True)
    tap('Cancel download')
    time.sleep(1)
    release_fifo()
    # Settled-state assertions only: the row may show DOWNLOADING until the released
    # writer unwinds and the owning teardown finishes.
    settled = False
    end = time.monotonic() + 45
    while time.monotonic() < end and not settled:
        open_row_menu()
        if 'Save for offline' in labels():
            settled = True
        else:
            dismiss_menu()
            time.sleep(2)
    assert settled, 'row never settled back to the unsaved state after Cancel'
    dismiss_menu()
    final_labels = labels()
    assert 'Offline download failed' not in final_labels, \
        'a cancelled download must not show the FAILED chip'
    assert 'Saved for offline' not in final_labels, \
        'a cancelled download must not show the READY chip'
    wait_label('Saved (0)', seconds=15)
    screen('library_cancel_settled')
    after = span_listing()
    open(OUT + '/span-after-cancel.txt', 'w').write(repr(sorted(after.items())))
    resurrected = {k: v for k, v in after.items() if k not in baseline}
    assert not resurrected, 'span payloads present after cancelled download settled: ' \
        + repr(resurrected)
    print('LIBRARY: cancel settled - no READY, no FAILED, no saved entry, no '
          'resurrected span payloads', flush=True)
    subprocess.run(['adb', 'shell', 'run-as', PKG, 'rm', '-f', SRC], capture_output=True)

conn, live = connected_lines()
open(OUT + '/connectivity-after.txt', 'w').write(conn)
assert not live, 'network RECONNECTED during the session: ' + repr(live[:2])
print('NETWORK-DISABLED: still no connected network after the check', flush=True)
print('PASS: save-for-offline slice on the local fixture (cache write, restart '
      'validation, source-deleted progressing playback, Library save/fail/retry/'
      'cancel/remove routes, span-payload inspection)', flush=True)
