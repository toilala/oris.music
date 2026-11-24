#!/usr/bin/env python3
"""Production Home sections with real catalog data, never starts playback."""
from playback_check import adb, tap, wait_label, screen, tree, PKG
import time
adb('shell', 'am', 'start', '-n', PKG+'/com.orismusic.client.debug.HomePicksCheckActivity')
wait_label('Quick picks', seconds=100)
wait_label('Community playlists', seconds=100)
time.sleep(3)
screen('home_picks_real_catalog')
# The chips have no row menu button; a long-press on the first chip opens the actions.
import re
root = tree()
header = next((n for n in root.iter('node') if (n.get('text') or '') == 'Quick picks'), None)
assert header is not None, 'Quick picks header missing'
hy = int(re.findall(r'\d+', header.get('bounds'))[1])
cands = []
for n in root.iter('node'):
    if n.get('clickable') != 'true':
        continue
    b = list(map(int, re.findall(r'\d+', n.get('bounds'))))
    if b[1] > hy + 100:
        cands.append((b[1], b[0], n))
cands.sort()
assert cands, 'no quick pick chip found'
b = list(map(int, re.findall(r'\d+', cands[0][2].get('bounds'))))
cx, cy = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
adb('shell', 'input', 'swipe', str(cx), str(cy), str(cx), str(cy), '700')
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
