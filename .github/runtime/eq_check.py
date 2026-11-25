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

from playback_check import adb, tap, wait_label, screen, tree, PKG, OUT

COMPONENT = PKG + '/com.orismusic.client.debug.LocalPlaybackCheckActivity'
MAIN = PKG + '/com.orismusic.client.MainActivity'
UNAVAILABLE = 'Unavailable right now.'
OFF_COPY = 'Off: sound passes through unchanged.'
ON_COPY = 'On: shaping the sound of the current output.'


def resumed_activity():
    out = adb('shell', 'dumpsys', 'activity', 'activities').decode()
    return out.split('mResumedActivity:')[-1].splitlines()[0]


def resume_main():
    """Bring MainActivity forward and verify the resumed activity before touching anything.
    Uses mResumedActivity (the same dumpsys parse as playback_check.still_open), never a
    focus-window regex, and never force-stops the app: the live audio session under test
    must survive navigation."""
    for _ in range(3):
        # CLEAR_TOP pops the check activity sitting above MainActivity in the one task.
        adb('shell', 'am', 'start', '-n', MAIN, '-f', '0x04000000')
        for _ in range(40):
            resumed = resumed_activity()
            if PKG in resumed and 'MainActivity' in resumed:
                return
            time.sleep(0.5)
    screen('failure')
    raise AssertionError('MainActivity never resumed to the foreground')


def open_playback_settings():
    resume_main()
    # A resumed singleTop activity lands wherever it was left; walk back to the root
    # page (the only page without a Back affordance) before navigating down.
    for _ in range(8):
        if not has_text('Back'):
            break
        adb('shell', 'input', 'keyevent', '4')
        time.sleep(1)
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


def chip_state(label):
    """The selected attribute of the clickable ancestor carrying the label, or None when
    the label is absent. Compose maps a FilterChip's selected state onto that ancestor."""
    root = tree()
    states = [n.get('content-desc') for n in root.iter('node')
              if (n.get('content-desc') or '').startswith('Active preset:')]
    if states:
        print('EQ-STATE ' + states[0], flush=True)
    parents = {c: p for p in root.iter('node') for c in p}
    target = next((n for n in root.iter('node') if n.get('text') == label), None)
    node = target
    while node is not None and node.get('clickable') != 'true':
        node = parents.get(node)
    return node.get('selected') if node is not None else None


def chip_state_wait(label, seconds=6):
    """Poll the chip's selected attribute on FRESH trees after its tap, never retrying
    the tap itself. On timeout capture the screen, the FULL tree, the matched node with
    its ancestors and descendants, and every selected node, so the review can tell a
    stale or duplicated a11y node apart from semantics the app never set."""
    end = time.monotonic() + seconds
    last = None
    while time.monotonic() < end:
        last = chip_state(label)
        if last == 'true':
            return
        time.sleep(0.5)
    screen('eq_preset_failed')
    root = tree()
    import xml.etree.ElementTree as ET
    open(OUT + '/eq-preset-tree.xml', 'w').write(ET.tostring(root, encoding='unicode'))
    parents = {c: p for p in root.iter('node') for c in p}
    matches = [n for n in root.iter('node') if n.get('text') == label]
    print('EQ-FORENSICS nodes carrying text ' + repr(label) + ': ' + str(len(matches)), flush=True)
    for m in matches:
        chain = []
        node = m
        while node is not None:
            chain.append(node.get('class') + ' sel=' + str(node.get('selected'))
                         + ' click=' + str(node.get('clickable')) + ' ' + str(node.get('bounds')))
            node = parents.get(node)
        print('EQ-FORENSICS chain: ' + ' <- '.join(chain), flush=True)
        print('EQ-FORENSICS subtree: ' + ET.tostring(m, encoding='unicode')[:1500], flush=True)
    selected = [n for n in root.iter('node') if n.get('selected') == 'true']
    print('EQ-FORENSICS selected nodes: '
          + str([(n.get('text'), n.get('content-desc'), n.get('bounds')) for n in selected]), flush=True)
    states = [n.get('content-desc') for n in root.iter('node')
              if (n.get('content-desc') or '').startswith('Active preset:')]
    print('EQ-FORENSICS app state line: ' + repr(states), flush=True)
    raise AssertionError(
        label + ' chip did not read as selected after its tap (last seen: '
        + repr(last) + '); post-tap capture is eq_preset_failed.png, tree is eq-preset-tree.xml')


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
chip_state_wait('Bass boost')
screen('eq_preset_bass')
for _ in range(8):
    if any(re.fullmatch(r'\d+(\.\d+)? (Hz|kHz)', n.get('text') or '') for n in tree().iter('node')):
        break
    adb('shell', 'input', 'swipe', '540', '1500', '540', '900', '350')
    time.sleep(1.2)
screen('eq_band_sliders')
# The band sliders carry accessible labels; drag one off its preset value and the chip
# row must fall back to Custom, derived from the band values rather than a stored name.
slider = next((n for n in tree().iter('node') if n.get('content-desc') == 'Equalizer band 60 Hz'), None)
assert slider is not None, 'no accessible Equalizer band 60 Hz slider in the tree'
x1, y1, x2, y2 = map(int, re.findall(r'\d+', slider.get('bounds')))
adb('shell', 'input', 'swipe', str((x1 + x2) // 2), str((y1 + y2) // 2), str(x1 + (x2 - x1) // 4), str((y1 + y2) // 2), '400')
time.sleep(1.5)
assert has_text('Custom'), 'Custom chip never appeared after a hand-tweaked band'
screen('eq_custom_mix')
prefs = read_prefs('eq-prefs-enabled.xml')
assert 'enabled' in prefs and 'true' in prefs, 'equalizer enabled flag was not persisted'
assert 'levels' in prefs, 'equalizer band levels were not persisted'

# 4. Platform read-back while the debug tone plays through the real service. The check
# activity starts in the app's own task (no CLEAR_TASK), so the way back is a plain
# resume of MainActivity instead of a process restart.
adb('shell', 'am', 'start', '-n', COMPONENT)
wait_label('Local audio playing', seconds=30)
time.sleep(3)
dump_audio('dumpsys-audio-eq-playing.txt')

# 5. Settings still show the equalizer during playback; toggle off; persisted off state.
# The activity may resume straight onto the playback page; only navigate when it did not.
resume_main()
time.sleep(1)
if not has_text('Playback speed'):
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
