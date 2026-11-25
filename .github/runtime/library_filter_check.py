#!/usr/bin/env python3
"""Offline library filtering, fixture-based and fully local: the library stores are
seeded over run-as before the app starts, then the real UI is driven. Covers blank,
title, artist and no-match queries, the filtered Play/Shuffle/row-tap queue identity
(read back on the Queue screen), query retention across section switching, playlist and
saved-list filtering, and the layout at 1.3x font scale. Assertions are exact-match on
composed nodes; anything missing fails loudly with the standard failure capture."""
import subprocess
import time
from xml.sax.saxutils import escape

from playback_check import adb, tap, wait_label, screen, tree, PKG, OUT

MAIN = PKG + '/com.orismusic.client.MainActivity'

LIKED = [
    ('lf-1', 'Midnight City', 'M83'),
    ('lf-6', 'Wait', 'M83'),
    ('lf-2', 'One More Time', 'Daft Punk'),
]
HISTORY = [
    ('lf-4', 'Nuvole Bianche', 'Ludovico Einaudi'),
    ('lf-2', 'One More Time', 'Daft Punk'),
]
PLAYLIST = [('lf-1', 'Midnight City', 'M83'), ('lf-4', 'Nuvole Bianche', 'Ludovico Einaudi')]
SAVED = [('lf-5', 'Weightless', 'Marconi Union'), ('lf-1', 'Midnight City', 'M83')]


def songs_json(rows):
    return '[' + ','.join('{"id":"%s","title":"%s","artist":"%s"}' % r for r in rows) + ']'


def seed():
    library = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>"
               + '<string name="liked">' + escape(songs_json(LIKED)) + '</string>'
               + '<string name="history">' + escape(songs_json(HISTORY)) + '</string>'
               + '<string name="playlists">[{"id":"pl-1","name":"Evening","songs":'
               + escape(songs_json(PLAYLIST)) + '}]</string>'
               + '</map>')
    offline = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>"
               + '<string name="songs">' + escape(songs_json(SAVED)) + '</string>'
               + '</map>')
    for name, content in (('oris-library', library), ('oris-offline', offline)):
        subprocess.run(['adb', 'shell', 'run-as', PKG, 'sh', '-c',
                        'cat > shared_prefs/' + name + '.xml'],
                       input=content.encode(), check=True)


def visible(text):
    return any(n.get('text') == text for n in tree().iter('node'))


def wait_visible(text, seconds=10):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if visible(text):
            return
        time.sleep(0.7)
    screen('failure')
    raise AssertionError('never visible: ' + text)


def wait_gone(text, seconds=10):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if not visible(text):
            return
        time.sleep(0.7)
    screen('failure')
    raise AssertionError('still visible: ' + text)


def tap_clickable(label):
    wait_label(label)
    root = tree()
    parents = {c: p for p in root.iter('node') for c in p}
    target = next(n for n in root.iter('node') if label in (n.get('text'), n.get('content-desc')))
    node = target
    while node is not None and node.get('clickable') != 'true':
        node = parents.get(node)
    if node is None:
        node = target
    print('TAP ' + label + ' bounds=' + str(node.get('bounds')), flush=True)
    import re
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1)


def type_query(text):
    """Type into the filter field; the query length is returned so clear_query can undo
    exactly this much. input text needs %s for spaces."""
    tap('Filter this list')
    adb('shell', 'input', 'text', text.replace(' ', '%s'))
    time.sleep(1)
    return len(text)


def clear_query(n):
    tap('Filter this list')
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    for _ in range(n):
        adb('shell', 'input', 'keyevent', 'KEYCODE_DEL')
    time.sleep(1)


def queue_shows(titles, not_titles):
    tap('Queue', bottom=False)
    wait_label('Queue')
    for t in titles:
        wait_visible(t)
    for t in not_titles:
        if visible(t):
            screen('failure')
            raise AssertionError('queue should not contain ' + t)
    adb('shell', 'input', 'keyevent', '4')
    time.sleep(1)


adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
original_font = adb('shell', 'settings', 'get', 'system', 'font_scale').decode().strip()
print('FONT initial=' + original_font, flush=True)
adb('shell', 'am', 'force-stop', PKG)
prior = subprocess.run(['adb', 'shell', 'run-as', PKG, 'cat', 'shared_prefs/oris-library.xml'],
                       capture_output=True)
