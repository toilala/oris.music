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

from playback_check import adb, tap, wait_label, screen, tree, tap_mini_player, PKG, OUT

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


def record(name, seconds, act):
    """Record the screen while act() performs the real gesture; the recording only counts
    if the pulled file exists and carries actual frames."""
    proc = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', str(seconds),
                             '/sdcard/' + name],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    time.sleep(0.7)
    try:
        act()
    except Exception:
        proc.kill()
        proc.wait(timeout=15)
        adb('pull', '/sdcard/' + name, OUT + '/' + name)
        adb('shell', 'rm', '/sdcard/' + name)
        raise
    proc.wait(timeout=seconds + 30)
    adb('pull', '/sdcard/' + name, OUT + '/' + name)
    adb('shell', 'rm', '/sdcard/' + name)
    size = os.path.getsize(OUT + '/' + name) if os.path.exists(OUT + '/' + name) else 0
    print('RECORDED ' + name + ' bytes=' + str(size), flush=True)
    if size < 20000:
        raise AssertionError(name + ' never recorded real frames (bytes=' + str(size) + ')')


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


def tap_dock_tab(name, marker):
    """Tap the dock tab carrying name, identified structurally: its clickable ancestor
    sits in the tab row beside the other tabs (a content match elsewhere on the page is
    not the tab). Verify the destination marker; on a miss dump the tree, capture the
    screen, and retry the gesture once before failing."""
    for attempt in (1, 2):
        root = tree()
        parents = {c: p for p in root.iter('node') for c in p}
        tapped = False
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
            print('TAP dock tab ' + name + ' bounds=' + node.get('bounds')
                  + ' attempt=' + str(attempt), flush=True)
            adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
            tapped = True
            break
        if not tapped:
            print('TAB-NAV no dock tab node matched ' + name + ' (attempt '
                  + str(attempt) + ')', flush=True)
        end = time.monotonic() + 8
        while time.monotonic() < end:
            if has_label(marker):
                print('TAB-NAV ' + name + ' destination verified: ' + marker, flush=True)
                return
            time.sleep(0.7)
        open(OUT + '/tab-nav-tree-' + name + '.xml', 'w').write(
            ET.tostring(tree(), encoding='unicode'))
        screen('tab_nav_failed_' + name.lower())
    raise AssertionError('dock tab ' + name + ' never reached its destination ('
                         + marker + '); tree and screen captured per attempt')


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

    # Dock tab switch, destination verified inside the recording window.
    cold_main()
    record('transition-tab-' + tag + '.mp4', 6,
           lambda: tap_dock_tab('Search', 'Songs, albums, artists'))

    # Now Playing expand and collapse on the local tone, no network involved.
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', COMPONENT)
    wait_label('Local audio playing', seconds=30)
    time.sleep(2)
    resume_main()
    wait_label(TONE, seconds=25)
    record('transition-player-expand-' + tag + '.mp4', 8, lambda: tap_mini_player(TONE))
    wait_label('Collapse player', seconds=25)
    record('transition-player-collapse-' + tag + '.mp4', 8, lambda: tap('Collapse player'))

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
    record('transition-preset-' + tag + '.mp4', 6, lambda: tap_clickable(target))
    end = time.monotonic() + 8
    while time.monotonic() < end:
        if any((n.get('content-desc') or '') == 'Active preset: ' + target
               for n in tree().iter('node')):
            print('EQ-VERIFIED preset moved to ' + target, flush=True)
            break
        time.sleep(0.7)
    else:
        screen('failure')
        raise AssertionError('preset transition never landed: Active preset stayed '
                             + repr(current_name))


initial_scale = adb('shell', 'settings', 'get', 'global', 'animator_duration_scale').decode().strip()
print('SCALE initial=' + initial_scale, flush=True)
try:
    phase('1', 'default')
    phase('0', 'disabled')
finally:
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', initial_scale)
left = adb('shell', 'settings', 'get', 'global', 'animator_duration_scale').decode().strip()
print('SCALE restored=' + left, flush=True)
adb('shell', 'am', 'force-stop', PKG)
print('PASS: real transitions recorded at default and disabled animator scales', flush=True)
