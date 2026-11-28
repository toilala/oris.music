#!/usr/bin/env python3
"""Cover flight on the debug fixture (production Home row and stage, local data) plus the Home
light/dark loading and populated endpoints on the real app. Each leg checks its prerequisites
before any verdict and stops with a diagnosis, never a retry."""
import os
import re
import subprocess
import time

from playback_check import (adb, tree, emit_image, ensure_ffmpeg, restart_app, stop_app,
                            wait_label, pidof, PKG, OUT)

APPEARANCE = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><string name=\"theme\">%s</string></map>"
HOME_MINIMAL = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><boolean name=\"minimal\" value=\"true\" /></map>"


def prefs(name, body):
    if body is None:
        adb('shell', 'run-as', PKG, 'rm', '-f', 'shared_prefs/%s.xml' % name)
        return
    local = '/tmp/%s.xml' % name
    open(local, 'w').write(body)
    adb('push', local, '/data/local/tmp/%s.xml' % name)
    adb('shell', 'run-as', PKG, 'sh', '-c',
        "'mkdir -p shared_prefs && cp /data/local/tmp/%s.xml shared_prefs/%s.xml'" % (name, name))


def shot(name):
    png = adb('exec-out', 'screencap', '-p')
    path = '%s/%s.png' % (OUT, name)
    open(path, 'wb').write(png)
    if ensure_ffmpeg():
        jpg = '%s/%s.jpg' % (OUT, name)
        subprocess.run(['ffmpeg', '-y', '-v', 'error', '-i', path, '-vf', 'scale=540:-1', '-q:v', '4', jpg])
        if os.path.exists(jpg):
            emit_image(name, open(jpg, 'rb').read())
            return
    emit_image(name, png)


def labels():
    out = set()
    for n in tree().iter('node'):
        for k in ('text', 'content-desc'):
            if n.get(k):
                out.add(n.get(k))
    return out


def bounds(n):
    return tuple(map(int, re.findall(r'\d+', n.get('bounds'))))



def sheet(name, mp4):
    """Frames from the moment the picture starts to change. Reads the recording at 20fps,
    finds the first frame that differs strongly from the one before it, and tiles 30 frames
    from just before that point."""
    if not ensure_ffmpeg() or not os.path.exists(mp4):
        print('SHEET SKIPPED ' + name, flush=True)
        return
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', mp4, '-vf', 'fps=20,scale=48:96,format=gray',
                          '-f', 'rawvideo', '-'], capture_output=True).stdout
    size = 48 * 96
    frames = [raw[i:i + size] for i in range(0, len(raw) - size + 1, size)]
    start = None
    for i in range(1, len(frames)):
        diff = sum(abs(a - b) for a, b in zip(frames[i], frames[i - 1])) / size
        if diff > 2.0:
            start = i
            break
    print('SHEET %s frames=%d change-at=%s' % (name, len(frames), None if start is None else '%.2fs' % (start / 20)), flush=True)
    if start is None:
        return
    jpg = '%s/%s_sheet.jpg' % (OUT, name)
    subprocess.run(['ffmpeg', '-y', '-v', 'error', '-ss', '%.2f' % max(0, start / 20 - 0.1), '-i', mp4,
                    '-vf', 'fps=20,scale=200:-1,tile=10x3', '-frames:v', '1', '-q:v', '5', jpg])
    if os.path.exists(jpg):
        emit_image(name + '_sheet', open(jpg, 'rb').read())


def sheet_all(name, mp4):
    """The whole recording at 6fps, 14 frames per row, for transitions that start with the launch."""
    if not ensure_ffmpeg() or not os.path.exists(mp4):
        print('SHEET SKIPPED ' + name, flush=True)
        return
    jpg = '%s/%s_sheet.jpg' % (OUT, name)
    subprocess.run(['ffmpeg', '-y', '-v', 'error', '-i', mp4, '-vf', 'fps=6,scale=130:-1,tile=14x6',
                    '-frames:v', '1', '-q:v', '5', jpg])
    if os.path.exists(jpg):
        emit_image(name + '_sheet', open(jpg, 'rb').read())
        print('SHEET-ALL ' + name + ' at 6fps, 14 per row', flush=True)


