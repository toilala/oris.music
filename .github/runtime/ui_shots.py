#!/usr/bin/env python3
"""Screenshots of the library, playlists and settings screens. Never plays audio."""
from playback_check import adb, tap, wait_label, screen, PKG
import time

adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'am', 'start', '-n', PKG + '/com.orismusic.client.MainActivity')
wait_label('Home', seconds=60)
time.sleep(3)
screen('shot_home')
tap('Library')
wait_label('Liked (0)')
screen('shot_library')
tap('Playlists (0)')
wait_label('New playlist')
screen('shot_playlists')
tap('Library')
tap('Settings', bottom=False)
wait_label('Skip silence')
screen('shot_settings')

# Seed a few songs so the filled-in screens can be photographed.
import json, subprocess
songs = [{'id': f'seed{i}', 'title': t, 'artist': a} for i, (t, a) in enumerate([
    ('Midnight City', 'M83'), ('Kathmandu', 'Bipul Chettri'), ('Heat Waves', 'Glass Animals'),
    ('Redbone', 'Childish Gambino'), ('Sunset Lover', 'Petit Biscuit')])]
blob = json.dumps(songs).replace('&', '&amp;').replace('"', '&quot;')
xml = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>"
       f'<string name="liked">{blob}</string><string name="history">{blob}</string></map>')
adb('shell', 'am', 'force-stop', PKG)
subprocess.run(['adb', 'shell', f"run-as {PKG} sh -c 'mkdir -p shared_prefs && cat > shared_prefs/oris-library.xml'"],
               input=xml.encode(), check=True)
adb('shell', 'am', 'start', '-n', PKG + '/com.orismusic.client.MainActivity')
wait_label('Home', seconds=60)
time.sleep(3)
screen('shot_home_jump')
tap('Library')
wait_label('Liked (5)')
screen('shot_library_filled')
tap('Search')
wait_label('Search music')
screen('shot_search')
tap('Library')
tap('Queue')
wait_label('Nothing queued yet. Pick a song on Home.')
screen('shot_queue')
tap('Open player')
wait_label('Collapse player')
time.sleep(1)
screen('shot_player_idle')
print('PASS: ui screenshots', flush=True)
