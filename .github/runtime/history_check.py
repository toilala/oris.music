#!/usr/bin/env python3
"""Listening-history recording, fixture-based and fully local. Two REAL playable local
fixture items (host-generated wavs pushed into the app's cacheDir over run-as and
addressed by file:// media ids, so the player reads actual audio), a seeded queue
snapshot the service restores PAUSED on cold start, and a seeded history store whose
head H differs from the restored queue song Q: a restore-as-play of Q could then never
hide behind consecutive-id dedupe, because Q is not the head. Seeding happens only
while the app is force-stopped; fixture wavs are written once and never regenerated on
the ordinary app restarts inside this check.

Covers, each with its own evidence: cold-start restore shows Q paused and leaves
history/counts exactly at baseline (value comparison of parsed prefs, never raw XML);
a real start of Q records it once and increments Q's count; skip-to-next while playing
records the next item with multiple increasing PLAYING positions attributed to it via
its session metadata; a repeat-one loop, selected through the real tray control
(OFF -> ALL -> ONE per PlayerConnection.toggleRepeatMode) and OBSERVED as wrapping
media-session positions for the same item, records no duplicate. Persisted comparisons
use parsed values only."""
import glob
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

from playback_check import adb, tap, tap_mini_player, wait_label, screen, tree, PKG, OUT, pidof

MAIN = PKG + '/com.orismusic.client.MainActivity'

Q_TITLE = 'History fixture Q'
NEXT_TITLE = 'History fixture next'
H_TITLE = 'History fixture H'
ARTIST = 'Oris test'
CACHE = '/data/user/0/' + PKG + '/cache/'
Q_ID = 'file://' + CACHE + 'history-fixture-q.wav'
NEXT_ID = 'file://' + CACHE + 'history-fixture-next.wav'
H_ID = 'history-fixture-h'
# Q runs long enough for several increasing position samples; NEXT is short so the
# repeat-one loop wraps inside a bounded watch.
Q_WAV = ('history-fixture-q.wav', 440.0, 20)
NEXT_WAV = ('history-fixture-next.wav', 550.0, 5)
BASE_COUNTS = {H_ID: 2}


def wav_bytes(seconds, freq):
    """PCM wav mirroring OfflineFixtureSetupReceiver.fixtureWav (8kHz, 16-bit mono)."""
    import math
    import struct
    rate = 8000
    samples = rate * seconds
    frames = bytearray()
    for i in range(samples):
        frames += struct.pack('<h', int(math.sin(i * 2.0 * math.pi * freq / rate) * 2000))
    header = (b'RIFF' + struct.pack('<I', 36 + len(frames)) + b'WAVEfmt '
              + struct.pack('<IHHIIHH', 16, 1, 1, rate, rate * 2, 2, 16)
              + b'data' + struct.pack('<I', len(frames)))
    return bytes(header) + bytes(frames)


def runas_write(path, data):
    r = subprocess.run(['adb', 'shell', 'run-as', PKG, 'tee', path],
                       input=data, capture_output=True)
    assert r.returncode == 0, 'fixture write failed for ' + path + ': ' + r.stderr.decode(errors='replace')
    back = subprocess.run(['adb', 'shell', 'run-as', PKG, 'cat', path], capture_output=True)
    assert back.returncode == 0 and back.stdout == data, path + ' read-back mismatch'
    return len(data)


def force_stopped():
    adb('shell', 'am', 'force-stop', PKG)
    time.sleep(1)
    alive = pidof(PKG)
    assert not alive, 'app process still alive after force-stop: ' + ' '.join(alive)


def songs_json(rows):
    return '[' + ','.join('{"id":"%s","title":"%s","artist":"%s"}' % r for r in rows) + ']'


def library_values(xml_text):
    """Parsed (history, counts) VALUES from oris-library.xml text. History is a list of
    song dicts in stored order, counts a dict keyed by song id; serialization detail
    (whitespace, attribute order, escaping) never reaches the comparison."""
    root = ET.fromstring(xml_text)
    history, counts = [], {}
    for node in root:
        if node.tag == 'string' and node.get('name') == 'history':
            history = json.loads(node.text or '[]')
        elif node.tag == 'string' and node.get('name') == 'counts':
            counts = {k: int(v) for k, v in json.loads(node.text or '{}').items()}
    return history, counts


