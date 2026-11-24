#!/usr/bin/env python3
"""Home and settings at 1.3x system font scale, then back to default."""
from playback_check import adb, tap, wait_label, screen, PKG
import time

adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
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
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
adb('shell', 'am', 'force-stop', PKG)
print('PASS: large text home and settings', flush=True)
