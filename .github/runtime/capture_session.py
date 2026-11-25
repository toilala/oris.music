#!/usr/bin/env python3
"""Capture-only UI session v2 over the seeded local state. No assertions about product
behavior, no playback, no query typing: each route retains a raw uiautomator tree dump
and a screenshot. Selectors come from the retained v1 tree evidence: a label
(content-desc or text) sits on a non-clickable child; the clickable ancestor is what
gets tapped. Routes are independent: a route that finds a mismatched context stops
ITSELF with tree+pixels evidence, recovers with a plain BACK key, re-anchors on the
nav dock, and the next route proceeds only when the anchor holds. Network stays off;
Home renders its offline state (no feed fixture exists)."""
import glob
import hashlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

from playback_check import adb, wait_label, screen, OUT, PKG

MAIN = PKG + '/com.orismusic.client.MainActivity'

LIKED = [
    ('dQw4w9WgXcQ', 'Midnight City', 'M83'),
    ('kJQP7kiw5Fk', 'One More Time', 'Daft Punk'),
]
HISTORY = [('60ItHLz5WEA', 'Nuvole Bianche', 'Ludovico Einaudi')]
COUNTS = {'dQw4w9WgXcQ': 12, 'kJQP7kiw5Fk': 7, '60ItHLz5WEA': 3, 'abc123xyz99': 5}
PLAYLIST = [
    ('dQw4w9WgXcQ', 'Midnight City', 'M83'),
    ('import:aurora|bicep', 'Aurora', 'Bicep'),
]
HINT = 'Search your liked songs'
DOCK = ('Home', 'Search', 'Library')


def songs_json(rows):
    return '[' + ','.join('{"id":"%s","title":"%s","artist":"%s"}' % r for r in rows) + ']'


def seed():
    playlists = ('[{"id":"pl1","name":"Imported mix","songs":' + songs_json(PLAYLIST) + '}]')
    counts = '{' + ','.join('"%s":%d' % kv for kv in COUNTS.items()) + '}'
    library = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>"
               + '<string name="liked">' + escape(songs_json(LIKED)) + '</string>'
               + '<string name="history">' + escape(songs_json(HISTORY)) + '</string>'
               + '<string name="playlists">' + escape(playlists) + '</string>'
               + '<string name="counts">' + escape(counts) + '</string>'
               + '</map>')
    data = library.encode()
    r = subprocess.run(['adb', 'shell', 'run-as', PKG, 'tee',
                        'shared_prefs/oris-library.xml'],
                       input=data, capture_output=True)
    assert r.returncode == 0, 'fixture write failed: ' + r.stderr.decode(errors='replace')
    back = subprocess.run(['adb', 'shell', 'run-as', PKG, 'cat',
                           'shared_prefs/oris-library.xml'],
                          capture_output=True)
    assert back.returncode == 0 and back.stdout == data, 'oris-library.xml read-back mismatch'
    ET.fromstring(back.stdout)
    print('SEED: liked/history/counts/playlists written and read back identical', flush=True)