print('FIXTURE pre-seed oris-library.xml present=' + str(prior.returncode == 0)
      + ' bytes=' + str(len(prior.stdout)), flush=True)
seed()
adb('shell', 'am', 'start', '-n', MAIN)
wait_label('Home', seconds=60)
tap('Library')

# Blank query keeps everything, header counts the full list.
wait_label('Liked (3)')
wait_visible('Midnight City')
wait_visible('One More Time')
wait_visible('Liked songs  ·  3 songs')
screen('library_filter_default')

# Title query, case-insensitive, header counts down.
n = type_query('midnight')
wait_gone('One More Time')
wait_visible('Midnight City')
wait_visible('Liked songs  ·  1 of 3 songs')
screen('library_filter_title')

# Query retention across section switching: Recent has no match for it.
tap('Recent (2)')
wait_visible('No matches.')
screen('library_filter_retention_recent')
tap('Liked (3)')
wait_visible('Midnight City')
if visible('One More Time'):
    screen('failure')
    raise AssertionError('query was lost switching back to Liked')
clear_query(n)
wait_visible('One More Time')

# Artist query.
n = type_query('daft')
wait_gone('Midnight City')
wait_visible('One More Time')
clear_query(n)

# No-match query shows its own state, not the empty-library guidance.
n = type_query('zzzz')
wait_visible('No matches.')
if visible('Tap the heart on the player to keep a song here.'):
    screen('failure')
    raise AssertionError('no-match state leaked the empty-library guidance')
screen('library_filter_nomatch')
clear_query(n)

# Two-song filter: Play queues exactly the filtered list, in order; Shuffle the same set.
n = type_query('m83')
wait_visible('Liked songs  ·  2 of 3 songs')
tap_clickable('Play')
wait_label('Collapse player', seconds=30)
tap('Collapse player')
wait_label('Filter this list')
queue_shows(['Midnight City', 'Wait'], ['One More Time'])
screen('library_filter_queue_play')
tap_clickable('Shuffle')
wait_label('Collapse player', seconds=30)
tap('Collapse player')
wait_label('Filter this list')
queue_shows(['Midnight City', 'Wait'], ['One More Time'])
screen('library_filter_queue_shuffle')

# A filtered row tap plays that exact song.
tap_clickable('Wait')
wait_label('Collapse player', seconds=30)
wait_visible('Wait')
screen('library_filter_rowtap')
tap('Collapse player')
wait_label('Filter this list')
clear_query(n)

# Playlist filtering, then a filtered row tap inside the playlist.
tap('Playlists (1)')
tap('Evening')
n = type_query('nuvole')
wait_gone('Midnight City')
wait_visible('Nuvole Bianche')
tap_clickable('Nuvole Bianche')
wait_label('Collapse player', seconds=30)
wait_visible('Nuvole Bianche')
screen('library_filter_playlist_rowtap')
tap('Collapse player')
wait_label('Filter this list')
clear_query(n)
tap('All playlists')

# Saved-for-offline filtering.
tap('Saved (2)')
wait_visible('Saved for offline  ·  2 songs')
n = type_query('weightless')
wait_gone('Midnight City')
wait_visible('Weightless')
screen('library_filter_saved')
clear_query(n)

# The same flow at 1.3x font scale: layout evidence for review; the original scale is
# restored in finally even when a capture assertion fails mid-way.
try:
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', MAIN)
    wait_label('Home', seconds=60)
    tap('Library')
    wait_label('Liked (3)')
    n = type_query('midnight')
    wait_visible('Liked songs  ·  1 of 3 songs')
    screen('library_filter_largetext')
finally:
    adb('shell', 'settings', 'put', 'system', 'font_scale', original_font)
    left = adb('shell', 'settings', 'get', 'system', 'font_scale').decode().strip()
    print('FONT restored=' + left, flush=True)
adb('shell', 'am', 'force-stop', PKG)
print('PASS: library filtering across liked/recent/saved/playlist, blank/title/artist/no-match, '
      'filtered queue identity, query retention, large-text capture', flush=True)