def read_library(name):
    xml = adb('shell', 'run-as', PKG, 'cat', 'shared_prefs/oris-library.xml').decode()
    open(OUT + '/' + name, 'w').write(xml)
    history, counts = library_values(xml)
    print('PERSISTED ' + name + ': history=' + repr([s.get('id') for s in history])
          + ' counts=' + repr(counts), flush=True)
    return history, counts


def seed():
    n = runas_write('cache/' + Q_WAV[0], wav_bytes(Q_WAV[2], Q_WAV[1]))
    print('FIXTURE-SEED ' + Q_WAV[0] + ' bytes=' + str(n) + ', read-back identical', flush=True)
    n = runas_write('cache/' + NEXT_WAV[0], wav_bytes(NEXT_WAV[2], NEXT_WAV[1]))
    print('FIXTURE-SEED ' + NEXT_WAV[0] + ' bytes=' + str(n) + ', read-back identical', flush=True)
    queue = {
        'title': 'History fixture queue',
        'items': [
            {'id': Q_ID, 'title': Q_TITLE, 'artists': [ARTIST], 'duration': Q_WAV[2] * 1000},
            {'id': NEXT_ID, 'title': NEXT_TITLE, 'artists': [ARTIST], 'duration': NEXT_WAV[2] * 1000},
        ],
        'index': 0,
        'position': 3000,
    }
    data = json.dumps(queue).encode()
    runas_write('files/queue_snapshot.json', data)
    print('FIXTURE-SEED queue_snapshot.json: ' + data.decode(), flush=True)
    library = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>"
               + '<string name="history">'
               + escape(songs_json([(H_ID, H_TITLE, ARTIST)])) + '</string>'
               + '<string name="counts">' + escape(json.dumps(BASE_COUNTS)) + '</string>'
               + '</map>')
    runas_write('shared_prefs/oris-library.xml', library.encode())
    print('FIXTURE-SEED oris-library.xml history head=' + H_ID
          + ' (deliberately != queue song) counts=' + repr(BASE_COUNTS), flush=True)


def session_state(title):
    """(state, position) of the media session whose metadata names title, or None.
    state=3 is playing; the position is attributed to this title's own session."""
    dump = adb('shell', 'dumpsys', 'media_session').decode()
    lines = dump.splitlines()
    for i, line in enumerate(lines):
        if title in line:
            for j in range(i, max(-1, i - 30), -1):
                m = re.search(r'state=(\d+), position=(-?\d+)', lines[j])
                if m:
                    return int(m.group(1)), int(m.group(2))
    return None


def wait_session(title, seconds=30):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        found = session_state(title)
        if found is not None:
            return found
        time.sleep(1)
    screen('failure')
    raise AssertionError('no media session carrying ' + repr(title))


def playing_samples(title, count, gap=1.2, seconds=45):
    """count increasing (state=3, position) samples attributed to title's session."""
    samples = []
    end = time.monotonic() + seconds
    while len(samples) < count and time.monotonic() < end:
        found = session_state(title)
        if found is not None and found[0] == 3:
            if not samples or found[1] > samples[-1][1]:
                samples.append(found)
        time.sleep(gap)
    assert len(samples) >= count, ('wanted ' + str(count) + ' increasing playing samples for '
                                   + repr(title) + ', got ' + repr(samples))
    print('PLAYBACK ' + repr(title) + ' increasing playing positions: ' + repr(samples), flush=True)
    return samples


def phase(name):
    print('PHASE ' + name, flush=True)


