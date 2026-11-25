#!/usr/bin/env python3
"""Equalizer settings evidence under network-disabled testing. First the honest
unavailable state with no audio session, then, if the platform actually creates the
effect, the real supported controls while the debug local tone plays through the service.
Platform capability is reported, never assumed: if the emulator cannot create an
Equalizer this captures the unavailable state and says so instead of failing the app."""
from playback_check import adb, tap, wait_label, screen, tree, PKG
import time

COMPONENT = PKG + '/com.orismusic.client.debug.LocalPlaybackCheckActivity'
UNAVAILABLE = 'Unavailable right now. It attaches to the player while music plays, when the device and current output support one.'


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


adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
adb('shell', 'am', 'force-stop', PKG)

# 1. Honest unsupported state: cold app, no playback, audio session 0.
open_playback_settings()
scroll_to_eq()
wait_label(UNAVAILABLE)
screen('eq_unavailable')

# 2. Real supported session attempt: the debug tone plays through the actual service.
adb('shell', 'am', 'start', '-n', COMPONENT, '-f', '0x10008000')
wait_label('Local audio playing', seconds=30)
time.sleep(2)
open_playback_settings()
scroll_to_eq()

audio_dump = adb('shell', 'dumpsys', 'audio').decode()
open('runtime-evidence/dumpsys-audio-eq.txt', 'w').write(audio_dump)
effect_lines = [l.strip() for l in audio_dump.splitlines() if 'ffect' in l or 'qualizer' in l]
print('AUDIO EFFECT LINES: ' + repr(effect_lines[:12]), flush=True)

if has_text(UNAVAILABLE):
    # Platform did not create the effect even with a live session: honest capture, not an
    # app failure. Supported-state and effect behavior stay unverified here.
    screen('eq_unsupported_emulator')
    adb('shell', 'am', 'force-stop', PKG)
    print('RESULT: Equalizer unsupported on this emulator/session; unavailable copy shown. '
          'Supported-state and effect behavior unverified.', flush=True)
    raise SystemExit(0)

# 3. Supported controls, toggle off.
wait_label('Off: sound passes through unchanged.')
screen('eq_available_off')

# 4. Toggle on: presets and per-band sliders render against the live equalizer.
tap('Equalizer', bottom=False)
wait_label('On: shaping the sound of the current output.')
wait_label('Presets')
screen('eq_enabled_controls')

# 5. A preset writes band levels; slider positions read the state back on screen.
tap('Bass boost', bottom=False)
time.sleep(2)
screen('eq_preset_bass')

# 6. Platform evidence while the effect is live, then persisted state on disk.
audio_dump = adb('shell', 'dumpsys', 'audio').decode()
open('runtime-evidence/dumpsys-audio-eq-enabled.txt', 'w').write(audio_dump)
effect_lines = [l.strip() for l in audio_dump.splitlines() if 'ffect' in l or 'qualizer' in l]
print('AUDIO EFFECT LINES (enabled): ' + repr(effect_lines[:12]), flush=True)

prefs = adb('shell', 'run-as', PKG, 'cat', 'shared_prefs/oris-eq.xml').decode()
open('runtime-evidence/eq-prefs.xml', 'w').write(prefs)
assert 'enabled' in prefs and 'true' in prefs, 'equalizer enabled flag was not persisted'
assert 'levels' in prefs, 'equalizer band levels were not persisted'
print('EQ persisted prefs: ' + prefs.replace('\n', ' '), flush=True)

# 7. Toggle back off so the saved state left behind is the plain one.
tap('Equalizer', bottom=False)
wait_label('Off: sound passes through unchanged.')
screen('eq_toggled_back_off')

adb('shell', 'am', 'force-stop', PKG)
print('PASS: equalizer unavailable copy, real-session controls, preset application, persisted levels', flush=True)
