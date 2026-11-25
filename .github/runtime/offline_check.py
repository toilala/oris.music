#!/usr/bin/env python3
"""Bounded save-for-offline product slice, LOCAL FIXTURE evidence only: a generated
tone served over file:// drives the real cache write, state lifecycle, restart
validation, source-deleted offline playback with strict progression, the real Library
save/fail/retry/cancel/remove routes and removal. Nothing here is proof that a
YouTube download works; live resolution stays out of scope and playback evidence
supports "playback without available upstream", never a counted zero resolver
opens. Radios are cut and dumpsys connectivity is the OS evidence before and after.
Cache-engine evidence (debug activity buttons driving OfflineAudio directly) is
kept separate from real-UI lifecycle evidence (Library rows and overflow menus).
Cancel/retry semantics follow DownloadRegistry ownership: after Cancel the row may
stay DOWNLOADING until the blocked writer is released and the owning teardown
settles; only the settled state is asserted.

Fixture generation is CONTROLLED: the debug activity never writes the source bytes
and carries no generation path at all; only OfflineFixtureSetupReceiver writes
them, via an explicit-component broadcast. A broadcast is a one-shot delivered
event that nothing retains or replays, so launching or restoring the ordinary
check screen after a deletion cannot execute setup again (unlike intent extras on
the displayed activity, which task restoration may redeliver). Every onCreate
appends a recreation marker (pid, elapsed clock, extras flag) that is collected
as supplemental diagnosis; recreation is never asserted to be process death.

Span discovery follows the REAL media3 1.7.1 SimpleCache layout: payloads are
*.v3 files inside numbered subdirectories (files/download/<0-9>/<id>.<pos>.<ts>.v3);
the root holds only metadata (cached_content_index.exi, <hex>.uid), which is never
payload. The contract is grounded on actual device output by a nested sandbox probe
before any span assertion, and its parser is checked host-side (--self-test). The
span filename encodes the cache's numeric id, not the song id: key attribution is
fresh isolated-cache evidence (pm clear, single fixture key), stated as such.

Legs are independent with declared dependencies and explicit prerequisite resets;
each leg records PASS/FAIL/UNVERIFIED, writes its evidence BEFORE asserting, and a
failed leg never aborts independent routes. The overall run fails when any required
leg fails. Final collection (connectivity-after, continuous logcat, results
summary) runs regardless."""
import glob
import math
import re
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape


def parse_find_paths(text):
    """Paths from `find <dir> -type f` output: one exact path per line; blank
    lines are dropped. No metadata columns exist in this contract."""
    return [line for line in text.splitlines() if line.strip()]


def parse_byte_count(text):
    """Byte count from `wc -c` output: the first integer field of the first
    non-blank line; an echoed path after the count is ignored."""
    for line in text.splitlines():
        fields = line.split()
        if fields:
            return int(fields[0])
    raise ValueError('no byte count in wc output')


SPAN_RE = re.compile(r'/\d+/\d+\.\d+\.\d+\.v3$')


def span_payloads(listing):
    """Only real span payload files from a recursive listing: media3 1.7.1
    SimpleCache writes spans as files/download/<0-9>/<id>.<position>.<ts>.v3.
    Root files (cached_content_index.exi, <hex>.uid) are identity/index metadata,
    never payload, and anything outside a numeric subdirectory is excluded."""
    return {p: b for p, b in listing.items() if SPAN_RE.search(p)}


def playing_progression(samples, playing_state=3):
    """Strict progression verdict over attributed (state, position) samples.
    Returns (accepted, playing_positions). Accepted requires at least two PLAYING
    samples with strictly increasing positions and no decrease between consecutive
    PLAYING samples (a decrease means a replay/reset, not progression). Terminal
    non-PLAYING samples are recorded by the caller but never count."""
    positions = [p for st, p in samples if st == playing_state]
    increasing = sum(1 for a, b in zip(positions, positions[1:]) if b > a)
    decreased = any(b < a for a, b in zip(positions, positions[1:]))
    accepted = len(positions) >= 2 and increasing >= 1 and not decreased
    return accepted, positions


