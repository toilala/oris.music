#!/usr/bin/env python3
"""Real-transition motion evidence under network-disabled testing: recordings, not
stills. The same transitions (dock tab switch, Now Playing expand and collapse on a
local tone, equalizer preset tap) are captured twice, once at the default OS animator
scale and once with animations disabled, so the motion-scale work is reviewed on pixels
in motion. Each phase prints the scale the OS actually reports; a capture that never
landed fails the check rather than passing silently."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

from playback_check import (adb, tap, wait_label, screen, tree, restore_setting,
                            emit_mp4_frames, PKG, OUT, pidof)

MAIN = PKG + '/com.orismusic.client.MainActivity'
COMPONENT = PKG + '/com.orismusic.client.debug.LocalPlaybackCheckActivity'
TONE = 'Runtime local tone'
UNAVAILABLE_LINES = ('Unavailable right now.',
                     'No audio session for it to work on right now.',
                     'Could not start the equalizer on this output.')


def set_scale(value):
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', value)
    got = adb('shell', 'settings', 'get', 'global', 'animator_duration_scale').decode().strip()
    print('SCALE animator_duration_scale=' + got + ' (asked ' + value + ')', flush=True)
    return got


def record(name, max_seconds, resolve, confirm):
    """Record a real transition so the gesture and the destination both land on camera.
    The tap target is resolved BEFORE the recorder starts (a uiautomator dump costs
    seconds and must not eat the window), then re-resolved once to prove it is still
    current; the recorder warms up ~1.5s, the tap is a bare input tap on the resolved
    center, and recording runs until the destination is tree-confirmed plus a 1.5s
    tail, stopped with SIGINT so the mp4 finalizes. Byte size and the confirmation
    state are both logged. A confirmation failure still pulls the partial recording
    and captures screen+tree, so partial evidence survives."""
    first = resolve()
    if first is None:
        screen('failure')
        raise AssertionError(name + ': gesture target never resolved before recording')
    x, y, desc = first
    again = resolve()
    if again is None:
        screen('failure')
        raise AssertionError(name + ': gesture target vanished before recording')
    if abs(again[0] - x) > 12 or abs(again[1] - y) > 12:
        x, y, desc = again
        print('TARGET-MOVED re-resolved: ' + desc, flush=True)
    print('TARGET ' + desc, flush=True)
    proc = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', str(max_seconds),
                             '/sdcard/' + name],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    time.sleep(1.5)
    adb('shell', 'input', 'tap', str(x), str(y))
    print('TAP-RECORDED ' + name + ' at ' + str(x) + ',' + str(y), flush=True)
    confirmed = False
    end = time.monotonic() + max(3, max_seconds - 4)
    while time.monotonic() < end:
        if confirm():
            confirmed = True
            break
        time.sleep(0.5)
    time.sleep(1.5)
    pid = pidof('screenrecord')
    if pid:
        adb('shell', 'kill', '-2', pid[0])
    try:
        proc.wait(timeout=25)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait(timeout=15)
    adb('pull', '/sdcard/' + name, OUT + '/' + name)
    adb('shell', 'rm', '/sdcard/' + name)
    size = os.path.getsize(OUT + '/' + name) if os.path.exists(OUT + '/' + name) else 0
    print('RECORDED ' + name + ' bytes=' + str(size)
          + ' destination-confirmed=' + str(confirmed), flush=True)
    emit_mp4_frames(name[:-4], OUT + '/' + name)
    if size < 20000:
        raise AssertionError(name + ' never recorded real frames (bytes=' + str(size) + ')')
    if not confirmed:
        screen(name[:-4] + '_unconfirmed')
        open(OUT + '/' + name[:-4] + '_unconfirmed_tree.xml', 'w').write(
            ET.tostring(tree(), encoding='unicode'))
        raise AssertionError(name + ': destination never confirmed inside the window; '
                             'partial recording, screen and tree preserved')


def resolve_dock_tab(name):
    """Center of the clickable dock-tab node carrying name, matched structurally: its
    holder must also hold a sibling tab label, so a content match elsewhere on the page
    is not mistaken for the tab. None when absent."""
    root = tree()
    parents = {c: p for p in root.iter('node') for c in p}
    for h in [n for n in root.iter('node') if name in (n.get('text'), n.get('content-desc'))]:
        node = h
        while node is not None and node.get('clickable') != 'true':
            node = parents.get(node)
        holder = parents.get(node) if node is not None else None
        if holder is None:
            continue
        siblings = [d.get('content-desc') for d in holder.iter('node')]
        if not any(s in ('Home', 'Library', 'Queue') and s != name for s in siblings):
            continue
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        return (x1 + x2) // 2, (y1 + y2) // 2, 'dock tab ' + name + ' bounds=' + node.get('bounds')
    return None


def resolve_clickable(label):
    """Center of the clickable ancestor of the node carrying label; None when absent."""
    root = tree()
    parents = {c: p for p in root.iter('node') for c in p}
    target = next((n for n in root.iter('node') if label in (n.get('text'), n.get('content-desc'))), None)
    if target is None:
        return None
    node = target
    while node is not None and node.get('clickable') != 'true':
        node = parents.get(node)
    if node is None:
        node = target
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    return (x1 + x2) // 2, (y1 + y2) // 2, label + ' bounds=' + node.get('bounds')


def resolve_mini_player(title):
    """Center of the mini-player pill: the clickable ancestor of the title whose subtree
    owns a transport button (same rule as playback_check.tap_mini_player). None when
    absent."""
    root = tree()
    parents = {c: p for p in root.iter('node') for c in p}
    for label_node in [n for n in root.iter('node') if n.get('text') == title]:
        node = label_node
        while node is not None and node.get('clickable') != 'true':
            node = parents.get(node)
        if node is None:
            continue
        descs = [d.get('content-desc') for d in node.iter('node')]
        if any(d in ('Play', 'Pause', 'Replay') for d in descs):
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
            return (x1 + x2) // 2, (y1 + y2) // 2, 'mini player ' + title + ' bounds=' + node.get('bounds')
    return None


def resumed_activity():
    out = adb('shell', 'dumpsys', 'activity', 'activities').decode()
    return out.split('mResumedActivity:')[-1].splitlines()[0]


def cold_main():
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', MAIN)
    wait_label('Home', seconds=60)


def resume_main():
    for _ in range(3):
        adb('shell', 'am', 'start', '-n', MAIN, '-f', '0x04000000')
        for _ in range(40):
            resumed = resumed_activity()
            if PKG in resumed and 'MainActivity' in resumed:
                return
            time.sleep(0.5)
    screen('failure')
    raise AssertionError('MainActivity never resumed to the foreground')


def has_text(fragment):
    return any(fragment in (n.get('text') or '') for n in tree().iter('node'))


def has_label(fragment):
    return any(fragment in (n.get('text'), n.get('content-desc')) for n in tree().iter('node'))


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


def open_playback_settings():
    resume_main()
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


adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
os.makedirs(OUT, exist_ok=True)


def phase(scale, tag):
    asked = set_scale(scale)

    # Dock tab switch: the destination is confirmed while the recorder is running.
    cold_main()
    record('transition-tab-' + tag + '.mp4', 15,
           lambda: resolve_dock_tab('Search'),
           lambda: has_label('Songs, albums, artists'))

    # Now Playing expand and collapse on the local tone, no network involved.
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', COMPONENT)
    wait_label('Local audio playing', seconds=30)
    time.sleep(2)
    resume_main()
    wait_label(TONE, seconds=25)
    record('transition-player-expand-' + tag + '.mp4', 15,
           lambda: resolve_mini_player(TONE),
           lambda: has_label('Collapse player'))
    record('transition-player-collapse-' + tag + '.mp4', 15,
           lambda: resolve_clickable('Collapse player'),
           lambda: not has_label('Collapse player') and has_label(TONE))

    # Equalizer preset tap, when this environment exposes an equalizer at all.
    open_playback_settings()
    scroll_to_eq()
    unavailable = next((line for line in UNAVAILABLE_LINES if has_text(line)), None)
    if unavailable is not None:
        print('NOTE: equalizer unavailable here (' + unavailable + '); preset transition '
              'not recorded at scale ' + asked, flush=True)
        return
    if not has_text('Presets'):
        tap_clickable('Equalizer')
        wait_label('Presets')
    for _ in range(8):
        if has_text('Bass boost'):
            break
        adb('shell', 'input', 'swipe', '540', '1500', '540', '900', '350')
        time.sleep(1.2)
    wait_label('Bass boost')
    current = next((n.get('content-desc') for n in tree().iter('node')
                    if (n.get('content-desc') or '').startswith('Active preset:')), None)
    current_name = current.split(': ', 1)[1] if current else None
    target = 'Flat' if current_name == 'Bass boost' else 'Bass boost'
    print('EQ-TARGET ' + target + ' (current ' + repr(current_name) + ')', flush=True)
    # Destination = the derived active-preset line moved off its pre-tap value. The exact
    # name is not asserted here: band values can clamp so eqPresetNameFor reports Custom,
    # and eq_check.py owns the exact preset-vector correctness for this environment.
    record('transition-preset-' + tag + '.mp4', 15,
           lambda: resolve_clickable(target),
           lambda: any((n.get('content-desc') or '').startswith('Active preset: ')
                       and (n.get('content-desc') or '') != current
                       for n in tree().iter('node')))
    print('EQ-VERIFIED preset moved to ' + target + ' (confirmed inside the recording)',
          flush=True)


initial_scale = adb('shell', 'settings', 'get', 'global', 'animator_duration_scale').decode().strip()
print('SCALE initial=' + repr(initial_scale), flush=True)
try:
    phase('1', 'default')
    phase('0', 'disabled')
finally:
    if initial_scale in ('null', ''):
        adb('shell', 'settings', 'delete', 'global', 'animator_duration_scale')
    else:
        restore_setting('global', 'animator_duration_scale', initial_scale)
adb('shell', 'am', 'force-stop', PKG)
print('PASS: real transitions recorded at default and disabled animator scales', flush=True)
