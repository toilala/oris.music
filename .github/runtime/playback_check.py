#!/usr/bin/env python3
"""Deterministic navigation smoke check. Never asks YouTube to play a song."""
import base64
import glob
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUT = 'runtime-evidence'
PKG = 'com.orismusic.client.debug'
os.makedirs(OUT, exist_ok=True)

def adb(*args):
    return subprocess.check_output(['adb', *args])

def screen(name):
    data = adb('exec-out', 'screencap', '-p')
    open(f'{OUT}/{name}.png', 'wb').write(data)
    encoded = base64.b64encode(data).decode()
    for start in range(0, len(encoded), 3000):
        print(f'ORIS_IMAGE {name} {encoded[start:start+3000]}', flush=True)

def tree():
    try:
        adb('shell', 'uiautomator', 'dump', '/sdcard/oris-nav.xml')
        return ET.fromstring(adb('shell', 'cat', '/sdcard/oris-nav.xml'))
    except (subprocess.CalledProcessError, ET.ParseError):
        return ET.Element('hierarchy')

def wait_label(label, seconds=25):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        root = tree()
        for node in root.iter('node'):
            if label in (node.get('text'), node.get('content-desc')):
                return node
        time.sleep(1)
    screen('failure')
    print(adb('logcat', '-d', '-s', 'AndroidRuntime').decode(), flush=True)
    raise AssertionError(f'UI never showed {label}')

def tap(label):
    wait_label(label)
    nodes = [n for n in tree().iter('node') if label in (n.get('text'), n.get('content-desc'))]
    node = nodes[-1]  # Bottom navigation, not the Home header's duplicate Search icon.
    points = list(map(int, re.findall(r'\d+', node.get('bounds'))))
    adb('shell', 'input', 'tap', str((points[0]+points[2])//2), str((points[1]+points[3])//2))
    time.sleep(1)

def back():
    adb('shell', 'input', 'keyevent', '4')
    time.sleep(1)

def still_open():
    assert PKG in adb('shell', 'dumpsys', 'activity', 'activities').decode().split('mResumedActivity:')[-1].split('\n')[0]

adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
adb('install', '-r', glob.glob('app/build/outputs/apk/foss/debug/*.apk')[0])
adb('shell', 'am', 'start', '-n', PKG+'/com.orismusic.client.MainActivity')
wait_label('Home', seconds=60)
tap('Queue')
wait_label('Nothing queued yet. Pick a song on Home.')
screen('queue')
tap('Open player')
wait_label('Now playing')
wait_label('Play')
screen('player_idle')
back()
wait_label('Nothing queued yet. Pick a song on Home.')
still_open()
screen('back_to_queue')
back()
wait_label('OrisMusic')
still_open()
screen('back_to_home')
tap('Search')
tap('Library')
back()
wait_label('Search music')
still_open()
back()
wait_label('OrisMusic')
still_open()
tap('Search')
wait_label('Search music')
screen('search_empty')
edit = next(n for n in tree().iter('node') if n.get('class') == 'android.widget.EditText')
xy = list(map(int, re.findall(r'\d+', edit.get('bounds'))))
adb('shell', 'input', 'tap', str((xy[0]+xy[2])//2), str((xy[1]+xy[3])//2))
adb('shell', 'input', 'text', 'Yesterday')
time.sleep(2)
adb('shell', 'input', 'keyevent', '66')
# Search is permitted; this check never taps a song or resolves an audio stream.
end = time.monotonic()+45
while time.monotonic() < end:
    nodes = list(tree().iter('node'))
    if any('Yesterday' in (n.get('text') or '') and n.get('class') != 'android.widget.EditText' for n in nodes):
        break
    time.sleep(1)
else:
    screen('search_failure')
    raise AssertionError('No search results for Yesterday')
screen('search_results')
# Clearing query reveals persisted recent searches.
tap('Clear search')
wait_label('Recent searches')
screen('search_history')
back()
wait_label('OrisMusic')
still_open()
back()
time.sleep(1)
focus = adb('shell', 'dumpsys', 'window').decode()
assert not any(PKG in line for line in focus.splitlines() if 'mCurrentFocus' in line)
print('PASS: idle player, nested Back, tab history, Home system exit; no playback network request', flush=True)