def fixture_wav_bytes(seconds=30):
    """Deterministic local fixture tone, same parameters as
    OfflineFixtureSetupReceiver.fixtureWav (440Hz, 8kHz, 16-bit mono). Used to
    restore a deleted source and to release a FIFO. Identity with the receiver's
    bytes is not asserted; within-run byte stability across fill/restart/playback
    comes from nothing rewriting the file after setup."""
    rate = 8000
    samples = rate * seconds
    out = bytearray()
    out += b'RIFF' + struct.pack('<i', 36 + samples * 2) + b'WAVEfmt '
    out += struct.pack('<ihhiihh', 16, 1, 1, rate, rate * 2, 2, 16)
    out += b'data' + struct.pack('<i', samples * 2)
    for i in range(samples):
        out += struct.pack('<h', int(math.sin(i * 2.0 * math.pi * 440 / rate) * 2000))
    return bytes(out)


def self_test():
    """Host-side parser/verdict checks, no device: the raw formats the device
    contract defines, metadata and blank exclusions, a full positive payload
    example in the REAL media3 1.7.1 layout, and strict sampler verdicts."""
    # find -type f contract: one path per line; blanks dropped.
    assert parse_find_paths('files/download/4/0.0.1764072000000.v3\n'
                            'files/download/cached_content_index.exi\n\n'
                            'files/download/666931664b3ea675.uid\n') == \
        ['files/download/4/0.0.1764072000000.v3',
         'files/download/cached_content_index.exi',
         'files/download/666931664b3ea675.uid']
    # wc -c contract: the byte count is the first field; padding and the echoed
    # path (including one with spaces) are ignored.
    assert parse_byte_count('32044 files/download/4/0.0.1764072000000.v3\n') == 32044
    assert parse_byte_count('     12 files/span-probe/alpha.bin\n') == 12
    assert parse_byte_count('12\n') == 12
    assert parse_byte_count('7 files/download/my file.v3\n') == 7
    # Metadata exclusions: .uid and .exi are never payload, and a .v3 at the root
    # (the pre-1.7 flat layout) does not satisfy the real-layout contract.
    listing = {'files/download/666931664b3ea675.uid': 0,
               'files/download/cached_content_index.exi': 4096,
               'files/download/oris.1.1764072000000.v3': 32044,
               'files/download/4/0.0.1764072000000.v3': 32044}
    assert span_payloads(listing) == \
        {'files/download/4/0.0.1764072000000.v3': 32044}
    assert span_payloads({'files/download/666931664b3ea675.uid': 0}) == {}
    # Positive payload: a full recursive round-trip over canned raw output.
    raw_paths = 'files/span-probe/4/0.0.1764072000000.v3\nfiles/span-probe/alpha.bin\n'
    sizes = {'files/span-probe/4/0.0.1764072000000.v3':
             '11 files/span-probe/4/0.0.1764072000000.v3\n',
             'files/span-probe/alpha.bin': '11 files/span-probe/alpha.bin\n'}
    got = {p: parse_byte_count(sizes[p]) for p in parse_find_paths(raw_paths)}
    assert got == {'files/span-probe/4/0.0.1764072000000.v3': 11,
                   'files/span-probe/alpha.bin': 11}, got
    # Strict sampler: the rejected shapes first.
    assert playing_progression([(3, 0), (1, 2003)]) == (False, [0])  # start/end pair
    assert playing_progression([(3, 0)]) == (False, [0])             # single sample
    assert playing_progression([]) == (False, [])                    # nothing
    assert playing_progression([(3, 500), (3, 400)]) == (False, [500, 400])  # reset
    assert playing_progression([(1, 2003), (4, 2003)]) == (False, [])  # terminal only
    # Accepted shapes: two or more increasing PLAYING samples; non-PLAYING samples
    # around them are recorded but never counted.
    assert playing_progression([(3, 0), (3, 500)]) == (True, [0, 500])
    assert playing_progression([(2, 0), (3, 100), (3, 900), (3, 1700), (1, 2003)]) == \
        (True, [100, 900, 1700])
    # Fixture byte generator: sizes and header fields for both durations.
    assert len(fixture_wav_bytes(2)) == 44 + 8000 * 2 * 2
    assert len(fixture_wav_bytes(30)) == 44 + 8000 * 2 * 30
    assert fixture_wav_bytes(30)[:4] == b'RIFF' and fixture_wav_bytes(30)[8:12] == b'WAVE'
    print('SELF-TEST: recursive span parser, metadata exclusions, real-layout '
          'positive payload, strict sampler verdicts and fixture bytes all passed',
          flush=True)


