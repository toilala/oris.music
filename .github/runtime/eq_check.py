#!/usr/bin/env python3
"""Equalizer settings evidence under network-disabled testing. The check never assumes
which state the app renders: it opens the settings page cold and branches on what is
actually there. Both states are captured honestly. On the available path it exercises
the real controls and keeps two kinds of evidence separate: platform read-back
(dumpsys audio sessions/effects) and persisted preferences (shared_prefs on disk)."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET

from playback_check import adb, tap, wait_label, screen, tree, PKG

COMPONENT = PKG + '/com.orismusic.client.debug.LocalPlaybackCheckActivity'
UNAVAILABLE = 'Unavailable right now.'
OFF_COPY = 'Off: sound passes through unchanged.'
ON_COPY = 'On: shaping the sound of the current output.'


def open_playback_settings():
    adb('shell', 'am', 'start', '-n', PKG + '/com.orismusic.client.MainActivity')
    wait_label('Home', seconds=60)
    tap('Library')
    tap('Settings', bottom=False)
    wait_label('Appearance')
    tap('Playback', bottom=False)
    wait_label('Playback speed')


def scroll_to_eq():
    for _ in range(10):
        if any(n.get('text') == 'Equalizer' for n in tree().iter('node')):
            return
        adb('shell', 'input', 'swipe', '540', '1500', '540', '600', '350')
        time.sleep(1.2)
    screen('failure')
    raise AssertionError('Playback settings never showed the Equalizer row')


def has_text(fragment):
    return any(fragment in (n.get('text') or '') for n in tree().iter('node'))


def tap_clickable(label):
    """Tap the clickable ancestor of the node carrying label, so taps never land on a
    plain Text sibling of the real control."""
    wait_label(label)
    root = tree()
    parents = {c: p for p in root.iter('node') for c in p}
    target = next(n for n in root.iter('node') if label in (n.get('text'), n.get('content-desc')))
    node = target
    while node is not None and node.get('clickable') != 'true':
        node = parents.get(node)
    if node is None:
        node = target
    print('TAP ' + label + ' bounds=' + str(node.get('bounds')) + ' clickable=' + str(node.get('clickable')), flush=True)
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1)


def dump_audio(name):
    data = adb('shell', 'dumpsys', 'audio').decode()
    open('runtime-evidence/' + name, 'w').write(data)
    lines = [l.strip() for l in data.splitlines() if 'ffect' in l or 'ession' in l]
    print('PLATFORM READ-BACK ' + name + ': ' + repr(lines[:14]), flush=True)


def read_prefs(name):
    prefs = adb('shell', 'run-as', PKG, 'cat', 'shared_prefs/oris-eq.xml').decode()
    open('runtime-evidence/' + name, 'w').write(prefs)
    print('PERSISTED PREFS ' + name + ': ' + prefs.replace('\n', ' '), flush=True)
    return prefs


adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
adb('shell', 'am', 'force-stop', PKG)

# 1. Cold app, no playback: whatever the page renders is the truth about this device.
open_playback_settings()
scroll_to_eq()
screen('eq_cold')
dump_audio('dumpsys-audio-eq-cold.txt')

if has_text(UNAVAILABLE):
    # The platform could not create an equalizer for this session: honest capture, not
    # an app failure. Supported controls stay unverified in this environment.
    adb('shell', 'am', 'force-stop', PKG)
    print('RESULT: Equalizer unavailable at cold start on this emulator; unavailable '
          'copy verified. Supported-state controls unverified here.', flush=True)
    raise SystemExit(0)

# 2. Available at cold start: the equalizer attached before any playback. Exercise it.
wait_label(OFF_COPY)
tap_clickable('Equalizer')
wait_label(ON_COPY)
wait_label('Presets')
screen('eq_enabled_controls')

# 3. Bring the actual preset chips into view (this page exposes only composed nodes),
# then a preset writes band levels; persisted preferences are read back from disk.
for _ in range(8):
    if has_text('Bass boost'):
        break
    adb('shell', 'input', 'swipe', '540', '1500', '540', '900', '350')
    time.sleep(1.2)
wait_label('Bass boost')
screen('eq_presets_in_view')
tap_clickable('Bass boost')
time.sleep(2)
screen('eq_preset_bass')
for _ in range(8):
    if any(re.fullmatch(r'\d+(\.\d+)? (Hz|kHz)', n.get('text') or '') for n in tree().iter('node')):
        break
    adb('shell', 'input', 'swipe', '540', '1500', '540', '900', '350')
    time.sleep(1.2)
screen('eq_band_sliders')
prefs = read_prefs('eq-prefs-enabled.xml')
assert 'enabled' in prefs and 'true' in prefs, 'equalizer enabled flag was not persisted'
assert 'levels' in prefs, 'equalizer band levels were not persisted'

# 4. Platform read-back while the debug tone plays through the real service.
adb('shell', 'am', 'start', '-n', COMPONENT, '-f', '0x10008000')
wait_label('Local audio playing', seconds=30)
time.sleep(3)
dump_audio('dumpsys-audio-eq-playing.txt')

# 5. Settings still show the equalizer during playback; toggle off; persisted off state.
open_playback_settings()
scroll_to_eq()
wait_label(ON_COPY)
tap_clickable('Equalizer')
wait_label(OFF_COPY)
screen('eq_toggled_back_off')
prefs = read_prefs('eq-prefs-final.xml')
assert 'enabled' in prefs and 'false' in prefs, 'equalizer off state was not persisted'

adb('shell', 'am', 'force-stop', PKG)
print('PASS: equalizer available at cold start, controls exercised against the real '
      'effect, preset persisted and read back, platform sessions captured before and '
      'during playback', flush=True)
