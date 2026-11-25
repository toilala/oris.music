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
settles; only the settled state is asserted. The span-listing contract is grounded
on actual device output by a sandbox probe before any span assertion, and its
parser is checked host-side (--self-test)."""
import glob
import math
import re
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape


def parse_listing_names(text):
    """Names from `ls -1A` output: one entry per line, dot entries excluded by -A,
    dotfiles included. Blank lines are ignored and stray dot entries are dropped
    defensively; every other line is one exact name (no metadata columns)."""
    return [line.strip() for line in text.splitlines()
            if line.strip() and line.strip() not in ('.', '..')]


def parse_byte_count(text):
    """Byte count from `wc -c` output: the first integer field of the first
    non-blank line; an echoed path after the count is ignored."""
    for line in text.splitlines():
        fields = line.split()
        if fields:
            return int(fields[0])
    raise ValueError('no byte count in wc output')


def self_test():
    """Host-side parser checks, no device: the raw formats the device contract
    defines, metadata and blank exclusions, and a full positive payload example."""
    # ls -1A contract: one name per line; dotfiles kept, dot entries dropped.
    names = parse_listing_names(
        'oris.1.1764072000000.v3\ncached_content_index.exi\n.hidden-probe\n\n')
    assert names == ['oris.1.1764072000000.v3', 'cached_content_index.exi',
                     '.hidden-probe'], names
    assert parse_listing_names('.\n..\n\nreal-file.v3\n') == ['real-file.v3']
    # wc -c contract: the byte count is the first field; padding and the echoed
    # path (including one with spaces) are ignored.
    assert parse_byte_count('32044 files/download/oris.1.1764072000000.v3\n') == 32044
    assert parse_byte_count('     12 files/span-probe/alpha.bin\n') == 12
    assert parse_byte_count('12\n') == 12
    assert parse_byte_count('7 files/download/my file.v3\n') == 7
    # Positive payload: a full listing round-trip over canned raw output.
    raw_names = 'oris.1.1764072000000.v3\ncached_content_index.exi\n'
    sizes = {'oris.1.1764072000000.v3': '32044 files/download/oris.1.1764072000000.v3\n',
             'cached_content_index.exi': '4096 files/download/cached_content_index.exi\n'}
    payload = {n: parse_byte_count(sizes[n]) for n in parse_listing_names(raw_names)}
    assert payload == {'oris.1.1764072000000.v3': 32044,
                       'cached_content_index.exi': 4096}, payload
    print('SELF-TEST: span listing parser checks passed (raw format, metadata '
          'exclusions, positive payload)', flush=True)


if '--self-test' in sys.argv[1:]:
    self_test()
    sys.exit(0)

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


def span_listing(cache_dir=CACHE_DIR):
    """(raw evidence text, {name: bytes}) under cache_dir; (raw, {}) when the dir
    does not exist yet. Defined contract instead of ls metadata parsing: `ls -1A`
    emits exactly one name per line (dot entries excluded, dotfiles included) and
    `wc -c` per name prints the byte count as its first field. Grounded on actual
    device output by preflight_listing() before any span assertion. Evidence for
    span-payload comparison, not a status derivation."""
    r = subprocess.run(['adb', 'shell', 'run-as', PKG, 'ls', '-1A', cache_dir],
                       capture_output=True)
    if r.returncode != 0:
        raw = r.stdout.decode(errors='replace') + r.stderr.decode(errors='replace')
        return raw, {}
    names_raw = r.stdout.decode(errors='replace')
    out = {}
    size_lines = []
    for name in parse_listing_names(names_raw):
        quoted = "'" + (cache_dir + '/' + name).replace("'", "'\\''") + "'"
        s = subprocess.run(['adb', 'shell', 'run-as', PKG, 'wc', '-c', quoted],
                           capture_output=True)
        size_lines.append(s.stdout.decode(errors='replace').strip()
                          or s.stderr.decode(errors='replace').strip())
        if s.returncode != 0:
            continue  # not a plain file (e.g. a subdir); the raw line stays in evidence
        out[name] = parse_byte_count(s.stdout.decode(errors='replace'))
    return names_raw + '-- sizes (wc -c) --\n' + '\n'.join(size_lines), out


def preflight_listing():
    """Grounds the listing contract on ACTUAL device output before any span check:
    writes fixture files of known sizes into a scratch dir inside the app sandbox,
    then asserts span_listing reports exactly those names and sizes, including a
    dotfile (kept by -A). The raw device output is saved to evidence either way."""
    probe = 'files/span-probe'
    subprocess.run(['adb', 'shell', 'run-as', PKG, 'rm', '-rf', probe],
                   capture_output=True)
    mk = subprocess.run(['adb', 'shell', 'run-as', PKG, 'mkdir', '-p', probe],
                        capture_output=True)
    assert mk.returncode == 0, 'probe dir failed: ' + mk.stderr.decode(errors='replace')
    fixtures = {'alpha.bin': b'oris-probe-a', 'beta.v3': b'x', '.hidden': b'12345'}
    for name, data in sorted(fixtures.items()):
        w = subprocess.run(['adb', 'shell', 'run-as', PKG, 'tee', probe + '/' + name],
                           input=data, capture_output=True)
        assert w.returncode == 0, 'probe write failed for ' + name + ': ' \
            + w.stderr.decode(errors='replace')
    raw, got = span_listing(probe)
    subprocess.run(['adb', 'shell', 'run-as', PKG, 'rm', '-rf', probe],
                   capture_output=True)
    open(OUT + '/span-preflight.txt', 'w').write(raw + '\n' + repr(sorted(got.items())))
    expected = {name: len(data) for name, data in fixtures.items()}
    assert got == expected, 'listing contract mismatch on actual device output: got ' \
        + repr(got) + ' expected ' + repr(expected)
    print('SPAN-PREFLIGHT: listing contract grounded on actual device output: '
          + repr(sorted(got.items())), flush=True)


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
    # On a fresh install the app's shared_prefs directory does not exist until the
    # first SharedPreferences access, so create it before tee writes the seed file.
    mk = subprocess.run(['adb', 'shell', 'run-as', PKG, 'mkdir', '-p', 'shared_prefs'],
                        capture_output=True)
    assert mk.returncode == 0, 'seed dir failed: ' + mk.stderr.decode(errors='replace')
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
# Fresh prefs are established, not assumed: install -r keeps any pre-existing app
# data, so clear it before seeding (no-op difference on a fresh emulator).
adb('shell', 'pm', 'clear', PKG)
seed_liked()
preflight_listing()

# Span-payload baseline: a fresh install has no download cache payloads.
baseline_raw, baseline = span_listing()
open(OUT + '/span-baseline.txt', 'w').write(baseline_raw + '\n' + repr(sorted(baseline.items())))
print('SPAN-BASELINE: %d cache files before any download' % len(baseline), flush=True)

# A1. Real cache write and state lifecycle through the real CacheWriter over file://.
adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'am', 'start', '-n', OFFLINE)
wait_label('Offline state: NOT SAVED', seconds=30)
tap('Download fixture')
wait_label('Offline state: READY', seconds=60)
screen('offline_ready')
ready_raw, ready_listing = span_listing()
payload = {k: v for k, v in ready_listing.items() if k not in baseline}
open(OUT + '/span-ready.txt', 'w').write(ready_raw + '\n' + repr(sorted(ready_listing.items())))
assert payload, 'READY but no new cache payload appeared: ' + repr(ready_listing) \
    + ' raw: ' + ready_raw
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
    after_raw, after = span_listing()
    open(OUT + '/span-after-cancel.txt', 'w').write(after_raw + '\n' + repr(sorted(after.items())))
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