if '--self-test' in sys.argv[1:]:
    self_test()
    sys.exit(0)

from playback_check import adb, tap, wait_label, screen, tree, back, tap_mini_player, OUT, PKG

MAIN = PKG + '/com.orismusic.client.MainActivity'
OFFLINE = PKG + '/com.orismusic.client.debug.OfflineCheckActivity'
OFFLINE_SETUP = PKG + '/com.orismusic.client.debug.OfflineFixtureSetupReceiver'
TONE = 'Offline fixture tone'
SRC = 'cache/offline-fixture-tone.wav'
CACHE_DIR = 'files/download'
MARKERS = 'files/offline-check-recreations.log'
FIXTURE_SECONDS = 30
FIXTURE_SIZE = 44 + 8000 * 2 * FIXTURE_SECONDS


class Unverified(Exception):
    """A leg whose prerequisite the image cannot establish; never looped."""


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


def bounded(args, seconds=20):
    """Failure-safe bounded adb shell call for evidence collection; never raises."""
    try:
        r = subprocess.run(['adb'] + args, capture_output=True, timeout=seconds)
        return (r.stdout + r.stderr).decode(errors='replace')
    except Exception as e:
        return 'evidence capture failed: ' + repr(e)


def span_listing(cache_dir=CACHE_DIR):
    """(raw evidence text, {path: bytes}) for every plain file under cache_dir,
    recursively. Defined contract instead of ls metadata parsing: `find <dir>
    -type f` emits exactly one path per line and `wc -c` per path prints the byte
    count as its first field. Grounded on actual device output by
    preflight_listing() before any span assertion. Evidence for span-payload
    comparison, not a status derivation."""
    r = subprocess.run(['adb', 'shell', 'run-as', PKG, 'find', cache_dir, '-type', 'f'],
                       capture_output=True)
    if r.returncode != 0:
        raw = r.stdout.decode(errors='replace') + r.stderr.decode(errors='replace')
        return raw, {}
    paths_raw = r.stdout.decode(errors='replace')
    out = {}
    size_lines = []
    for path in parse_find_paths(paths_raw):
        quoted = "'" + path.replace("'", "'\\''") + "'"
        s = subprocess.run(['adb', 'shell', 'run-as', PKG, 'wc', '-c', quoted],
                           capture_output=True)
        size_lines.append(s.stdout.decode(errors='replace').strip()
                          or s.stderr.decode(errors='replace').strip())
        if s.returncode != 0:
            continue  # not readable as a plain file; the raw line stays in evidence
        out[path] = parse_byte_count(s.stdout.decode(errors='replace'))
    return paths_raw + '-- sizes (wc -c) --\n' + '\n'.join(size_lines), out


def save_listing(name, cache_dir=CACHE_DIR):
    raw, listing = span_listing(cache_dir)
    open(OUT + '/' + name + '.txt', 'w').write(raw + '\n' + repr(sorted(listing.items())))
    return listing


def preflight_listing():
    """Grounds the recursive listing contract on ACTUAL device output before any
    span check: writes fixture files of known sizes into a scratch dir inside the
    app sandbox, INCLUDING a nested numeric subdirectory shaped like a real span,
    then asserts span_listing reports exactly those paths and sizes (dotfile kept)
    and span_payloads selects only the nested .v3. Raw output saved either way."""
    probe = 'files/span-probe'
    subprocess.run(['adb', 'shell', 'run-as', PKG, 'rm', '-rf', probe],
                   capture_output=True)
    mk = subprocess.run(['adb', 'shell', 'run-as', PKG, 'mkdir', '-p', probe + '/4'],
                        capture_output=True)
    assert mk.returncode == 0, 'probe dir failed: ' + mk.stderr.decode(errors='replace')
    fixtures = {'alpha.bin': b'oris-probe-a', 'beta.v3': b'x', '.hidden': b'12345',
                '4/0.0.1764072000000.v3': b'span-payload'}
    for name, data in sorted(fixtures.items()):
        w = subprocess.run(['adb', 'shell', 'run-as', PKG, 'tee', probe + '/' + name],
                           input=data, capture_output=True)
        assert w.returncode == 0, 'probe write failed for ' + name + ': ' \
            + w.stderr.decode(errors='replace')
    raw, got = span_listing(probe)
    subprocess.run(['adb', 'shell', 'run-as', PKG, 'rm', '-rf', probe],
                   capture_output=True)
    open(OUT + '/span-preflight.txt', 'w').write(raw + '\n' + repr(sorted(got.items())))
    expected = {probe + '/' + name: len(data) for name, data in fixtures.items()}
    assert got == expected, 'listing contract mismatch on actual device output: got ' \
        + repr(got) + ' expected ' + repr(expected)
    picked = span_payloads(got)
    assert picked == {probe + '/4/0.0.1764072000000.v3': len(b'span-payload')}, picked
    print('SPAN-PREFLIGHT: recursive listing contract grounded on actual device '
          'output: ' + repr(sorted(got.items())), flush=True)


