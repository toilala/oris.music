#!/usr/bin/env python3
"""Home, player and settings at 1.3x system font scale, captured RESTED (feed content
settled, not the loading skeleton), then the original scale is restored (the captured
value, never a hardcoded default; an unset key is deleted, not written back as the
literal string 'null')."""
from playback_check import adb, back, tap, tap_mini_player, wait_label, screen, restore_setting, PKG
import time

adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
original_font = adb('shell', 'settings', 'get', 'system', 'font_scale').decode().strip()
print('FONT initial=' + repr(original_font), flush=True)
try:
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', PKG + '/com.orismusic.client.MainActivity')
    wait_label('Home', seconds=60)
    # Rested means the feed's real shelves, not the loading skeleton: wait for a shelf
    # header before photographing. A network miss still captures, loudly.
    try:
        wait_label('Quick picks', seconds=45)
    except Exception:
        print('WARN: home feed never settled at 1.3x; capturing unrested', flush=True)
    time.sleep(2)
    screen('largetext_home')
    # The full player at 1.3x: controls, titles and the seek row under enlarged text.
    tap_mini_player('Midnight City')
    wait_label('Lyrics', seconds=60)
    time.sleep(2)
    screen('largetext_player')
    back()
    wait_label('Home', seconds=30)
    tap('Library')
    tap('Settings', bottom=False)
    wait_label('Appearance')
    screen('largetext_settings')
finally:
    restore_setting('system', 'font_scale', original_font)
adb('shell', 'am', 'force-stop', PKG)
print('PASS: large text home, player and settings', flush=True)
