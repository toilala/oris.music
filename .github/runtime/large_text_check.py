#!/usr/bin/env python3
"""Home and settings at 1.3x system font scale, then the original scale is restored
(the captured value, never a hardcoded default; an unset key is deleted, not written
back as the literal string 'null')."""
from playback_check import adb, tap, wait_label, screen, restore_setting, PKG
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
    time.sleep(2)
    screen('largetext_home')
    tap('Library')
    tap('Settings', bottom=False)
    wait_label('Appearance')
    screen('largetext_settings')
finally:
    restore_setting('system', 'font_scale', original_font)
adb('shell', 'am', 'force-stop', PKG)
print('PASS: large text home and settings', flush=True)