def restore_source():
    data = fixture_wav_bytes(FIXTURE_SECONDS)
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
                       input=fixture_wav_bytes(FIXTURE_SECONDS), capture_output=True,
                       timeout=seconds)
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


def app_pid():
    out = bounded(['shell', 'pidof', PKG]).strip().split()
    return out[0] if out and out[0].isdigit() else 'unknown'


def save_markers(name):
    open(OUT + '/' + name, 'w').write(bounded(['shell', 'run-as', PKG, 'cat', MARKERS]))


def setup_fixture():
    """The explicit debug setup action: a one-shot explicit-component broadcast to
    OfflineFixtureSetupReceiver, the ONLY path that writes the source bytes. No
    activity is launched and nothing is retained, so no recreation can replay it.
    Also sets fixtureUri in the receiving process as a side effect."""
    adb('shell', 'am', 'broadcast', '-n', OFFLINE_SETUP, '--ei', 'fixture_seconds',
        str(FIXTURE_SECONDS))
    end = time.monotonic() + 15
    while time.monotonic() < end and not source_present():
        time.sleep(0.5)
    assert source_present(), 'setup broadcast did not produce the fixture source'


def launch_debug():
    """Launch the displayed check activity. Its intent never carries generation,
    so neither this launch nor any later task restoration can write source bytes."""
    adb('shell', 'am', 'start', '-n', OFFLINE)


def wait_offline_label(seconds=30):
    """Wait for the debug status line in any of its states; returns the exact label."""
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        for l in labels():
            if l and l.startswith('Offline state:'):
                return l
        time.sleep(1)
    screen('failure')
    raise AssertionError('debug status line never appeared')


def remove_via_debug():
    """Establish key-not-cached/not-saved through the debug surface."""
    adb('shell', 'am', 'force-stop', PKG)
    launch_debug()
    if wait_offline_label() != 'Offline state: NOT SAVED':
        tap('Remove fixture')
        wait_label('Offline state: NOT SAVED', seconds=30)


def ensure_ready():
    """Establish a READY cached fixture through the debug surface."""
    adb('shell', 'am', 'force-stop', PKG)
    launch_debug()
    if wait_offline_label() == 'Offline state: READY':
        return
    if not source_present():
        setup_fixture()
    tap('Download fixture')
    wait_label('Offline state: READY', seconds=60)


RESULTS = []
LOGCAT = None


def passed(name):
    return any(n == name and r == 'PASS' for n, r, _ in RESULTS)


def capture_failure(name):
    """Failure-safe bounded evidence for a failed route; the original route
    failure is preserved and re-recorded by the caller."""
    safe = re.sub(r'[^a-z0-9]+', '-', name.lower())
    try:
        screen('failure-' + safe)
    except Exception:
        pass
    open(OUT + '/failure-' + safe + '-tree.xml', 'w').write(
        bounded(['shell', 'uiautomator', 'dump', '/sdcard/oris-fail.xml']) +
        bounded(['shell', 'cat', '/sdcard/oris-fail.xml']))
    save_listing('failure-' + safe + '-spans')
    save_markers('failure-' + safe + '-markers.txt')


