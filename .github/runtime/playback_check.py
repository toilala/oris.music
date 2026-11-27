#!/usr/bin/env python3
"""Deterministic navigation smoke check. Never asks YouTube to play a song."""
import base64
import glob
import json
import os
import re
import subprocess
import time
import traceback
import xml.etree.ElementTree as ET

OUT = 'runtime-evidence'
PKG = 'toilala.oris.debug'
os.makedirs(OUT, exist_ok=True)

def adb(*args):
    return subprocess.check_output(['adb', *args])

def emit_image(name, data):
    encoded = base64.b64encode(data).decode()
    for start in range(0, len(encoded), 3000):
        print(f'ORIS_IMAGE {name} {encoded[start:start+3000]}', flush=True)

def screen(name):
    data = adb('exec-out', 'screencap', '-p')
    open(f'{OUT}/{name}.png', 'wb').write(data)
    emit_image(name, data)

def ensure_ffmpeg():
    """The runner image has no ffmpeg; install it once per job so recorded transitions
    can be read as frames. Best-effort: frame emission stays warn-only if apt fails."""
    import shutil
    if shutil.which('ffprobe') and shutil.which('ffmpeg'):
        return True
    r = subprocess.run(['sudo', 'apt-get', 'install', '-y', 'ffmpeg'], capture_output=True)
    if not (shutil.which('ffprobe') and shutil.which('ffmpeg')):
        subprocess.run(['sudo', 'apt-get', 'update', '-qq'], capture_output=True)
        r = subprocess.run(['sudo', 'apt-get', 'install', '-y', 'ffmpeg'], capture_output=True)
    ok = bool(shutil.which('ffprobe') and shutil.which('ffmpeg'))
    print('FFMPEG ' + ('ready' if ok else 'unavailable (apt rc=' + str(r.returncode) + ')'), flush=True)
    return ok

def emit_mp4_frames(tag, mp4_path, frames=10, span=6.0):
    # The gesture sits at the start of every leg's recording, so sample the first span
    # seconds densely instead of spreading few frames across a mostly idle tail.
    """Key frames of a recorded transition as ORIS_IMAGE chunks, so the motion itself
    is inspectable from the log rather than only start/end stills or the artifact zip.
    ffmpeg/ffprobe are NOT on this runner, so frame emission is best-effort only and
    must never take a check down with it."""
    if not ensure_ffmpeg():
        print('FRAMES SKIPPED ' + tag + ' (no ffmpeg on runner)', flush=True)
        return
    if not os.path.exists(mp4_path):
        print('FRAMES MISSING ' + tag + ' (' + mp4_path + ')', flush=True)
        return
    probe = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration',
                            '-of', 'csv=p=0', mp4_path], capture_output=True)
    try:
        total = float(probe.stdout.decode().strip())
    except ValueError:
        total = 0.0
    if total <= 0:
        print('FRAMES UNREADABLE ' + tag, flush=True)
        return
    window = min(total, span)
    for i in range(frames):
        ts = window * (i + 0.5) / frames
        png = f'{OUT}/{tag}_f{i}.png'
        r = subprocess.run(['ffmpeg', '-y', '-v', 'error', '-ss', f'{ts:.2f}', '-i', mp4_path,
                            '-frames:v', '1', '-vf', 'scale=540:-1', png], capture_output=True)
        if r.returncode != 0 or not os.path.exists(png):
            print('FRAME FAIL ' + tag + ' f' + str(i), flush=True)
            continue
        emit_image(f'{tag}_f{i}', open(png, 'rb').read())

def tree():
    for _ in range(2):
        try:
            adb('shell', 'uiautomator', 'dump', '/sdcard/oris-nav.xml')
            return ET.fromstring(adb('shell', 'cat', '/sdcard/oris-nav.xml'))
        except (subprocess.CalledProcessError, ET.ParseError):
            time.sleep(1)
    return ET.Element('hierarchy')

def wait_label(label, seconds=25, pkg=None):
    # uiautomator dumps every window, including the system navigation bar whose
    # buttons carry content-desc "Back"/"Home": pkg scopes matching to one package
    # so a caller can never wait on another window's node.
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        root = tree()
        for node in root.iter('node'):
            if pkg is not None and (node.get('package') or '') != pkg:
                continue
            if label in (node.get('text'), node.get('content-desc')):
                return node
        time.sleep(1)
    screen('failure')
    try:
        open(f'{OUT}/failure-logcat.txt', 'wb').write(adb('logcat', '-d'))
        adb('shell', 'uiautomator', 'dump', '/sdcard/oris-fail.xml')
        open(f'{OUT}/failure-ui.xml', 'wb').write(adb('shell', 'cat', '/sdcard/oris-fail.xml'))
    except subprocess.CalledProcessError:
        pass
    print(adb('logcat', '-d', '-s', 'AndroidRuntime').decode(), flush=True)
    raise AssertionError(f'UI never showed {label}')

