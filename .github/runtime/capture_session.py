#!/usr/bin/env python3
"""Acceptance capture session v3 for the stats-back fix and the remaining screens.
Each route establishes its own current screen from the live tree before acting,
verifies the expected post-action context, and retains before/after tree+pixels.
Selectors resolve labels to their clickable ancestor (shape learned from retained
v1/v2 trees). No repeated root BACK as a reset: recovery re-anchors via the dock,
and a route with a mismatched context stops itself with evidence. No playback,
no query typing, network stays off."""
import glob
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
CHIP_RE = re.compile(r'^(Liked|Recent|Playlists|Saved) \(\d+\)$')
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


def tap_label(label, log_name):
    root = dump_ui('probe')
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


def texts_of(root):
    return [n.get('text') for n in root.iter('node') if n.get('text')]


def descs_of(root):
    return [n.get('content-desc') for n in root.iter('node') if n.get('content-desc')]


def chips_present(root):
    return any(CHIP_RE.match(t) for t in texts_of(root))


def stats_open(root):
    return 'Listening stats' in texts_of(root) and not chips_present(root)


def dock_visible(root):
    return len(set(descs_of(root)) & set(DOCK)) >= 2


class RouteAbort(Exception):
    pass


class SessionAbort(Exception):
    pass


def route(name, fn):
    try:
        fn()
        print('ROUTE-OK %s' % name, flush=True)
    except SessionAbort:
        raise
    except Exception as e:
        open(OUT + '/%s-ABORT.txt' % name, 'w').write('route %s aborted: %r\n' % (name, e))
        try:
            snap('abort-%s' % name)
        except Exception:
            pass
        print('ROUTE-ABORT %s: %r (evidence retained)' % (name, e), flush=True)


def require(ok, msg):
    if not ok:
        raise RouteAbort(msg)


def back_once():
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(1.5)


def ensure_library_list(tag):
    """Establish the Library chip-list screen from wherever the session is, using the
    dock and (only if stats is showing) its own in-view return action. Aborts the
    whole session when the dock itself is gone; raises RouteAbort when Library shows
    an unexpected state."""
    root = dump_ui('anchor-%s' % tag)
    if not dock_visible(root):
        snap('anchor-lost-%s' % tag)
        open(OUT + '/ANCHOR-LOST.txt', 'w').write(
            'dock not visible while anchoring for %s; session stopped\n' % tag)
        raise SessionAbort(tag)
    if chips_present(root):
        return root
    require(tap_label('Library', 'tap-dock-library-%s.txt' % tag),
            'dock Library not resolvable while anchoring for %s' % tag)
    root = dump_ui('anchor2-%s' % tag)
    if chips_present(root):
        return root
    if stats_open(root):
        require(tap_label('Back to Library', 'tap-backtolib-%s.txt' % tag),
                'stats open and its in-view return action not resolvable')
        root = dump_ui('anchor3-%s' % tag)
        if chips_present(root):
            return root
    snap('anchor-mismatch-%s' % tag)
    raise RouteAbort('Library did not show the chip list while anchoring for %s' % tag)


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

adb('shell', 'am', 'start', '-n', MAIN)
wait_label('Home', seconds=60)
adb('shell', 'am', 'force-stop', PKG)
seed()
adb('shell', 'am', 'start', '-n', MAIN)
wait_label('Home', seconds=60)


def library_route():
    root = ensure_library_list('library')
    require(tap_label('Library', 'tap-library.txt'), 'dock Library not resolvable')
    root = snap('02-library')
    require(chips_present(root), 'Library screen lacks filter chips')
route('library', library_route)


def stats_back_route():
    ensure_library_list('sb')
    require(tap_label('Listening stats', 'tap-stats.txt'),
            'Listening stats label not resolvable')
    root = snap('03-stats-open')
    require(stats_open(root), 'stats view did not open')
    back_once()
    root = snap('04-stats-system-back')
    if not chips_present(root):
        open(OUT + '/ACCEPTANCE-FAIL-stats-back.txt', 'w').write(
            'system Back from stats did NOT return to the Library chip list; texts=%r\n'
            % texts_of(root)[:12])
        raise RouteAbort('acceptance failed: stats system Back did not reach chip list')
    print('ACCEPT: system Back from stats returns to Library list', flush=True)