def leg(name, requires=(), unverified_ok=False):
    """Run one independent route: declared dependencies, per-leg evidence, and a
    result record. A FAIL of a required leg fails the run but never aborts the
    independent legs that follow."""
    def run(fn):
        missing = [d for d in requires if not passed(d)]
        if missing:
            RESULTS.append((name, 'UNVERIFIED', 'dependency not PASS: ' + ','.join(missing)))
            print('LEG %s: UNVERIFIED (dependency not PASS: %s)'
                  % (name, ','.join(missing)), flush=True)
            return
        try:
            fn()
            RESULTS.append((name, 'PASS', ''))
            print('LEG %s: PASS' % name, flush=True)
        except Unverified as u:
            RESULTS.append((name, 'UNVERIFIED', str(u)))
            print('LEG %s: UNVERIFIED: %s' % (name, u), flush=True)
        except Exception as e:
            RESULTS.append((name, 'FAIL', repr(e)))
            print('LEG %s: FAIL: %s' % (name, repr(e)), flush=True)
            capture_failure(name)
    return run


def leg_setup_install():
    adb('wait-for-device')
    adb('shell', 'input', 'keyevent', '82')
    adb('shell', 'svc', 'wifi', 'disable')
    adb('shell', 'svc', 'data', 'disable')
    time.sleep(3)
    conn, live = connected_lines()
    open(OUT + '/connectivity-before.txt', 'w').write(conn)
    assert not live, 'network still live, refusing to run: ' + repr(live[:2])
    print('NETWORK-DISABLED: no connected network before the check (local-fixture '
          'evidence)', flush=True)
    adb('install', '-r', glob.glob('app/build/outputs/apk/foss/debug/*.apk')[0])
    # Fresh prefs are established, not assumed: install -r keeps any pre-existing app
    # data, so clear it before seeding (no-op difference on a fresh emulator).
    adb('shell', 'pm', 'clear', PKG)
    seed_liked()
    preflight_listing()
    baseline = save_listing('span-baseline')
    print('SPAN-BASELINE: %d cache files before any download' % len(baseline),
          flush=True)
    global LOGCAT
    adb('shell', 'logcat', '-c')
    LOGCAT = subprocess.Popen(['adb', 'logcat'],
                              stdout=open(OUT + '/logcat-full.txt', 'w'),
                              stderr=subprocess.DEVNULL)


def leg_setup_fixture():
    adb('shell', 'am', 'force-stop', PKG)
    setup_fixture()
    launch_debug()  # no extras: URI restore + recreation marker, never a rewrite
    wait_label('Offline state: NOT SAVED', seconds=30)
    save_markers('markers-after-setup.txt')
    print('SETUP: fixture source written by the one-shot setup broadcast (%ds); '
          'the check screen carries no generation path' % FIXTURE_SECONDS, flush=True)


def leg_a1():
    wait_label('Offline state: NOT SAVED', seconds=30)
    tap('Download fixture')
    wait_label('Offline state: READY', seconds=60)
    screen('offline_ready')
    listing = save_listing('span-ready')
    payload = span_payloads(listing)
    nonzero = {p: b for p, b in payload.items() if b > 0}
    open(OUT + '/span-ready-verdict.txt', 'w').write(
        'spans=' + repr(sorted(payload.items())) + '\nnonzero='
        + repr(sorted(nonzero.items())) + '\n')
    assert nonzero, 'READY but no nonzero span payload in the real layout: ' \
        + repr(sorted(payload.items()))
    total = sum(nonzero.values())
    # Fresh isolated cache (pm clear, single fixture key): every span belongs to the
    # key. The filename encodes the numeric cache id, not the song id, so this is
    # isolated-cache evidence, never a filename-to-key mapping.
    assert total >= FIXTURE_SIZE, 'cached span bytes %d below the %d-byte fixture: ' \
        '%r' % (total, FIXTURE_SIZE, sorted(nonzero.items()))
    print('CACHE-WRITE: download reached READY over the local fixture; nonzero '
          'span payloads in the real layout (fresh isolated-cache evidence): '
          + repr(sorted(nonzero.items())), flush=True)


def leg_a2():
    adb('shell', 'am', 'force-stop', PKG)
    launch_debug()  # no setup extra: no rewrite path exists on this launch
    wait_label('Offline state: READY', seconds=60)
    screen('offline_ready_after_restart')
    save_markers('markers-after-restart.txt')
    print('RESTART: READY revalidated from the persisted spans after a process '
          'restart; recreation markers collected (no setup extra, no rewrite)',
          flush=True)


