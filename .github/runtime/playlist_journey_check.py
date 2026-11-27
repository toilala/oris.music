#!/usr/bin/env python3
"""Real playlist create/add/use journey through the shipping UI: create a playlist
from the Library dialog (real text entry), add a song to it from a library row's
song-actions sheet, open it and play from it into the real player. Journey-level
evidence only: the seeded ids do not resolve to audio, so audible playback stays
UNVERIFIED here (CI cannot resolve streams); the asserted product behavior is the
queue handoff into the player. Screenshots land in runtime-evidence/."""
import json
import subprocess
import time
from playback_check import adb, back, tap, wait_label, screen, tree, PKG

SONGS = [('Midnight City', 'M83'), ('Kathmandu', 'Bipul Chettri'), ('Heat Waves', 'Glass Animals'),
         ('Redbone', 'Childish Gambino'), ('Sunset Lover', 'Petit Biscuit')]
NAME = 'EvidenceMix'


def seed_liked():
    songs = [{'id': f'journey{i}', 'title': t, 'artist': a,
              'thumb': f'https://picsum.photos/seed/journey{i}/300/300'} for i, (t, a) in enumerate(SONGS)]
    blob = json.dumps(songs).replace('&', '&amp;').replace('"', '&quot;')
    xml = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>"
           f'<string name="liked">{blob}</string><string name="history">{blob}</string></map>')
    adb('shell', 'am', 'force-stop', PKG)
    subprocess.run(['adb', 'shell', f"run-as {PKG} sh -c 'mkdir -p shared_prefs && cat > shared_prefs/oris-library.xml'"],
                   input=xml.encode(), check=True)
    adb('shell', 'am', 'start', '-n', PKG + '/com.orismusic.client.MainActivity')


def tap_edittext():
    import re
    end = time.monotonic() + 15
    while time.monotonic() < end:
        for node in tree().iter('node'):
            if node.get('class') == 'android.widget.EditText':
                nums = [int(v) for v in re.findall(r'\d+', node.get('bounds'))]
                x = (nums[0] + nums[2]) // 2
                y = (nums[1] + nums[3]) // 2
                adb('shell', 'input', 'tap', str(x), str(y))
                return
        time.sleep(1)
    raise AssertionError('no EditText node in the create dialog')


seed_liked()
wait_label('Library', seconds=60)
tap('Library')
wait_label('Liked (5)', seconds=30)

# CREATE: real dialog, real keyboard input, real Create button.
tap('Playlists (0)')
wait_label('New playlist')
tap('New playlist')
wait_label('Name')
tap_edittext()
adb('shell', 'input', 'text', NAME)
time.sleep(1)
screen('playlist_create_dialog')
tap('Create', bottom=False)
wait_label(NAME, seconds=15)
screen('playlist_created_open')
print('PASS: playlist created through the real dialog', flush=True)

# ADD: song-actions sheet on a Liked row now lists the playlist; tap it.
# System Back from the detail must step back to the list (regression: there was no
# BackHandler for an open playlist, so Back left the Library altogether).
back()
wait_label('Playlists (1)')
tap('Liked (5)')
wait_label('Midnight City')
tap('Song actions')
wait_label(NAME, seconds=15)
screen('playlist_in_sheet')
tap(NAME)
time.sleep(1)
tap('Playlists (1)')
wait_label('1 songs')
screen('playlist_after_add')
print('PASS: song added to the playlist from the row sheet', flush=True)

# USE: open the playlist, play the song from it, land in the real player with
# that queue. Audible playback is UNVERIFIED (seeded ids cannot resolve in CI);
# the assertion is the queue handoff, evidenced by the queue screen after.
tap(NAME)
wait_label('Midnight City')
screen('playlist_detail')
tap('Midnight City')
wait_label('Lyrics', seconds=90)
screen('playlist_use_player')
back()
time.sleep(1)
tap('Queue')
wait_label('Midnight City', seconds=30)
screen('playlist_use_queue')
print('PASS: playing from the playlist hands its queue to the player', flush=True)
print('UNVERIFIED: audible stream playback (CI cannot resolve streams)', flush=True)
print('PASS: playlist create/add/use journey', flush=True)
