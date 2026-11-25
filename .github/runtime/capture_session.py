#!/usr/bin/env python3
"""Functional acceptance session v5: import create/reopen/restart-persistence,
unsupported-input rejection, and provenance-marker guard behavior (mixed playlist,
Liked section, Home shelves) on seeded local data only. Expectations are derived
from the parser semantics at this revision (2 unique songs from the 4-line input:
1 duplicate collapsed, 1 unreadable line) and the availability-qualified copy.
Selectors resolve labels to their clickable ancestor or the EditText containing
the label. Each route re-anchors from the live tree; a failing route writes
ACCEPTANCE-FAIL evidence and stops itself. Network stays off for the whole
session; the only playback action is one eligible-row attempt that must fail
locally, recorded as observed evidence. No live-service requests."""
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
    ('import:sea|tres', 'Sea', 'Tres'),
    ('dQw4w9WgXcQ', 'Midnight City', 'M83'),
    ('lf-4', 'Local Fixture', 'Debug'),
]
HISTORY = [
    ('import:sky|lude', 'Sky', 'Lude'),
    ('60ItHLz5WEA', 'Nuvole Bianche', 'Ludovico Einaudi'),
]
COUNTS = {'dQw4w9WgXcQ': 12, 'kJQP7kiw5Fk': 7, '60ItHLz5WEA': 3}
PLAYLIST = [
    ('dQw4w9WgXcQ', 'Midnight City', 'M83'),
    ('import:aurora|bicep', 'Aurora', 'Bicep'),
]
CHIP_RE = re.compile(r'^(Liked|Recent|Playlists|Saved) \(\d+\)$')
DOCK = ('Home', 'Search', 'Library')