if __name__ == '__main__':
    adb('wait-for-device')
    adb('shell', 'input', 'keyevent', '82')
    adb('install', '-r', glob.glob('app/build/outputs/apk/foss/debug/*.apk')[0])

    # First launch creates the app's data directories; then seed while stopped so the
    # next process start reads the seeded stores.
    adb('shell', 'am', 'start', '-n', MAIN)
    wait_label('Home', seconds=60)
    force_stopped()
    seed()

    # 1. COLD RESTORE: the restored queue is Q and paused, and the seeded history and
    # counts are untouched. A restore-as-play would surface here: Q is not the seeded
    # history head, so dedupe cannot mask it.
    phase('restore')
    adb('shell', 'am', 'start', '-n', MAIN)
    wait_label('Home', seconds=60)
    state, position = wait_session(Q_TITLE)
    assert state != 3, 'restored queue is PLAYING without a user gesture: state=' + str(state)
    print('RESTORE: session carries ' + repr(Q_TITLE) + ' at position ' + str(position)
          + ' with non-playing state=' + str(state), flush=True)
    screen('history_restored_paused')
    history, counts = read_library('history-prefs-restored.xml')
    assert [s.get('id') for s in history] == [H_ID], \
        'restore rewrote history: ' + repr([s.get('id') for s in history])
    assert counts == BASE_COUNTS, 'restore rewrote counts: ' + repr(counts)
    print('RESTORE: history head still ' + H_ID + ', counts unchanged ' + repr(counts), flush=True)

    # 2. REAL START: playing Q from the restored queue records it once, count +1.
    phase('start')
    tap_mini_player(Q_TITLE)
    wait_label('Play', seconds=15)
    tap('Play')
    state, _ = wait_session(Q_TITLE)
    assert state == 3, 'Q did not reach playing state after Play: state=' + str(state)
    playing_samples(Q_TITLE, 3)
    screen('history_started_q')
    history, counts = read_library('history-prefs-after-start.xml')
    assert [s.get('id') for s in history] == [Q_ID, H_ID], \
        'history after real start: ' + repr([s.get('id') for s in history])
    assert counts == {Q_ID: 1, H_ID: 2}, 'counts after real start: ' + repr(counts)
    print('START: history head is Q, counts ' + repr(counts), flush=True)

    # 3. SKIP NEXT while playing: the next fixture item records with its own samples.
    phase('next')
    tap('Next')
    state, _ = wait_session(NEXT_TITLE)
    assert state == 3, 'next item did not reach playing state: state=' + str(state)
    playing_samples(NEXT_TITLE, 2)
    screen('history_started_next')
    history, counts = read_library('history-prefs-after-next.xml')
    assert [s.get('id') for s in history] == [NEXT_ID, Q_ID, H_ID], \
        'history after skip-next: ' + repr([s.get('id') for s in history])
    assert counts == {NEXT_ID: 1, Q_ID: 1, H_ID: 2}, 'counts after skip-next: ' + repr(counts)
    print('NEXT: history head is next, counts ' + repr(counts), flush=True)

    # 4. REPEAT-ONE: selected through the real tray (More -> Repeat -> Repeat all ->
    # Repeat one, the OFF -> ALL -> ONE cycle in PlayerConnection.toggleRepeatMode);
    # the loop itself is OBSERVED as a wrap in the same session's playing positions.
    phase('repeat-one')
    tap('More')
    wait_label('Repeat', seconds=15)
    tap('Repeat')
    wait_label('Repeat all', seconds=15)
    tap('Repeat all')
    wait_label('Repeat one', seconds=15)
    print('REPEAT-ONE: tray shows Repeat one (selected via OFF -> ALL -> ONE)', flush=True)
    screen('history_repeat_one_set')
    positions = []
    end = time.monotonic() + 12
    while time.monotonic() < end:
        found = session_state(NEXT_TITLE)
        assert found is not None and found[0] == 3, \
            'session left playing state during the repeat-one watch: ' + repr(found)
        positions.append(found[1])
        time.sleep(1)
    wraps = [i for i in range(1, len(positions)) if positions[i] < positions[i - 1]]
    assert wraps, 'no position wrap observed during repeat-one watch: ' + repr(positions)
    print('REPEAT-ONE: loop observed, positions ' + repr(positions)
          + ' (wrap at sample ' + repr(wraps) + ')', flush=True)
    adb('shell', 'input', 'keyevent', '4')
    history, counts = read_library('history-prefs-after-repeat.xml')
    assert [s.get('id') for s in history] == [NEXT_ID, Q_ID, H_ID], \
        'repeat-one rewrote history: ' + repr([s.get('id') for s in history])
    assert counts == {NEXT_ID: 1, Q_ID: 1, H_ID: 2}, 'repeat-one rewrote counts: ' + repr(counts)
    print('REPEAT-ONE: no duplicate recorded after the observed loop', flush=True)

    print('PASS: history restore/start/next/repeat-one; persisted history and counts '
          'match each phase exactly', flush=True)