def bounds_of(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds'))))


def center(node):
    p = bounds_of(node)
    return (p[0] + p[2]) // 2, (p[1] + p[3]) // 2


def parent_map(root):
    return {c: p for p in root.iter('node') for c in p}


def clickable_ancestor(node, pmap):
    """Climb from a labelled (usually non-clickable) node to its nearest clickable
    ancestor; returns None when no ancestor is clickable. Validates that the resolved
    ancestor's bounds contain the labelled node's bounds (tree-shape guard)."""
    cur = node
    while cur is not None and cur.get('clickable') != 'true':
        cur = pmap.get(cur)
    if cur is None:
        return None
    a = bounds_of(cur)
    b = bounds_of(node)
    if not (a[0] <= b[0] and a[1] <= b[1] and a[2] >= b[2] and a[3] >= b[3]):
        return None
    return cur


def find_labelled(root, label):
    return [n for n in root.iter('node')
            if n.get('content-desc') == label or n.get('text') == label]


def resolve_tap(root, label):
    """Resolve a label to the center of its clickable ancestor. Returns
    (x, y, chosen_desc, candidates_note) or (None, None, None, candidates_note)."""
    pmap = parent_map(root)
    notes = []
    hits = []
    for n in find_labelled(root, label):
        anc = clickable_ancestor(n, pmap)
        notes.append('label-via=%s label-bounds=%s clickable=%s anc-bounds=%s'
                     % ('desc' if n.get('content-desc') == label else 'text',
                        n.get('bounds'), n.get('clickable'),
                        anc.get('bounds') if anc is not None else 'NONE'))
        if anc is not None:
            hits.append(anc)
    note = 'candidates for %r:\n' % label + ('\n'.join(notes) if notes else 'none')
    if not hits:
        return None, None, None, note
    chosen = min(hits, key=lambda a: center(a)[1])
    x, y = center(chosen)
    return x, y, chosen.get('bounds'), note


def tap_label(root_name, label, log_name):
    """Tap the topmost clickable ancestor carrying this label, from the live tree.
    All candidates are logged; returns False without tapping when none resolves."""
    root = dump_ui(root_name)
    x, y, chosen, note = resolve_tap(root, label)
    with open(OUT + '/' + log_name, 'w') as f:
        f.write(note + ('\nCHOSEN anc-bounds=%s tap=(%d,%d)' % (chosen, x, y)
                        if x is not None else '\nNO TAP'))
    if x is None:
        return False
    adb('shell', 'input', 'tap', str(x), str(y))
    time.sleep(1.5)
    return True


def dump_ui(name):
    for _ in range(3):
        try:
            adb('shell', 'uiautomator', 'dump', '/sdcard/oris-cap.xml')
            raw = adb('shell', 'cat', '/sdcard/oris-cap.xml')
            open(OUT + '/' + name + '.xml', 'wb').write(raw)
            return ET.fromstring(raw)
        except (subprocess.CalledProcessError, ET.ParseError):
            time.sleep(1)
    open(OUT + '/' + name + '.xml', 'w').write('<!-- dump failed -->')
    return ET.Element('hierarchy')


def snap(name):
    screen(name)
    return dump_ui(name)


def frame_hash(name):
    screen(name)
    try:
        return hashlib.sha256(open(OUT + '/' + name + '.png', 'rb').read()).hexdigest()
    except OSError:
        return 'missing'


def anchored(root):
    """True when the nav dock is visible, i.e. we are on a top-level screen."""
    labels = set()
    for n in root.iter('node'):
        if n.get('content-desc') in DOCK:
            labels.add(n.get('content-desc'))
    return len(labels) >= 2


class RouteAbort(Exception):
    pass


def route(name, fn):
    """Run one independent route. On any failure: retain tree+pixels for the route,
    BACK-key recover, and re-anchor on the dock. Returns True when the route completed."""
    try:
        fn()
        print('ROUTE-OK %s' % name, flush=True)
        return True
    except Exception as e:
        open(OUT + '/%s-ABORT.txt' % name, 'w').write('route %s aborted: %r\n' % (name, e))
        try:
            snap('abort-%s' % name)
        except Exception:
            pass
        print('ROUTE-ABORT %s: %r (evidence retained)' % (name, e), flush=True)
        try:
            adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
            time.sleep(1.5)
            root = dump_ui('reanchor-%s' % name)
            screen('reanchor-%s' % name)
            if not anchored(root):
                open(OUT + '/ANCHOR-LOST.txt', 'w').write(
                    'after %s abort + BACK, dock not visible; remaining routes stopped\n' % name)
                print('ANCHOR-LOST after %s; stopping remaining routes' % name, flush=True)
                sys.exit(1)
        except Exception as re:
            open(OUT + '/ANCHOR-LOST.txt', 'w').write('recovery itself failed: %r\n' % re)
            sys.exit(1)
        return False


def require(ok, msg):
    if not ok:
        raise RouteAbort(msg)


adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
adb('shell', 'svc', 'wifi', 'disable')
adb('shell', 'svc', 'data', 'disable')
time.sleep(3)
conn = adb('shell', 'dumpsys', 'connectivity').decode()
open(OUT + '/connectivity-before.txt', 'w').write(conn)
live = [line for line in conn.splitlines()
        if re.search(r'\bCONNECTED\b', line) and 'DISCONNECTED' not in line]
assert not live, 'network still live, refusing to run: ' + repr(live[:2])
print('NETWORK-DISABLED (bounded evidence)', flush=True)
open(OUT + '/display.txt', 'w').write(
    adb('shell', 'wm', 'size').decode() + adb('shell', 'wm', 'density').decode())
adb('install', '-r', glob.glob('app/build/outputs/apk/foss/debug/*.apk')[0])

# First launch creates the data directories; seed, then restart on the seeded stores.
adb('shell', 'am', 'start', '-n', MAIN)
wait_label('Home', seconds=60)
adb('shell', 'am', 'force-stop', PKG)
seed()
adb('shell', 'am', 'start', '-n', MAIN)
wait_label('Home', seconds=60)


def home_route():
    snap('01-home')  # raw first frame, retained as evidence whatever it shows
    # Settled frame: poll until two consecutive screencaps are byte-identical.
    prev = frame_hash('settle-a')
    settled = False
    for _ in range(10):
        time.sleep(2.5)
        cur = frame_hash('settle-b')
        if cur == prev and cur != 'missing':
            settled = True
            break
        prev = cur
    root = snap('01b-home-settled')
    texts = [n.get('text') for n in root.iter('node') if n.get('text')]
    import os
    size = os.path.getsize(OUT + '/01b-home-settled.png')
    open(OUT + '/home-settle.txt', 'w').write(
        'settled=%s png-bytes=%d tree-texts=%r\n' % (settled, size, texts[:12]))
route('home', home_route)


def library_route():
    require(tap_label('probe', 'Library', 'tap-library.txt'), 'dock Library not resolvable')
    snap('02-library')
route('library', library_route)


def stats_route():
    require(tap_label('probe', 'Listening stats', 'tap-stats.txt'),
            'Listening stats label not resolvable to a clickable ancestor')
    root = snap('03-stats')
    adb('shell', 'input', 'swipe', '540', '1500', '540', '700', '400')
    time.sleep(1)
    snap('04-stats-scrolled')
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(1.5)
    back = snap('03b-stats-back')
    if not anchored(back):
        open(OUT + '/CONTEXT-MISMATCH-stats-back.txt', 'w').write(
            'BACK from stats did not land on a dock-anchored screen\n')
        raise RouteAbort('post-stats BACK context differs, evidence retained')
route('stats', stats_route)


def playlists_route():
    require(tap_label('probe', 'Library', 'tap-library2.txt'), 'dock Library not resolvable')
    root = dump_ui('probe')
    chips = [n.get('text') for n in root.iter('node')
             if n.get('text') and n.get('text').startswith('Playlists')]
    if not chips:
        raise RouteAbort('no Playlists chip text on Library: %r' % chips)
    require(tap_label('probe', chips[0], 'tap-playlists-chip.txt'), 'Playlists chip not resolvable')
    snap('05-playlists')
route('playlists', playlists_route)


def playlist_open_route():
    require(tap_label('probe', 'Imported mix', 'tap-playlist.txt'),
            'Imported mix row not resolvable')
    snap('06-playlist-open')
route('playlist-open', playlist_open_route)


def import_route():
    # Import affordances live outside an open playlist, so BACK to the enclosing
    # list first (system key, not a guessed selector), then scan the live labels.
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(1.5)
    root = dump_ui('probe')
    labels = set()
    for n in root.iter('node'):
        for v in (n.get('content-desc'), n.get('text')):
            if v:
                labels.add(v)
    open(OUT + '/import-candidates.txt', 'w').write(
        'labels visible at import route entry:\n' + '\n'.join(sorted(labels)))
    entries = [l for l in labels if 'import' in l.lower()]
    if not entries:
        raise RouteAbort('no import-labelled affordance visible; candidates logged')
    require(tap_label('probe', sorted(entries)[0], 'tap-import.txt'),
            'import affordance not resolvable')
    snap('07-import-dialog')
route('import-dialog', import_route)


def search_route():
    root = dump_ui('probe')
    if not anchored(root):
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        time.sleep(1.5)
    require(tap_label('probe', 'Search', 'tap-search.txt'), 'dock Search not resolvable')
    snap('08-search-online')
route('search-online', search_route)


def library_mode_route():
    snap('09-search-before-mode-tap')
    root = dump_ui('probe')
    pmap = parent_map(root)
    # Mode-chip disambiguation: 'Library' labels both the dock tab (bottom strip) and
    # the mode chip (upper). Resolve every candidate's clickable ancestor and choose
    # the topmost resolved target; dock candidates sit far lower and lose. All
    # candidates and the choice are logged for review.
    notes = []
    hits = []
    for n in find_labelled(root, 'Library'):
        anc = clickable_ancestor(n, pmap)
        notes.append('label-bounds=%s clickable=%s anc-bounds=%s'
                     % (n.get('bounds'), n.get('clickable'),
                        anc.get('bounds') if anc is not None else 'NONE'))
        if anc is not None:
            hits.append(anc)
    open(OUT + '/mode-select.txt', 'w').write(
        'candidates for Library-mode chip (topmost resolved target tapped):\n'
        + ('\n'.join(notes) if notes else 'none'))
    if not hits:
        raise RouteAbort('no Library-labelled candidate resolves on the Search screen')
    chosen = min(hits, key=lambda a: center(a)[1])
    x, y = center(chosen)
    adb('shell', 'input', 'tap', str(x), str(y))
    time.sleep(1.5)
    after = snap('10-search-library-mode')
    if not any(HINT in (n.get('text') or '') for n in after.iter('node')):
        open(OUT + '/CONTEXT-MISMATCH.txt', 'w').write(
            'post-tap tree lacks the Library-mode hint %r; mode state uncertain\n' % HINT)
        raise RouteAbort('post-tap context differs, evidence retained')
route('search-library-mode', library_mode_route)

conn = adb('shell', 'dumpsys', 'connectivity').decode()
open(OUT + '/connectivity-after.txt', 'w').write(conn)
print('CAPTURE-COMPLETE: named tree+screenshot pairs retained', flush=True)
