#!/usr/bin/env python3
"""Search screen Library mode, fixture-based and fully local. The library store is
seeded over run-as, the offline fixture tone is downloaded through the real pipeline
(OfflineCheckActivity over file://), then the Search screen's Library mode is driven
with the radios off. Covers mode entry, title/artist matching, query change with no
stale rows, query clear, the Saved offline badge (validated READY from the cache,
never mere saves membership), and result-tap queue playback served from the download
cache. Network-disabled evidence: dumpsys connectivity before and after, labeled
bounded evidence; the zero-request property itself is the guarded call paths in code
(suggestions and online results are both mode-gated), not this script's promise."""
import glob
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

from playback_check import adb, tap, wait_label, screen, tree, OUT, PKG

MAIN = PKG + '/com.orismusic.client.MainActivity'
OFFLINE = PKG + '/com.orismusic.client.debug.OfflineCheckActivity'
TONE = 'Offline fixture tone'
HINT = 'Search your liked songs, history, playlists and offline saves. Works without a connection.'

LIKED = [
    ('offline-fixture-tone', 'Offline fixture tone', 'Oris test'),
    ('lf-1', 'Midnight City', 'M83'),
    ('lf-2', 'One More Time', 'Daft Punk'),
]
HISTORY = [('lf-4', 'Nuvole Bianche', 'Ludovico Einaudi')]


def connected_lines():
    conn = adb('shell', 'dumpsys', 'connectivity').decode()
    return conn, [line for line in conn.splitlines()
                  if re.search(r'\bCONNECTED\b', line) and 'DISCONNECTED' not in line]


def songs_json(rows):
    return '[' + ','.join('{"id":"%s","title":"%s","artist":"%s"}' % r for r in rows) + ']'


def seed():
    library = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>"
               + '<string name="liked">' + escape(songs_json(LIKED)) + '</string>'
               + '<string name="history">' + escape(songs_json(HISTORY)) + '</string>'
               + '</map>')
    data = library.encode()
    r = subprocess.run(['adb', 'shell', 'run-as', PKG, 'tee',
                        'shared_prefs/oris-library.xml'],
                       input=data, capture_output=True)
    assert r.returncode == 0, ('fixture write failed for oris-library.xml: '
                               + r.stderr.decode(errors='replace'))
    back = subprocess.run(['adb', 'shell', 'run-as', PKG, 'cat',
                           'shared_prefs/oris-library.xml'],
                          capture_output=True)
    assert back.returncode == 0 and back.stdout == data, 'oris-library.xml read-back mismatch'
    ET.fromstring(back.stdout)
    print('FIXTURE-SEED oris-library.xml bytes=' + str(len(data))
          + ', read-back identical, parses', flush=True)


def visible(text):
    return any(n.get('text') == text for n in tree().iter('node'))


def wait_visible(text, seconds=15):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if visible(text):
            return
        time.sleep(0.7)
    screen('failure')
    raise AssertionError('never visible: ' + text)


def wait_gone(text, seconds=15):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if not visible(text):
            return
        time.sleep(0.7)
    screen('failure')
    raise AssertionError('still visible: ' + text)


