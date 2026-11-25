#!/usr/bin/env python3
"""Functional acceptance session v4: Library search query/results/change/clear and
import Preview/create/reopen persistence, on seeded local data only. Each route
establishes its own current screen from the live tree before acting, verifies the
expected post-action context, and retains before/after tree+pixels. Selectors
resolve labels to their clickable ancestor or to the EditText containing the label
(shapes validated offline against retained v2/v3 trees). No repeated root BACK as
a reset: recovery re-anchors via the dock. A failing route writes ACCEPTANCE-FAIL
evidence and stops itself; nothing is retried or repaired. Network stays off for
the whole session; no playback, no live-service requests."""
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
NOMATCH = 'Nothing in your library matches'
CHIP_RE = re.compile(r'^(Liked|Recent|Playlists|Saved) \(\d+\)$')
DOCK = ('Home', 'Search', 'Library')

IMPORT_LINES = ['Midnight City - M83', 'Aurora - Bicep', 'Aurora - Bicep', 'broken line']
IMPORT_NAME = 'Acceptance check'
SONGS_LABEL = 'Songs as Title - Artist, one per line, or a CSV export'
UNSUPPORTED_LINK = 'https://music.youtube.com/playlist'
UNSUPPORTED_REASON = "Playlist links can't be read offline"


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
    """Tap the EditText whose bounds contain the given label/placeholder, then verify
    an EditText actually holds focus. Returns False without tapping when no field
    contains the label."""
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
    """Tap a dock entry: the BOTTOMMOST clickable ancestor of the label, so the
    Search screen's own Library mode chip (higher on screen) is never mistaken
    for the dock's Library entry."""
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


def ensure_search_screen(tag):
    root = dump_ui('anchor-%s' % tag)
    if not dock_visible(root):
        snap('anchor-lost-%s' % tag)
        open(OUT + '/ANCHOR-LOST.txt', 'w').write(
            'dock not visible while anchoring for %s; session stopped\n' % tag)
        raise SessionAbort(tag)
    require(tap_dock('Search', 'tap-search-%s.txt' % tag),
            'dock Search not resolvable (%s)' % tag)
    return dump_ui('search-screen-%s' % tag)


def select_library_mode(log_name):
    """Tap the Search-screen Library mode chip: the topmost clickable ancestor of a
    Library label (the dock's Library sits lower). Validated against retained trees."""
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
    open(OUT + '/' + log_name, 'w').write(
        'candidates for Library-mode chip (topmost resolved target tapped):\n'
        + ('\n'.join(notes) if notes else 'none'))
    if not hits:
        return False
    chosen = min(hits, key=lambda a: center(a)[1])
    x, y = center(chosen)
    adb('shell', 'input', 'tap', str(x), str(y))
    time.sleep(1.5)
    return True


def hint_visible(root):
    return any(HINT in (n.get('text') or '') for n in root.iter('node'))


def clear_and_retype(query, tap_log, field_log):
    require(tap_label('Clear search', tap_log), 'Clear search action not resolvable')
    root = dump_ui('probe')
    require(hint_visible(root), 'clear did not restore the blank-query hint')
    require(focus_field('Songs, albums, artists', field_log),
            'search field not focusable for %r' % query)
    type_text(query)
    time.sleep(2)


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


def search_functional_route():
    ensure_search_screen('sf')
    require(select_library_mode('mode-select.txt'),
            'no Library-labelled candidate resolves on the Search screen')
    root = snap('20-search-library-mode')
    require(hint_visible(root), 'Library-mode blank-query hint missing after mode tap')

    require(focus_field('Songs, albums, artists', 'tap-field-1.txt'),
            'search field not focusable for first query')
    type_text('midnight')
    time.sleep(2)
    root = snap('21-search-results-midnight')
    ts = texts_of(root)
    if not ('Midnight City' in ts and 'M83' in ts):
        accept_fail('search-query',
                    'query "midnight" did not surface the seeded liked song; texts=%r\n'
                    % ts[:20])
    if 'One More Time' in ts:
        accept_fail('search-query',
                    'query "midnight" leaked a non-matching song; texts=%r\n' % ts[:20])
    print('ACCEPT: Library search query "midnight" returns the seeded Midnight City row',
          flush=True)

    clear_and_retype('nuvole', 'tap-clear-1.txt', 'tap-field-2.txt')
    root = snap('22-search-results-changed')
    ts = texts_of(root)
    if not ('Nuvole Bianche' in ts and 'Ludovico Einaudi' in ts):
        accept_fail('search-change',
                    'changed query "nuvole" did not surface the seeded history song; texts=%r\n'
                    % ts[:20])
    if 'Midnight City' in ts:
        accept_fail('search-change',
                    'stale Midnight City row after query change; texts=%r\n' % ts[:20])
    print('ACCEPT: query change replaces results (history song found, prior result gone)',
          flush=True)

    clear_and_retype('zzzz', 'tap-clear-2.txt', 'tap-field-3.txt')
    root = snap('23-search-nomatch')
    ts = texts_of(root)
    if not any(NOMATCH in t for t in ts):
        accept_fail('search-nomatch',
                    'no-match state text missing for "zzzz"; texts=%r\n' % ts[:20])
    print('ACCEPT: no-match query renders the empty state', flush=True)

    require(tap_label('Clear search', 'tap-clear-3.txt'),
            'Clear search action not resolvable (final)')
    root = snap('24-search-cleared')
    if not hint_visible(root):
        accept_fail('search-clear',
                    'clear did not restore the blank-query hint; texts=%r\n'
                    % texts_of(root)[:20])
    print('ACCEPT: clear restores the initial Library-mode state', flush=True)