def leg_a3():
    adb('shell', 'am', 'force-stop', PKG)
    launch_debug()
    wait_label('Offline state: READY', seconds=60)
    delete_source()
    print('SOURCE-ABSENT before playback: the original tone file is gone; the '
          'playback path never reads it (cache key only) and upstream has no '
          'network', flush=True)
    pid_before = app_pid()
    tap('Play offline')
    # Strict progression: the full 30s fixture window is sampled; acceptance needs
    # at least two attributed PLAYING samples with strictly increasing positions.
    # A single static read is inconclusive, never a pass, and the low-to-end
    # lifecycle pair can never pass: terminal non-PLAYING samples are recorded but
    # never counted.
    seq = []
    end = time.monotonic() + FIXTURE_SECONDS + 15
    while time.monotonic() < end:
        s = session_position(fail=False)
        if s and (not seq or s != seq[-1]):
            seq.append(s)
        time.sleep(0.5)
    open(OUT + '/playback-samples.txt', 'w').write(repr(seq))
    if not seq:
        session_position()  # raises with the dump saved
    screen('offline_playing')
    # Evidence before assertions.
    source_after = source_present()
    pid_after = app_pid()
    save_markers('markers-after-playback.txt')
    open(OUT + '/playback-diagnosis.txt', 'w').write(
        'samples=' + repr(seq) + '\npid_before=' + pid_before + ' pid_after='
        + pid_after + '\nsource_reappeared=' + repr(source_after) + '\n')
    accepted, positions = playing_progression(seq)
    assert accepted, 'no strict attributed progression: PLAYING positions ' \
        + repr(positions) + ' from samples ' + repr(seq)
    assert not source_after, 'fixture source reappeared during playback; see ' \
        'playback-diagnosis.txt and markers-after-playback.txt'
    print('PLAYBACK PROGRESSION: strict attributed PLAYING advancement ' \
        + repr(positions[:2]) + (' ... %d PLAYING samples' % len(positions))
        + ', source deleted and network off', flush=True)
    print('SOURCE-ABSENT after playback: playback did not regenerate the source',
          flush=True)
    markers = [l for l in open(OUT + '/logcat-full.txt', errors='replace')
               if re.search(r'YTPlayerUtils|innertube|OfflineModeException|UnknownHost'
                            r'|SocketException|PlaybackException', l)]
    print('PLAYBACK UPSTREAM (narrow claim): uninterrupted attributed playback with '
          'denied network and deleted source supports playback without available '
          'upstream; supplemental logcat resolution markers in window: %d'
          % len(markers), flush=True)


def leg_insets():
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


def leg_library_menu():
    # B2/B6. Real Library Saved-section row and overflow actions, separate from the
    # debug activity. The Saved section header text contains "Saved for offline"
    # inside a longer string, so exact-match chip assertions are done on the Liked
    # row only; the Saved row is asserted through its menu discipline and count.
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
    print('LIBRARY: Saved-section row offers only Remove from offline for a READY '
          'copy', flush=True)
    tap('Remove from offline')
    wait_label('Saved (0)', seconds=25)
    screen('library_removed')
    print('LIBRARY: removal cleared the Saved section', flush=True)


def leg_removal():
    # F. Removal is real: the debug surface agrees the copy is gone. Prerequisite
    # reset: establish READY again (re-source if the isolation leg deleted it),
    # then remove. The relaunch restores fixtureUri without rewriting the source.
    ensure_ready()
    tap('Remove fixture')
    wait_label('Offline state: NOT SAVED', seconds=30)
    screen('offline_removed')
    listing = save_listing('span-removed')
    resurrected = span_payloads(listing)
    assert not resurrected, 'span payloads remain after Remove fixture: ' \
        + repr(sorted(resurrected.items()))
    print('REMOVAL: cache spans and saved state gone after Remove fixture '
          '(recursive span listing)', flush=True)


def leg_real_ui_save():
    # B1. Real-UI save from the Liked row (same process, fixtureUri still set by
    # the removal leg's relaunch; the source file persists after removal).
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


def leg_failed_route():
    # B3. FAILED route, deterministic: remove, delete the source, then save again
    # on the same Liked row. No network involved: the write fails on the missing
    # local file.
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


def leg_retry():
    # B4. Retry after restore reaches READY.
    dismiss_menu()
    restore_source()
    open_row_menu()
    tap('Retry download')
    wait_label('Saved for offline', seconds=60)
    screen('library_row_ready_after_retry')
    print('LIBRARY: Retry download reached READY after the source was restored',
          flush=True)


