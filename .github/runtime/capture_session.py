#!/usr/bin/env python3
"""Capture-only UI session over the seeded local state. No assertions, no playback,
no query typing: each step retains a raw uiautomator tree dump and a screenshot
before and after one minimal navigation step. If a route's context differs from
what the tree shows, the route stops with its evidence instead of guessing.
Network stays off; Home renders its offline state (no feed fixture exists)."""
import glob
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

from playback_check import adb, tap, wait_label, screen, OUT, PKG

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


def tap_desc_clickable(desc):
    """Tap a clickable node carrying this exact content-desc (e.g. the stats IconButton,
    which shares its label with the Stats view title text)."""
    end = time.monotonic() + 20
    while time.monotonic() < end:
        nodes = [n for n in dump_ui('probe').iter('node')
                 if n.get('content-desc') == desc and n.get('clickable') == 'true']
        if nodes:
            p = list(map(int, re.findall(r'\d+', nodes[0].get('bounds'))))
            adb('shell', 'input', 'tap', str((p[0] + p[2]) // 2), str((p[1] + p[3]) // 2))
            time.sleep(1.5)
            return True
        time.sleep(1)
    return False


def tap_mode_chip(label):
    """Tap the Search mode chip from the CURRENT captured tree, never from assumptions:
    candidates carry the label as text or desc; the chip sits above the nav dock, so the
    topmost candidate wins. Every candidate's attributes are logged for review."""
    root = dump_ui('probe')
    found = [n for n in root.iter('node')
             if label in (n.get('text'), n.get('content-desc'))]
    notes = []
    for n in found:
        p = list(map(int, re.findall(r'\d+', n.get('bounds'))))
        notes.append('bounds=%s clickable=%s checkable=%s desc=%s text=%s'
                     % (n.get('bounds'), n.get('clickable'), n.get('checkable'),
                        n.get('content-desc'), n.get('text')))
    open(OUT + '/mode-select.txt', 'w').write(
        'candidates for %r (topmost tapped):\n' % label + '\n'.join(notes))
    if not found:
        return None
    def center_y(n):
        p = list(map(int, re.findall(r'\d+', n.get('bounds'))))
        return (p[1] + p[3]) // 2
    chosen = min(found, key=center_y)
    p = list(map(int, re.findall(r'\d+', chosen.get('bounds'))))
    adb('shell', 'input', 'tap', str((p[0] + p[2]) // 2), str((p[1] + p[3]) // 2))
    time.sleep(1.5)
    return chosen


def step(name, fn):
    try:
        fn()
    except Exception as e:
        open(OUT + '/ABORT.txt', 'w').write('step %s aborted: %r\n' % (name, e))
        try:
            snap('abort-' + name)
        except Exception:
            pass
        print('ABORT at %s: %r (evidence preserved, session stopped)' % (name, e), flush=True)
        sys.exit(1)


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
step('home', lambda: snap('01-home'))

step('library', lambda: (tap('Library'), snap('02-library')))

def stats_in():
    if not tap_desc_clickable('Listening stats'):
        raise AssertionError('stats icon not found as clickable desc')
step('stats', lambda: (stats_in(), snap('03-stats')))

def stats_scroll():
    adb('shell', 'input', 'swipe', '540', '1500', '540', '700', '400')
    time.sleep(1)
step('stats-scrolled', lambda: (stats_scroll(), snap('04-stats-scrolled')))

def stats_out():
    if not tap_desc_clickable('Listening stats'):
        raise AssertionError('stats icon not found for exit')
step('playlists', lambda: (stats_out(), tap('Playlists (1)'), snap('05-playlists')))

step('playlist-open', lambda: (tap('Imported mix'), snap('06-playlist-open')))

step('import-dialog', lambda: (tap('All playlists'), tap('Import playlist'), snap('07-import-dialog')))

step('search-online', lambda: (tap('Cancel'), tap('Search'), snap('08-search-online')))

def library_mode():
    snap('09-search-before-mode-tap')
    if tap_mode_chip('Library') is None:
        raise AssertionError('no Library-labelled node on the Search screen')
    time.sleep(1)
    root = snap('10-search-library-mode')
    if not any(HINT in (n.get('text') or '') for n in root.iter('node')):
        open(OUT + '/CONTEXT-MISMATCH.txt', 'w').write(
            'post-tap tree lacks the Library-mode hint; mode state uncertain, route stopped\n')
        raise AssertionError('post-tap context differs, route stopped with evidence')
step('search-library-mode', library_mode)

conn = adb('shell', 'dumpsys', 'connectivity').decode()
open(OUT + '/connectivity-after.txt', 'w').write(conn)
print('CAPTURE-COMPLETE: 10 named tree+screenshot pairs retained', flush=True)
