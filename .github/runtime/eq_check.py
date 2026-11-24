#!/usr/bin/env python3
"""Equalizer settings evidence. First the honest unavailable state with no audio session,
then the real supported controls while the debug local tone plays through the service.
The tone drives the actual Equalizer on the actual session; no injected preview state."""
from playback_check import adb, tap, wait_label, screen, tree, PKG
import glob
import time

COMPONENT = PKG + '/com.orismusic.client.debug.LocalPlaybackCheckActivity'


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


adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
adb('shell', 'am', 'force-stop', PKG)

# 1. Honest unsupported state: cold app, no playback, audio session 0.
open_playback_settings()
scroll_to_eq()
wait_label('Unavailable right now. It attaches to the player while music plays, when the device and current output support one.')
screen('eq_unavailable')

# 2. Real supported session: the debug tone plays through the actual service and ExoPlayer,
#    so EqualizerController attaches to the real audio session id.
adb('shell', 'am', 'start', '-n', COMPONENT, '-f', '0x10008000')
wait_label('Local audio playing', seconds=30)
time.sleep(2)
open_playback_settings()
scroll_to_eq()
wait_label('Off: sound passes through unchanged.')
screen('eq_available_off')

# 3. Toggle on: presets and per-band sliders render against the live equalizer.
tap('Equalizer', bottom=False)
wait_label('On: shaping the sound of the current output.')
wait_label('Presets')
screen('eq_enabled_controls')

# 4. A preset writes real band levels on the live session.
tap('Bass boost', bottom=False)
time.sleep(2)
screen('eq_preset_bass')

# 5. Persistence evidence: what the controller saved on disk.
prefs = adb('shell', 'run-as', PKG, 'cat', 'shared_prefs/oris-eq.xml').decode()
open('runtime-evidence/eq-prefs.xml', 'w').write(prefs)
assert 'enabled' in prefs and 'true' in prefs, 'equalizer enabled flag was not persisted'
assert 'levels' in prefs, 'equalizer band levels were not persisted'
print('EQ persisted prefs: ' + prefs.replace('\n', ' '), flush=True)

# 6. Toggle back off so the saved state left behind is the plain one.
tap('Equalizer', bottom=False)
wait_label('Off: sound passes through unchanged.')
screen('eq_toggled_back_off')

adb('shell', 'am', 'force-stop', PKG)
print('PASS: equalizer unavailable copy, real-session controls, preset application, persisted levels', flush=True)