def leg_cancel():
    # B5. Cancel with a controlled blocked source: a FIFO in place of the file
    # makes the writer block in open, so DOWNLOADING is directly observed via the
    # menu. Cancel is requested, the FIFO is released under a bounded host write,
    # and only the settled state is asserted: no READY, no FAILED, no saved entry,
    # no resurrected payloads.
    open_row_menu()
    tap('Remove from offline')
    gone('Saved for offline')
    wait_label('Saved (0)')
    if not make_fifo():
        raise Unverified('mkfifo under run-as is unsupported on this image')
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
    print('LIBRARY: DOWNLOADING directly observed via the Cancel download menu '
          'item during the blocked window', flush=True)
    tap('Cancel download')
    time.sleep(1)
    release_fifo()
    # Settled-state assertions only: the row may show DOWNLOADING until the
    # released writer unwinds and the owning teardown finishes.
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
    after = save_listing('span-after-cancel')
    resurrected = span_payloads(after)
    assert not resurrected, 'span payloads present after cancelled download ' \
        'settled: ' + repr(sorted(resurrected.items()))
    print('LIBRARY: cancel settled - no READY, no FAILED, no saved entry, no '
          'resurrected span payloads', flush=True)
    subprocess.run(['adb', 'shell', 'run-as', PKG, 'rm', '-f', SRC], capture_output=True)


LEGS = [
    ('SETUP-INSTALL', leg_setup_install, ()),
    ('SETUP-FIXTURE', leg_setup_fixture, ('SETUP-INSTALL',)),
    ('A1-CACHE-WRITE', leg_a1, ('SETUP-FIXTURE',)),
    ('A2-RESTART', leg_a2, ('A1-CACHE-WRITE',)),
    ('A3-ISOLATED-PLAYBACK', leg_a3, ('A1-CACHE-WRITE',)),
    ('C-INSETS', leg_insets, ('A1-CACHE-WRITE',)),
    ('B2B6-LIBRARY-MENU', leg_library_menu, ('A1-CACHE-WRITE',)),
    ('F-REMOVAL', leg_removal, ('A1-CACHE-WRITE',)),
    ('B1-REAL-UI-SAVE', leg_real_ui_save, ('F-REMOVAL',)),
    ('B3-FAILED-ROUTE', leg_failed_route, ('B1-REAL-UI-SAVE',)),
    ('B4-RETRY', leg_retry, ('B3-FAILED-ROUTE',)),
    ('B5-CANCEL', leg_cancel, ('B4-RETRY',)),
]

try:
    for name, fn, requires in LEGS:
        leg(name, requires)(fn)
finally:
    # Final collection runs regardless of leg outcomes.
    try:
        conn, live = connected_lines()
        open(OUT + '/connectivity-after.txt', 'w').write(conn)
        if live:
            RESULTS.append(('CONNECTIVITY-AFTER', 'FAIL',
                            'network reconnected: ' + repr(live[:2])))
            print('LEG CONNECTIVITY-AFTER: FAIL: network RECONNECTED during the '
                  'session: ' + repr(live[:2]), flush=True)
        else:
            RESULTS.append(('CONNECTIVITY-AFTER', 'PASS', ''))
            print('NETWORK-DISABLED: still no connected network after the check',
                  flush=True)
    except Exception as e:
        RESULTS.append(('CONNECTIVITY-AFTER', 'FAIL', repr(e)))
    if LOGCAT is not None:
        try:
            LOGCAT.terminate()
            LOGCAT.wait(timeout=10)
        except Exception:
            pass
    lines = ['%s %s %s' % (n, r, d) for n, r, d in RESULTS]
    open(OUT + '/results.txt', 'w').write('\n'.join(lines) + '\n')
    print('RESULTS:', flush=True)
    for line in lines:
        print('  ' + line, flush=True)

failed = [n for n, r, _ in RESULTS if r == 'FAIL']
if failed:
    print('FAIL: required leg(s) failed: ' + ', '.join(failed), flush=True)
    sys.exit(1)
print('PASS: save-for-offline slice on the local fixture (cache write, restart '
      'validation, strictly progressing source-deleted playback, Library '
      'save/fail/retry/cancel/remove routes, recursive span inspection)', flush=True)
