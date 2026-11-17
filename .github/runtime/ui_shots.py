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
tap('Home')
tap('Settings', bottom=False)
wait_label('Skip silence')
screen('shot_settings')
print('PASS: ui screenshots', flush=True)
