#!/usr/bin/env python3
"""Screenshots of the library, playlists and settings screens. Never plays audio."""
from playback_check import adb, tap, wait_label, screen, PKG
import re
import time
import subprocess

# Capture the cold entrance, not only the settled UI. The video must be inspected.
record = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '16', '/sdcard/home-entry.mp4'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'am', 'start', '-n', PKG + '/com.orismusic.client.MainActivity')
wait_label('Home', seconds=60)
time.sleep(3)
screen('shot_home')
wait_label('Oris')
record.communicate(timeout=25)
adb('pull', '/sdcard/home-entry.mp4', 'runtime-evidence/home-entry.mp4')
tap('Library')
wait_label('Liked (0)')
screen('shot_library')
tap('Playlists (0)')
wait_label('New playlist')
screen('shot_playlists')
tap('Library')
tap('Settings', bottom=False)
wait_label('Appearance')
screen('shot_settings')
tap('Navigation', bottom=False)
wait_label('Selection effect')
screen('shot_navigation')

# Seed a few songs so the filled-in screens can be photographed.
import json, subprocess
artists = ['Glass Animals', 'glass animals', 'Christina Perri', 'christina perri', 'M83', 'Bipul Chettri']
songs = [{'id': f'seed{i}', 'title': f'Seed song {i} with a rather long title to wrap', 'artist': artists[i % 6]} for i in range(24)]
counts = json.dumps({f'seed{i}': (i % 5) + 1 for i in range(24)}).replace('&', '&amp;').replace('"', '&quot;')
liked = songs[:5]
blob = json.dumps(songs).replace('&', '&amp;').replace('"', '&quot;')
likedblob = json.dumps(liked).replace('&', '&amp;').replace('"', '&quot;')
xml = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>"
       f'<string name="liked">{likedblob}</string><string name="history">{blob}</string><string name="counts">{counts}</string></map>')
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
wait_label('Appearance')
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
time.sleep(12)
for i in range(8):
    adb('shell', 'input', 'swipe', '540', '1200', '540', '400', '350')
    time.sleep(1.5)
    crash = adb('logcat', '-d', '-b', 'crash').decode()
    dead = [p for p in re.findall(r'FATAL EXCEPTION[^\n]*\nProcess: ([^,]+)', crash) if p.strip() == PKG]
    if dead:
        print(crash[-3000:], flush=True)
        screen('failure')
        raise AssertionError('app crashed while scrolling Home')
screen('shot_home_scrolled')
tap('Library')
wait_label('Liked (5)')
screen('shot_library_filled')
tap('Search')
wait_label('Songs, albums, artists')
screen('shot_search')
tap('Library')
tap('Queue')
wait_label('Back from queue')
screen('shot_queue')
tap('Open player')
wait_label('Lyrics', seconds=90)
time.sleep(1)
screen('shot_player_idle')
tap('More')
wait_label('Shuffle')
wait_label('Share')
wait_label('Sleep')
screen('player_more')
tap('Sleep')
wait_label('Stop when this song ends')
screen('player_sleep_menu')
print('PASS: ui screenshots', flush=True)

adb('shell', 'am', 'start', '-n', PKG+'/com.orismusic.client.debug.ControlStylesCheckActivity')
wait_label('Player styles')
screen('control_styles_paused')
tap('Soft pebble', bottom=False)
time.sleep(1)
screen('control_styles_playing')
style_record = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '6', '/sdcard/control-motion.mp4'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
style_record.communicate(timeout=15)
adb('pull', '/sdcard/control-motion.mp4', 'runtime-evidence/control-motion.mp4')
