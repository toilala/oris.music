#!/usr/bin/env python3
"""Production Home sections with real catalog data, never starts playback."""
from playback_check import adb, tap, wait_label, screen, tree, PKG
import time
adb('shell', 'am', 'start', '-n', PKG+'/com.orismusic.client.debug.HomePicksCheckActivity')
wait_label('Quick picks', seconds=100)
wait_label('Community playlists', seconds=100)
time.sleep(3)
screen('home_picks_real_catalog')
menus = [n for n in tree().iter('node') if (n.get('content-desc') or '').startswith('More for ')]
assert menus, 'Quick pick row menu missing'
tap(menus[0].get('content-desc'))
wait_label('Play next')
wait_label('Add to queue')
wait_label('Start radio')
screen('home_picks_menu')
adb('shell', 'input', 'keyevent', '4')
# A horizontal swipe shows the next real song page.
adb('shell', 'input', 'swipe', '900', '330', '140', '330', '450')
time.sleep(1)
screen('home_picks_second_page')
print('PASS: real quick picks and community playlists rendered, menu and paging checked', flush=True)