route('stats-system-back', stats_back_route)


def stats_button_route():
    ensure_library_list('btn')
    require(tap_label('Listening stats', 'tap-stats2.txt'),
            'Listening stats label not resolvable (reopen)')
    root = snap('05-stats-reopen')
    require(stats_open(root), 'stats view did not reopen')
    require(tap_label('Back to Library', 'tap-backtolib.txt'),
            'in-view Back to Library action not resolvable')
    root = snap('06-stats-button-back')
    if not chips_present(root):
        open(OUT + '/ACCEPTANCE-FAIL-stats-button.txt', 'w').write(
            'in-view Back to Library did NOT return to the chip list; texts=%r\n'
            % texts_of(root)[:12])
        raise RouteAbort('acceptance failed: in-view return did not reach chip list')
    print('ACCEPT: in-view Back to Library returns to Library list', flush=True)
route('stats-button-back', stats_button_route)


def open_playlists_list(tag):
    root = ensure_library_list(tag)
    chip = next((t for t in texts_of(root)
                 if t.startswith('Playlists') and CHIP_RE.match(t)), None)
    require(chip, 'no Playlists chip text on Library list (%s)' % tag)
    require(tap_label(chip, 'tap-playlists-chip-%s.txt' % tag),
            'Playlists chip not resolvable (%s)' % tag)
    return dump_ui('playlists-list-%s' % tag)


def playlists_route():
    open_playlists_list('pl')
    snap('07-playlists')
route('playlists', playlists_route)


def playlist_open_route():
    root = open_playlists_list('plopen')
    if 'Imported mix' not in texts_of(root):
        raise RouteAbort('Imported mix row not visible on playlists list')
    require(tap_label('Imported mix', 'tap-playlist.txt'),
            'Imported mix row not resolvable')
    snap('08-playlist-open')
route('playlist-open', playlist_open_route)


def import_route():
    root = open_playlists_list('imp')
    if 'Import playlist' not in texts_of(root):
        open(OUT + '/import-candidates.txt', 'w').write(
            'texts at import entry:\n' + '\n'.join(sorted(texts_of(root))))
        raise RouteAbort('no Import playlist affordance visible; candidates logged')
    require(tap_label('Import playlist', 'tap-import.txt'),
            'Import playlist affordance not resolvable')
    root = snap('09-import-dialog')
    require('Import playlist' in texts_of(root), 'import dialog did not open')
    require(tap_label('Cancel', 'tap-import-cancel.txt'), 'dialog Cancel not resolvable')
    dump_ui('09b-import-dismissed')
route('import-dialog', import_route)


def search_route():
    root = dump_ui('anchor-search')
    if not dock_visible(root):
        snap('anchor-lost-search')
        open(OUT + '/ANCHOR-LOST.txt', 'w').write('dock not visible before search route\n')
        raise SessionAbort('search')
    require(tap_label('Search', 'tap-search.txt'), 'dock Search not resolvable')
    snap('10-search-online')
route('search-online', search_route)


def library_mode_route():
    snap('11-search-before-mode-tap')
    root = dump_ui('probe')
    pmap = parent_map(root)
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
    after = snap('12-search-library-mode')
    if not any(HINT in (n.get('text') or '') for n in after.iter('node')):
        open(OUT + '/CONTEXT-MISMATCH.txt', 'w').write(
            'post-tap tree lacks the Library-mode hint %r; mode state uncertain\n' % HINT)
        raise RouteAbort('post-tap context differs, evidence retained')
route('search-library-mode', library_mode_route)

conn = adb('shell', 'dumpsys', 'connectivity').decode()
open(OUT + '/connectivity-after.txt', 'w').write(conn)
print('CAPTURE-COMPLETE: acceptance session retained', flush=True)