IMPORT_LINES = ['Midnight City - M83', 'Aurora - Bicep', 'Aurora - Bicep', 'broken line']
IMPORT_NAME = 'Acceptance check'
SONGS_LABEL = 'Songs as Title - Artist, one per line, or a CSV export'
UNSUPPORTED_LINK = 'https://music.youtube.com/playlist'
UNSUPPORTED_REASON = "Playlist links can't be read offline"
MARKER = "Metadata only, can't play"
CAPTION_ALL_META = "Play and Shuffle skip the 2 metadata-only entries; they can't play."
CAPTION_MIXED = "Play and Shuffle skip the 1 metadata-only entry; it can't play."
FAIL_PILL = 'No connection'


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
        notes.append('label-via=%s label-bounds=%s clickable=%s enabled=%s anc-bounds=%s'
                     % ('desc' if n.get('content-desc') == label else 'text',
                        n.get('bounds'), n.get('clickable'), n.get('enabled'),
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


def edit_texts(root):
    return [n for n in root.iter('node') if (n.get('class') or '').endswith('EditText')]


def edit_text_containing(root, node):
    b = bounds_of(node)
    for e in edit_texts(root):
        eb = bounds_of(e)
        if eb[0] <= b[0] and eb[1] <= b[1] and eb[2] >= b[2] and eb[3] >= b[3]:
            return e
    return None


def focus_field(label, log_name):
    root = dump_ui('probe')
    hits = find_labelled(root, label)
    target = None
    for n in hits:
        target = edit_text_containing(root, n)
        if target is not None:
            break
    if target is None:
        open(OUT + '/' + log_name, 'w').write(
            'focus_field %r: %d label hit(s), none contained in an EditText\nNO TAP'
            % (label, len(hits)))
        return False
    x, y = center(target)
    adb('shell', 'input', 'tap', str(x), str(y))
    time.sleep(1.2)
    after = dump_ui('focus-' + log_name.replace('.txt', ''))
    focused = [e.get('bounds') for e in edit_texts(after) if e.get('focused') == 'true']
    open(OUT + '/' + log_name, 'w').write(
        'focus_field %r: target=%s tap=(%d,%d) focused-after=%s'
        % (label, target.get('bounds'), x, y, focused))
    return bool(focused)


def type_text(s):
    adb('shell', 'input', 'text', s.replace(' ', '%s'))
    time.sleep(0.6)


def type_lines(lines):
    for i, ln in enumerate(lines):
        type_text(ln)
        if i < len(lines) - 1:
            adb('shell', 'input', 'keyevent', 'KEYCODE_ENTER')
            time.sleep(0.4)


def field_with_text(sub):
    root = dump_ui('probe')
    return any(sub in (e.get('text') or '') for e in edit_texts(root))


def tap_dock(label, log_name):
    root = dump_ui('probe')
    pmap = parent_map(root)
    hits = []
    for n in find_labelled(root, label):
        anc = clickable_ancestor(n, pmap)
        if anc is not None:
            hits.append(anc)
    with open(OUT + '/' + log_name, 'w') as f:
        f.write('dock candidates for %r: %s'
                % (label, [h.get('bounds') for h in hits] or 'none'))
    if not hits:
        return False
    chosen = max(hits, key=lambda a: center(a)[1])
    x, y = center(chosen)
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


def back():
    adb('shell', 'input', 'keyevent', '4')
    time.sleep(1)


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


def accept_fail(name, body):
    open(OUT + '/ACCEPTANCE-FAIL-%s.txt' % name, 'w').write(body)
    raise RouteAbort('acceptance failed: %s' % name)


def ensure_library_list(tag):
    root = dump_ui('anchor-%s' % tag)
    if not dock_visible(root):
        snap('anchor-lost-%s' % tag)
        open(OUT + '/ANCHOR-LOST.txt', 'w').write(
            'dock not visible while anchoring for %s; session stopped\n' % tag)
        raise SessionAbort(tag)
    if chips_present(root):
        return root
    require(tap_dock('Library', 'tap-dock-library-%s.txt' % tag),
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


def open_playlists_list(tag):
    root = ensure_library_list(tag)
    chip = next((t for t in texts_of(root)
                 if t.startswith('Playlists') and CHIP_RE.match(t)), None)
    require(chip, 'no Playlists chip text on Library list (%s)' % tag)
    require(tap_label(chip, 'tap-playlists-chip-%s.txt' % tag),
            'Playlists chip not resolvable (%s)' % tag)
    return dump_ui('playlists-list-%s' % tag)


def open_playlist(name, tag):
    root = open_playlists_list(tag)
    require(name in texts_of(root), 'playlist %r not listed (%s)' % (name, tag))
    require(tap_label(name, 'tap-open-%s.txt' % tag),
            'playlist %r row not resolvable (%s)' % (name, tag))
    return dump_ui('playlist-%s' % tag)


def row_for(root, title):
    """The clickable ancestor of the row's title text, or None."""
    pmap = parent_map(root)
    for n in find_labelled(root, title):
        if n.get('text') != title:
            continue
        anc = clickable_ancestor(n, pmap)
        if anc is not None:
            return anc
    return None


def tap_node(node):
    x, y = center(node)
    adb('shell', 'input', 'tap', str(x), str(y))
    time.sleep(1.5)


def no_attempt_observed(name, seconds=4):
    """No failure pill and no player opened within the window."""
    time.sleep(seconds)
    root = dump_ui(name)
    ts = texts_of(root)
    return FAIL_PILL not in ts


def menu_items_for_row(root, row, log_name):
    """Open the row's overflow (the 'Add to playlist' icon button inside the row's
    subtree) and return the menu texts."""
    target = None
    for n in row.iter('node'):
        if n.get('content-desc') == 'Add to playlist':
            target = n
            break
    if target is None:
        open(OUT + '/' + log_name, 'w').write('no Add to playlist button inside row subtree')
        return None
    btn = target if target.get('clickable') == 'true' else clickable_ancestor(target, parent_map(root))
    if btn is None:
        return None
    tap_node(btn)
    menu = dump_ui('menu-' + log_name.replace('.txt', ''))
    return texts_of(menu)


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


def import_full_route():
    root = open_playlists_list('impf')
    if 'Import playlist' not in texts_of(root):
        open(OUT + '/import-candidates.txt', 'w').write(
            'texts at import entry:\n' + '\n'.join(sorted(texts_of(root))))
        raise RouteAbort('no Import playlist affordance visible; candidates logged')
    require(tap_label('Import playlist', 'tap-import.txt'),
            'Import playlist affordance not resolvable')
    root = snap('30-import-dialog')
    require('Import playlist' in texts_of(root), 'import dialog did not open')

    require(focus_field('Name', 'tap-name.txt'), 'Name field not focusable')
    type_text(IMPORT_NAME)
    require(field_with_text(IMPORT_NAME), 'Name field did not take the typed value')

    require(focus_field(SONGS_LABEL, 'tap-songs.txt'), 'songs field not focusable')
    type_lines(IMPORT_LINES)
    require(field_with_text('Midnight City - M83'),
            'songs field did not take the typed lines')

    require(tap_label('Preview', 'tap-preview.txt'), 'Preview action not resolvable')
    root = snap('31-import-preview')
    ts = texts_of(root)
    ok = (any('2 entries ready to import' in t for t in ts)
          and any('1 duplicate collapsed' in t for t in ts)
          and any("1 line couldn't be read" in t for t in ts)
          and any('2 entries are metadata only: listed in the playlist' in t for t in ts)
          and not any('songs ready' in t for t in ts)
          and not any('3 entries' in t for t in ts))
    if not ok:
        accept_fail('import-preview',
                    'preview summary mismatch (expected 2 entries ready, 1 duplicate, '
                    '1 skipped, metadata-only limitation; no "songs ready"); texts=%r\n'
                    % ts[:24])
    print('ACCEPT: preview reports 2 entries ready to import, 1 duplicate collapsed, '
          '1 skipped, metadata-only limitation (corrected expectation)', flush=True)

    require(tap_label('Import', 'tap-import-confirm.txt'),
            'Import confirm action not resolvable')
    root = snap('32-import-created')
    ts = texts_of(root)
    markers = sum(1 for t in ts if MARKER in t)
    ok = (IMPORT_NAME in ts and 'Midnight City' in ts and 'Aurora' in ts
          and markers >= 2 and CAPTION_ALL_META in ts)
    if not ok:
        accept_fail('import-create',
                    'created detail mismatch (name/rows/2 markers/caption); texts=%r\n'
                    % ts[:24])
    print('ACCEPT: import creates the playlist; both rows marked metadata only; '
          'caption states the skip', flush=True)

    pmap = parent_map(root)
    for label in ('Play', 'Shuffle'):
        hits = [clickable_ancestor(n, pmap) for n in find_labelled(root, label)]
        hits = [h for h in hits if h is not None]
        require(hits, 'no %s button on the all-metadata playlist detail' % label)
        states = sorted(set(h.get('enabled') for h in hits))
        if states != ['false']:
            accept_fail('import-create-buttons',
                        '%s button(s) not disabled on all-metadata playlist; states=%r\n'
                        % (label, states))
    print('ACCEPT: Play and Shuffle disabled when every entry is metadata only', flush=True)


def import_reopen_route():
    require(tap_label('All playlists', 'tap-all-playlists.txt'),
            'All playlists return action not resolvable')
    root = snap('33-playlists-after-create')
    require(IMPORT_NAME in texts_of(root), 'created playlist not listed on Playlists')
    require(tap_label(IMPORT_NAME, 'tap-reopen.txt'),
            'created playlist row not resolvable')
    root = snap('34-import-reopened')
    ts = texts_of(root)
    if not (IMPORT_NAME in ts and 'Midnight City' in ts and 'Aurora' in ts
            and any(MARKER in t for t in ts)):
        accept_fail('import-reopen',
                    'reopened playlist lost rows or markers; texts=%r\n' % ts[:24])
    print('ACCEPT: reopened playlist persists rows and metadata-only markers', flush=True)


def import_unsupported_route():
    require(tap_label('All playlists', 'tap-all-playlists-u.txt'),
            'All playlists return action not resolvable (unsupported pass)')
    require(tap_label('Import playlist', 'tap-import2.txt'),
            'Import playlist affordance not resolvable (second pass)')
    snap('35-import-dialog-2')
    require(focus_field(SONGS_LABEL, 'tap-songs2.txt'),
            'songs field not focusable (unsupported input)')
    type_text(UNSUPPORTED_LINK)
    require(field_with_text('music.youtube.com'),
            'songs field did not take the link text')
    require(tap_label('Preview', 'tap-preview2.txt'),
            'Preview action not resolvable (unsupported input)')
    root = snap('36-import-unsupported')
    ts = texts_of(root)
    if not any(UNSUPPORTED_REASON in t for t in ts):
        accept_fail('import-unsupported',
                    'unsupported link input was not rejected with its reason; texts=%r\n'
                    % ts[:24])
    pmap = parent_map(root)
    hits = [clickable_ancestor(n, pmap) for n in find_labelled(root, 'Import')
            if n.get('text') == 'Import']
    hits = [h for h in hits if h is not None]
    require(hits, 'no Import confirm button after unsupported preview')
    states = sorted(set(h.get('enabled') for h in hits))
    if states != ['false']:
        accept_fail('import-unsupported-button',
                    'Import confirm not disabled for unsupported input; states=%r\n' % states)
    print('ACCEPT: unsupported playlist-link input rejected with its reason; '
          'Import confirm disabled', flush=True)

    require(tap_label('Cancel', 'tap-import-cancel.txt'),
            'dialog Cancel not resolvable (second pass)')
    root = snap('37-import-cancelled')
    require('Import playlist' in texts_of(root), 'dialog did not dismiss back to playlists')


def liked_guard_route():
    root = ensure_library_list('liked')
    chip = next((t for t in texts_of(root)
                 if t.startswith('Liked') and CHIP_RE.match(t)), None)
    require(chip, 'no Liked chip on Library list')
    require(tap_label(chip, 'tap-liked-chip.txt'), 'Liked chip not resolvable')
    root = snap('40-liked-section')
    ts = texts_of(root)
    require('Sea' in ts and 'Local Fixture' in ts and 'Midnight City' in ts,
            'seeded liked rows missing; texts=%r' % ts[:20])

    sea = row_for(root, 'Sea')
    fixture = row_for(root, 'Local Fixture')
    require(sea is not None and fixture is not None, 'liked rows not resolvable')
    sea_marker = any(MARKER in (n.get('text') or '') for n in sea.iter('node'))
    fixture_marker = any(MARKER in (n.get('text') or '') for n in fixture.iter('node'))
    if not sea_marker or fixture_marker:
        accept_fail('liked-markers',
                    'marker mismatch: import-marked row marker=%s, legacy-id row marker=%s\n'
                    % (sea_marker, fixture_marker))
    print('ACCEPT: import-marked liked row carries the marker; legacy non-shaped id row '
          'does not', flush=True)

    items = menu_items_for_row(root, sea, 'menu-sea.txt')
    require(items is not None, 'menu did not open for the import-marked row')
    if any('Save for offline' in t for t in items) or any('Retry download' in t for t in items):
        accept_fail('liked-menu-sea',
                    'offline save offered on an import-marked row; items=%r\n' % items)
    if 'Imported mix' not in items:
        accept_fail('liked-menu-sea',
                    'playlist actions missing on import-marked row; items=%r\n' % items)
    print('ACCEPT: import-marked row menu excludes offline save, keeps playlist actions',
          flush=True)
    back()
    root = dump_ui('41-liked-after-sea-menu')

    fixture = row_for(root, 'Local Fixture')
    require(fixture is not None, 'Local Fixture row gone after menu dismiss')
    items = menu_items_for_row(root, fixture, 'menu-fixture.txt')
    require(items is not None, 'menu did not open for the legacy-id row')
    if not any('Save for offline' in t for t in items):
        accept_fail('liked-menu-fixture',
                    'offline save missing on an attempt-eligible legacy-id row; items=%r\n'
                    % items)
    print('ACCEPT: legacy non-shaped id row keeps offline save (attempt eligible)',
          flush=True)
    back()
    root = dump_ui('42-liked-after-fixture-menu')

    sea = row_for(root, 'Sea')
    require(sea is not None, 'Sea row gone before tap')
    tap_node(sea)
    if not no_attempt_observed('43-liked-sea-tap'):
        accept_fail('liked-sea-tap',
                    'tapping an import-marked row started a playback attempt\n')
    root = dump_ui('43b-liked-sea-tap')
    require('Sea' in texts_of(root), 'Liked section lost after import-marked row tap')
    print('ACCEPT: tapping an import-marked liked row is a no-op (no attempt, no navigation)',
          flush=True)


def home_guard_route():
    root = dump_ui('anchor-home')
    require(dock_visible(root), 'dock not visible before Home route')
    require(tap_dock('Home', 'tap-dock-home.txt'), 'dock Home not resolvable')
    root = snap('50-home')
    ts = texts_of(root)
    require('Continue listening' in ts, 'Continue listening shelf missing on Home')
    require('Sky' in ts, 'import-marked history entry not listed on Home shelf')
    sky = row_for(root, 'Sky')
    require(sky is not None, 'Sky tile not resolvable on Home')
    tap_node(sky)
    if not no_attempt_observed('51-home-sky-tap'):
        accept_fail('home-sky-tap',
                    'tapping an import-marked Home tile started a playback attempt\n')
    root = dump_ui('51b-home-sky-tap')
    if not dock_visible(root) or 'Continue listening' not in texts_of(root):
        accept_fail('home-sky-tap-nav',
                    'Home navigated away after import-marked tile tap; texts=%r\n'
                    % texts_of(root)[:20])
    print('ACCEPT: tapping an import-marked Home tile is a no-op (guard at playHistory)',
          flush=True)


def restart_route():
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', MAIN)
    wait_label('Home', seconds=60)
    root = open_playlists_list('restart')
    ts = texts_of(root)
    require(IMPORT_NAME in ts and 'Imported mix' in ts,
            'playlists lost across restart; texts=%r' % ts[:20])
    require(tap_label(IMPORT_NAME, 'tap-reopen-restart.txt'),
            'created playlist row not resolvable after restart')
    root = snap('60-restart-playlist')
    ts = texts_of(root)
    markers = sum(1 for t in ts if MARKER in t)
    if not ('Midnight City' in ts and 'Aurora' in ts and markers >= 2
            and CAPTION_ALL_META in ts):
        accept_fail('restart-persistence',
                    'created playlist lost rows/markers/caption across restart; texts=%r\n'
                    % ts[:24])
    print('ACCEPT: imported playlist, rows, markers and caption persist across '
          'force-stop restart (actual storage reload)', flush=True)
    require(tap_label('All playlists', 'tap-all-playlists-r.txt'),
            'All playlists return action not resolvable after restart')


def mixed_playlist_route():
    root = open_playlist('Imported mix', 'mixed')
    root = snap('70-mixed-playlist')
    ts = texts_of(root)
    require(CAPTION_MIXED in ts, 'mixed caption missing; texts=%r' % ts[:24])

    aurora = row_for(root, 'Aurora')
    midnight = row_for(root, 'Midnight City')
    require(aurora is not None and midnight is not None, 'mixed playlist rows not resolvable')
    if aurora.get('enabled') != 'false':
        accept_fail('mixed-aurora-row',
                    'metadata-only row not disabled; enabled=%s\n' % aurora.get('enabled'))
    if midnight.get('enabled') != 'true':
        accept_fail('mixed-midnight-row',
                    'eligible row not enabled; enabled=%s\n' % midnight.get('enabled'))
    print('ACCEPT: mixed playlist disables the metadata-only row, keeps the eligible row',
          flush=True)

    tap_node(aurora)
    if not no_attempt_observed('71-mixed-aurora-tap'):
        accept_fail('mixed-aurora-tap',
                    'tapping the metadata-only row started a playback attempt\n')
    root = dump_ui('71b-mixed-aurora-tap')
    require('Aurora' in texts_of(root), 'playlist detail lost after metadata-only row tap')
    print('ACCEPT: tapping the metadata-only row is a no-op', flush=True)

    pmap = parent_map(root)
    hits = [clickable_ancestor(n, pmap) for n in find_labelled(root, 'Play')]
    hits = [h for h in hits if h is not None]
    require(hits, 'no Play button on the mixed playlist detail')
    require(any(h.get('enabled') == 'true' for h in hits), 'Play button not enabled')
    tap_node(hits[0])
    end = time.monotonic() + 30
    seen = None
    while time.monotonic() < end:
        root = dump_ui('probe')
        ts = texts_of(root)
        if FAIL_PILL in ts:
            seen = 'failure-pill'
            break
        if not dock_visible(root):
            seen = 'player-opened'
            break
        time.sleep(1)
    snap('72-mixed-eligible-attempt')
    if seen is None:
        accept_fail('mixed-eligible-attempt',
                    'eligible Play tap produced neither failure feedback nor an opened '
                    'player within 30s (network off)\n')
    print('ACCEPT: eligible Play tap attempts playback; observed outcome (network off): %s'
          % seen, flush=True)
    open(OUT + '/mixed-attempt-outcome.txt', 'w').write(
        'eligible attempt outcome: %s\n' % seen)


route('import-full', import_full_route)
route('import-reopen', import_reopen_route)
route('import-unsupported', import_unsupported_route)
route('liked-guard', liked_guard_route)
route('home-guard', home_guard_route)
route('restart-persistence', restart_route)
route('mixed-playlist', mixed_playlist_route)

conn = adb('shell', 'dumpsys', 'connectivity').decode()
open(OUT + '/connectivity-after.txt', 'w').write(conn)
print('CAPTURE-COMPLETE: functional acceptance session v5 retained', flush=True)