def tap(label, bottom=True, pkg=None):
    wait_label(label, pkg=pkg)
    nodes = []
    for _ in range(6):
        nodes = [n for n in tree().iter('node')
                 if (pkg is None or (n.get('package') or '') == pkg)
                 and label in (n.get('text'), n.get('content-desc'))]
        if nodes:
            break
        time.sleep(1)
    node = nodes[-1] if bottom else nodes[0]
    points = list(map(int, re.findall(r'\d+', node.get('bounds'))))
    adb('shell', 'input', 'tap', str((points[0]+points[2])//2), str((points[1]+points[3])//2))
    time.sleep(1)


def pidof(name):
    """Pids of a running process; [] when nothing matches. pidof exits 1 exactly when no
    process matches - the expected dead state - so only that rc is quiet. Timeouts,
    device errors and every other rc keep raising with stderr preserved."""
    r = subprocess.run(['adb', 'shell', 'pidof', name], capture_output=True, timeout=30)
    if r.returncode == 1 and not r.stdout.strip():
        return []
    if r.returncode != 0:
        raise AssertionError('pidof ' + name + ' rc=' + str(r.returncode) + ': '
                             + r.stderr.decode(errors='replace').strip())
    return r.stdout.decode().split()


def ime_shown():
    """True/False from dumpsys input_method's mInputShown (the API-29 emulator signal);
    None when the line is absent, so callers fail loudly instead of assuming."""
    dump = adb('shell', 'dumpsys', 'input_method').decode(errors='replace')
    m = re.search(r'mInputShown=(\w+)', dump)
    return None if m is None else m.group(1) == 'true'


def dismiss_ime(seconds=10):
    """Close the soft keyboard before navigating to something it covers, and confirm it
    actually disappeared. BACK is sent only when the keyboard is genuinely shown - with
    no IME up, BACK would navigate away instead."""
    shown = ime_shown()
    if shown is None:
        raise AssertionError('IME state unreadable: no mInputShown in dumpsys input_method')
    if shown:
        adb('shell', 'input', 'keyevent', '4')
        end = time.monotonic() + seconds
        while time.monotonic() < end:
            time.sleep(0.7)
            now = ime_shown()
            if now is False:
                print('IME dismissed and confirmed hidden', flush=True)
                return
            if now is None:
                raise AssertionError('IME state unreadable after BACK')
        screen('failure')
        raise AssertionError('IME still shown after BACK')
    print('IME already hidden, no BACK sent', flush=True)


def tap_mini_any(titles, seconds=30):
    """Tap the mini-player pill showing any one of titles: the clickable ancestor of the
    title text whose subtree owns a transport button (content-desc Play/Pause/Replay).
    Used when the now-playing title is not predetermined (shuffled start)."""
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        root = tree()
        parents = {c: p for p in root.iter('node') for c in p}
        for label_node in [n for n in root.iter('node') if n.get('text') in titles]:
            node = label_node
            while node is not None and node.get('clickable') != 'true':
                node = parents.get(node)
            if node is None:
                continue
            descs = [d.get('content-desc') for d in node.iter('node')]
            if any(d in ('Play', 'Pause', 'Replay') for d in descs):
                x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
                print('TAP mini player ' + label_node.get('text')
                      + ' bounds=' + node.get('bounds'), flush=True)
                adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
                return
        time.sleep(1)
    screen('failure')
    raise AssertionError('mini player pill for any of ' + repr(titles) + ' never found')


def tap_mini_player(title):
    """Tap the mini-player pill: the clickable ancestor of the title text whose subtree
    owns a transport button (content-desc Play/Pause/Replay). Home's 'Continue listening'
    row is not clickable, so it can never shadow the pill."""
    tap_mini_any([title])


def get_setting(ns, key):
    return adb('shell', 'settings', 'get', ns, key).decode().strip()


def restore_setting(ns, key, initial):
    """Restore a shared OS setting to its captured initial value. A captured 'null'
    means the key was unset: delete it rather than writing the literal string 'null'
    back (settings get prints null for an absent key). The final value is always read
    back and printed so the log shows what the OS actually holds."""
    if initial in ('', 'null'):
        adb('shell', 'settings', 'delete', ns, key)
    else:
        adb('shell', 'settings', 'put', ns, key, initial)
    final = get_setting(ns, key)
    print('SETTING ' + ns + '/' + key + ' initial=' + repr(initial)
          + ' final=' + repr(final), flush=True)
    return final


def back():
    adb('shell', 'input', 'keyevent', '4')
    time.sleep(1)

def still_open():
    assert PKG in adb('shell', 'dumpsys', 'activity', 'activities').decode().split('mResumedActivity:')[-1].split('\n')[0]

def main():
    adb('wait-for-device')
    adb('shell', 'input', 'keyevent', '82')
    adb('install', '-r', glob.glob('app/build/outputs/apk/foss/debug/*.apk')[0])
    adb('shell', 'am', 'start', '-n', PKG+'/com.orismusic.client.MainActivity')
    wait_label('Home', seconds=60)
    tap('Library')
    tap('Queue')
    wait_label('Nothing queued yet. Pick a song on Home.')
    screen('queue')
    wait_label('Open player')
    # the content-desc sits on the icon child; enabled lives on the button that owns it.
    # a disabled control may drop its click semantics, so the owner is the nearest node
    # that is clickable or reports enabled=false, and the chain goes to the log as evidence.
    root = tree()
    parents = {c: p for p in root.iter('node') for c in p}
    node = next(n for n in root.iter('node') if 'Open player' in (n.get('text'), n.get('content-desc')))
    chain, n = [], node
    while n is not None and len(chain) < 6:
        chain.append({k: n.get(k) for k in ('clickable', 'enabled', 'class')})
        n = parents.get(n)
    print('OPEN_PLAYER_CHAIN ' + json.dumps(chain), flush=True)
    owner = node
    while owner is not None and owner.get('clickable') != 'true' and owner.get('enabled') != 'false':
        owner = parents.get(owner)
    assert owner is not None and owner.get('enabled') == 'false', 'Open player must be disabled while the queue is empty'
    back()
    wait_label('Liked (0)')
    still_open()
    back()
    wait_label('Home')
    still_open()
    screen('back_to_home')
    tap('Search')
    tap('Library')
    back()
    wait_label('Songs, albums, artists')
    still_open()
    back()
    wait_label('Home')
    still_open()
    tap('Search')
    wait_label('Songs, albums, artists')
    screen('search_empty')
    edit = next(n for n in tree().iter('node') if n.get('class') == 'android.widget.EditText')
    xy = list(map(int, re.findall(r'\d+', edit.get('bounds'))))
    adb('shell', 'input', 'tap', str((xy[0]+xy[2])//2), str((xy[1]+xy[3])//2))
    adb('shell', 'input', 'text', 'Yesterday')
    time.sleep(2)
    # Hide IME, then press the app's explicit Search button (not generic keyboard Enter).
    back()
    # beta5: no Search button any more; typing auto-submits after a short pause.
    time.sleep(3)
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
    wait_label('Home')
    still_open()
    back()
    time.sleep(1)
    focus = adb('shell', 'dumpsys', 'window').decode()
    assert not any(PKG in line for line in focus.splitlines() if 'mCurrentFocus' in line)
    print('PASS: idle player, nested Back, tab history, Home system exit; no playback network request', flush=True)

if __name__ == '__main__':
    # A failed assertion must not block the remaining checks and their screenshots.
    failure = None
    try:
        main()
    except BaseException:
        failure = traceback.format_exc()
        print(failure, flush=True)
    failures = []
    # offline_check stays last when present: it cuts the radios and keeps them cut as evidence.
    # battery.txt overrides the default set so a focused evidence run can skip the rest.
    default_battery = ['recents_check.py', 'lyrics_check.py', 'ui_shots.py', 'playlist_journey_check.py', 'notif_check.py', 'home_picks_check.py', 'eq_check.py', 'large_text_check.py', 'morph_gesture_check.py', 'morph_cancel_check.py', 'motion_record.py', 'light_dock_check.py', 'offline_check.py']
    battery_file = '.github/runtime/battery.txt'
    if os.path.exists(battery_file):
        battery = [line.strip() for line in open(battery_file) if line.strip()]
        print('BATTERY from battery.txt: ' + ', '.join(battery), flush=True)
    else:
        battery = default_battery
    for check in battery:
        result = subprocess.run(['python3', '.github/runtime/'+check])
        if result.returncode: failures.append(check)
    if failure is not None: raise SystemExit(1)
    if failures: raise AssertionError('Runtime checks failed: '+', '.join(failures))