route('search-functional', search_functional_route)


def import_functional_route():
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
    ok = (any('3 songs ready' in t for t in ts)
          and any('1 duplicate collapsed' in t for t in ts)
          and any("1 line couldn't be read" in t for t in ts)
          and any("can't play until matched" in t for t in ts))
    if not ok:
        accept_fail('import-preview',
                    'preview summary mismatch (expected 3 ready, 1 duplicate, 1 skipped, '
                    'unmatched warning); texts=%r\n' % ts[:24])
    print('ACCEPT: preview reports 3 ready, 1 duplicate collapsed, 1 line skipped, '
          'unmatched-play warning', flush=True)

    require(tap_label('Import', 'tap-import-confirm.txt'),
            'Import confirm action not resolvable')
    root = snap('32-import-created')
    ts = texts_of(root)
    if not (IMPORT_NAME in ts and any('Not matched yet' in t for t in ts)):
        accept_fail('import-create',
                    'created playlist detail missing name or unmatched markers; texts=%r\n'
                    % ts[:24])
    print('ACCEPT: import creates the playlist and lists songs with unmatched markers',
          flush=True)

    chip = next((t for t in ts if t.startswith('Playlists') and CHIP_RE.match(t)), None)
    require(chip, 'no Playlists chip on the created playlist detail')
    require(tap_label(chip, 'tap-playlists-chip-impf.txt'),
            'Playlists chip not resolvable on detail')
    root = snap('33-playlists-after-create')
    require(IMPORT_NAME in texts_of(root), 'created playlist not listed on Playlists')
    require(tap_label(IMPORT_NAME, 'tap-reopen.txt'),
            'created playlist row not resolvable')
    root = snap('34-import-reopened')
    ts = texts_of(root)
    if not (IMPORT_NAME in ts and 'Midnight City' in ts
            and ts.count('Aurora') >= 1 and any('Not matched yet' in t for t in ts)):
        accept_fail('import-reopen',
                    'reopened playlist lost rows or unmatched markers; texts=%r\n'
                    % ts[:24])
    print('ACCEPT: reopened playlist persists rows and unmatched markers', flush=True)

    require(tap_label(chip, 'tap-playlists-chip-impf2.txt'),
            'Playlists chip not resolvable (unsupported-input pass)')
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
    imp = []
    for n in find_labelled(root, 'Import'):
        anc = clickable_ancestor(n, parent_map(root))
        if anc is not None:
            imp.append('enabled=%s bounds=%s' % (anc.get('enabled'), anc.get('bounds')))
    open(OUT + '/import-unsupported-button.txt', 'w').write(
        'Import confirm candidates after unsupported preview:\n'
        + ('\n'.join(imp) if imp else 'none'))
    print('ACCEPT: unsupported playlist-link input rejected with its reason', flush=True)

    require(tap_label('Cancel', 'tap-import-cancel.txt'),
            'dialog Cancel not resolvable (second pass)')
    root = snap('37-import-cancelled')
    require(chips_present(root), 'dialog did not dismiss back to the chip list')
route('import-functional', import_functional_route)


conn = adb('shell', 'dumpsys', 'connectivity').decode()
open(OUT + '/connectivity-after.txt', 'w').write(conn)
print('CAPTURE-COMPLETE: functional acceptance session retained', flush=True)