def tap_checkable(label):
    """Tap a FilterChip by label: the checkable node, never a bottom-nav tab of the
    same name (the nav 'Library' tab outranks the mode chip under tap(bottom=True))."""
    wait_label(label)
    end = time.monotonic() + 15
    while time.monotonic() < end:
        nodes = [n for n in tree().iter('node')
                 if n.get('checkable') == 'true' and label in (n.get('text'), n.get('content-desc'))]
        if nodes:
            points = list(map(int, re.findall(r'\d+', nodes[0].get('bounds'))))
            adb('shell', 'input', 'tap', str((points[0]+points[2])//2), str((points[1]+points[3])//2))
            time.sleep(1)
            return
        time.sleep(1)
    raise AssertionError('no checkable node labelled ' + label)


def type_query(text):
    tap('Songs, albums, artists')
    adb('shell', 'input', 'text', text.replace(' ', '%s'))
    time.sleep(1)
    return len(text)


def clear_query(n):
    tap('Songs, albums, artists')
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    for _ in range(n):
        adb('shell', 'input', 'keyevent', 'KEYCODE_DEL')
    time.sleep(1)


def tone_session_playing(seconds=30):
    """PlaybackState of the media session whose metadata names the fixture tone."""
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        dump = adb('shell', 'dumpsys', 'media_session').decode()
        lines = dump.splitlines()
        for i, line in enumerate(lines):
            if TONE in line:
                for j in range(i, max(-1, i - 30), -1):
                    m = re.search(r'state=(\d+), position=(\d+)', lines[j])
                    if m:
                        if int(m.group(1)) == 3:
                            print('PLAYBACK: fixture tone session state=3 (playing), position '
                                  + m.group(2), flush=True)
                            return
                        break
        time.sleep(1)
    screen('failure')
    raise AssertionError('fixture tone session never reached playing state')


adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
adb('shell', 'svc', 'wifi', 'disable')
adb('shell', 'svc', 'data', 'disable')
time.sleep(3)
conn, live = connected_lines()
open(OUT + '/connectivity-before.txt', 'w').write(conn)
assert not live, 'network still live, refusing to run: ' + repr(live[:2])
print('NETWORK-DISABLED: no connected network before the check (bounded evidence)', flush=True)
adb('install', '-r', glob.glob('app/build/outputs/apk/foss/debug/*.apk')[0])

# First launch creates the app's data directories; then seed and restart so the
# seeded stores are what the next process start reads.
adb('shell', 'am', 'start', '-n', MAIN)
wait_label('Home', seconds=60)
adb('shell', 'am', 'force-stop', PKG)
seed()

# Real cache write through the real download pipeline, over file:// only.
adb('shell', 'am', 'start', '-n', OFFLINE)
wait_label('Offline state: NOT SAVED', seconds=30)
tap('Download fixture')
wait_label('Offline state: READY', seconds=60)
print('CACHE-WRITE: fixture tone downloaded to READY over the local fixture', flush=True)

adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'am', 'start', '-n', MAIN)
wait_label('Home', seconds=60)
tap('Search')

# Both modes render; Online stays the default.
wait_visible('Library')
wait_visible('Online')
print('MODES: Library and Online chips both render', flush=True)

tap_checkable('Library')
# Still on the Search screen - the nav tab of the same name leads elsewhere.
wait_visible('Songs, albums, artists')
wait_visible(HINT)
screen('search_library_blank')
print('MODE-ENTRY: Library mode shows its offline hint on a blank query', flush=True)

# Title and artist match; non-matches absent.
n = type_query('midnight')
wait_visible('Midnight City')
wait_visible('M83')
assert not visible('One More Time'), 'non-matching liked row rendered'
assert not visible('Nuvole Bianche'), 'non-matching history row rendered'
screen('search_library_match')
print('MATCH: title/artist rows render, non-matches excluded', flush=True)

# Query change swaps rows with no stale remainder, then clear empties the list.
clear_query(n)
n = type_query('nuvole')
wait_visible('Nuvole Bianche')
wait_gone('Midnight City')
print('STALE-GUARD: query change replaced the rows', flush=True)
clear_query(n)
wait_gone('Nuvole Bianche')
wait_visible(HINT)
print('STALE-GUARD: cleared query returns to the blank hint', flush=True)

# The badge is validated cache coverage: the fixture tone reached READY through the
# real download above, so this row is the one place the badge may appear.
n = type_query('offline')
wait_visible(TONE)
wait_visible('Saved offline')
wait_visible('Oris test')
screen('search_library_badge')
print('BADGE: Saved offline renders on the cache-validated row', flush=True)

# Result tap queues the local result list and plays the cached tone with no network.
tap(TONE)
tone_session_playing()
screen('now_playing_from_library_search')
print('RESULT-TAP: queued and playing from the download cache, radios off', flush=True)

conn, live = connected_lines()
open(OUT + '/connectivity-after.txt', 'w').write(conn)
assert not live, 'network RECONNECTED during the session: ' + repr(live[:2])
print('NETWORK-DISABLED: still no connected network after the check', flush=True)
print('PASS: local library search (modes, match, stale-guard, badge, result-tap playback)',
      flush=True)