def art_variance(b):
    """Pixel spread of the cover square of a card, 0 for a flat placeholder. Read from a screenshot."""
    if not ensure_ffmpeg():
        return None
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', b))
    w = x2 - x1
    png = adb('exec-out', 'screencap', '-p')
    open('/tmp/art.png', 'wb').write(png)
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', '/tmp/art.png', '-vf',
                          'crop=%d:%d:%d:%d,scale=16:16,format=gray' % (w, w, x1, y1), '-f', 'rawvideo', '-'],
                         capture_output=True).stdout
    if not raw:
        return None
    mean = sum(raw) / len(raw)
    return round((sum((v - mean) ** 2 for v in raw) / len(raw)) ** 0.5, 1)


def record(name, action, seconds=10, full=False):
    proc = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', str(seconds), '/sdcard/' + name],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    time.sleep(1.5)
    action()
    end = time.monotonic() + seconds - 2.5
    while time.monotonic() < end:
        time.sleep(0.5)
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
    print('RECORDED ' + name + ' bytes=' + str(os.path.getsize(OUT + '/' + name)), flush=True)
    (sheet_all if full else sheet)(name[:-4], OUT + '/' + name)



FIX = PKG + '/com.orismusic.client.debug.CoverFlightCheckActivity'


def fixture_card(title):
    """The clickable node that holds the given caption. Returns (cx, cy_of_cover, bounds_tuple) or None."""
    for n in tree().iter('node'):
        if n.get('clickable') == 'true' and any(t.get('text') == title for t in n.iter('node')):
            x1, y1, x2, y2 = bounds(n)
            return ((x1 + x2) // 2, y1 + (x2 - x1) // 2, (x1, y1, x2, y2))
    return None


def wait_text(text, seconds):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if any(text in l for l in labels()):
            return True
        time.sleep(1.5)
    return False


def wait_loaded(idx, seconds):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        for l in labels():
            m = re.search(r'covers loaded \[([\d,]*)\]', l)
            if m and str(idx) in m.group(1).split(','):
                return True
        time.sleep(1.5)
    return False


def launch_fixture(theme):
    stop_app()
    prefs('appearance', APPEARANCE % theme)
    adb('shell', 'am', 'start', '-n', FIX)


def flight_leg(theme):
    tag = theme.lower()
    launch_fixture(theme)
    if not wait_loaded(0, 45):
        print('PREREQ FAILED %s: the tapped cover (0) never reported a successful load; labels=%s' % (tag, sorted(labels())[:12]), flush=True)
        shot(tag + '_fixture_notready')
        return
    time.sleep(1)
    card = fixture_card('Fixture playlist 0')
    if card is None:
        print('PREREQ FAILED %s: card label not found' % tag, flush=True)
        shot(tag + '_fixture_nocard')
        return
    var = art_variance('[%d,%d][%d,%d]' % card[2])
    print('FLIGHT %s card=%s art-variance=%s (flat is near 0)' % (tag, card[2], var), flush=True)
    if var is None or var < 8:
        print('PREREQ FAILED %s: cover pixels are flat, art not drawn' % tag, flush=True)
        shot(tag + '_fixture_flat')
        return
    shot(tag + '_flight_before')
    record(tag + '_flight_open.mp4', lambda: adb('shell', 'input', 'tap', str(card[0]), str(card[1])))
    ls = labels()
    opened = 'Back' in ls and 'Cover flight fixture' not in ls
    print('FLIGHT %s page-open=%s' % (tag, opened), flush=True)
    shot(tag + '_flight_page')
    if not opened:
        print('PREREQ FAILED %s: page did not open, stopping this leg' % tag, flush=True)
        return
    record(tag + '_flight_close.mp4', lambda: adb('shell', 'input', 'keyevent', '4'))
    time.sleep(1.5)
    after = fixture_card('Fixture playlist 0')
    same = after is not None and all(abs(a - b) <= 4 for a, b in zip(after[2], card[2]))
    var_after = art_variance('[%d,%d][%d,%d]' % after[2]) if after else None
    print('FLIGHT %s home-visible=%s source-bounds-before=%s after=%s same=%s art-variance-after=%s' % (
        tag, 'Cover flight fixture' in labels(), card[2], after[2] if after else None, same, var_after), flush=True)
    shot(tag + '_flight_after')


def interrupted_leg():
    """Back is pressed on the device the moment the screen first changes after the tap, so it lands
    inside the opening motion. The script prints how long after the tap that was; the sheet is the
    evidence that the picture was mid-flight."""
    launch_fixture('Light')
    if not wait_loaded(1, 45):
        print('PREREQ FAILED interrupted: the tapped cover (1) never reported a successful load', flush=True)
        return
    time.sleep(1)
    card = fixture_card('Fixture playlist 1')
    if card is None:
        print('PREREQ FAILED interrupted: card not found', flush=True)
        return
    script = ("screencap /sdcard/ia.raw; s=$(cut -d' ' -f1 /proc/uptime); input tap %d %d; "
              "n=0; while [ $n -lt 40 ]; do screencap /sdcard/ib.raw; "
              "if ! cmp -s /sdcard/ia.raw /sdcard/ib.raw; then t=$(cut -d' ' -f1 /proc/uptime); input keyevent 4; "
              "echo START=$s CHANGED=$t FRAMES_POLLED=$n; break; fi; n=$((n+1)); done; "
              "rm -f /sdcard/ia.raw /sdcard/ib.raw") % (card[0], card[1])
    outputs = []

    def act():
        outputs.append(adb('shell', script).decode(errors='replace'))

    record('interrupted.mp4', act, seconds=10, full=True)
    text = ' '.join(outputs).strip()
    m = re.search(r'START=([\d.]+) CHANGED=([\d.]+) FRAMES_POLLED=(\d+)', text)
    if m:
        print('INTERRUPTED back pressed %.2fs after the tap (first changed frame, %s polls)' % (float(m.group(2)) - float(m.group(1)), m.group(3)), flush=True)
    else:
        print('INTERRUPTED trigger never saw a changed frame: ' + text[:200], flush=True)
    time.sleep(1)
    ls = labels()
    print('INTERRUPTED home-visible=%s page-visible=%s' % ('Cover flight fixture' in ls, 'Back' in ls and 'Cover flight fixture' not in ls), flush=True)
    shot('interrupted_after')


def leg_states(theme):
    stop_app()
    prefs('appearance', APPEARANCE % theme)
    prefs('home', None)
    adb('shell', 'am', 'start', '-n', PKG + '/com.orismusic.client.MainActivity')
    t0 = time.monotonic()
    for i in range(6):
        time.sleep(0.6)
        if i in (1, 3):
            shot('%s_load_%d' % (theme, i))
    wait_label('Quick picks', seconds=60, pkg=PKG)
    time.sleep(2)
    print('STATES %s populated after %.1fs' % (theme, time.monotonic() - t0), flush=True)
    shot('%s_populated' % theme)


def main():
    adb('wait-for-device')
    original = adb('shell', 'settings', 'get', 'global', 'animator_duration_scale').decode().strip()
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '1')
    adb('shell', 'cmd', 'uimode', 'night', 'no')
    steps = [
        ('light flight', lambda: flight_leg('Light')),
        ('dark flight', lambda: flight_leg('Dark')),
        ('interrupted open', interrupted_leg),
        ('light states', lambda: leg_states('Light')),
        ('dark states', lambda: leg_states('Dark')),
    ]
    for name, step in steps:
        try:
            step()
        except Exception as e:
            print('LEG failed: ' + name + ' ' + repr(e), flush=True)
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', original if original not in ('null', '') else '1')


main()
