#!/usr/bin/env python3
"""Home top section and card-to-page transition: loading, populated, offline and legacy
minimal states, light and dark through the app's own theme preference, open/close/reversal
and an animations-off pass. Recordings are read by content: the transition start is found
from the frames, not from a fixed offset. Each leg prints what it confirmed."""
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


def find_card():
    """First clickable, portrait-ish card below the Quick picks header (cover with a caption
    under it). Returns (cx, cy, bounds, caption)."""
    root = tree()
    qp = next((n for n in root.iter('node') if 'Quick picks' in (n.get('text') or '')), None)
    top = bounds(qp)[3] if qp is not None else 0
    best = None
    for n in root.iter('node'):
        if n.get('clickable') != 'true':
            continue
        x1, y1, x2, y2 = bounds(n)
        w, h = x2 - x1, y2 - y1
        if y1 > top + 200 and 250 <= w <= 700 and h > w and x1 >= 0 and x2 <= 1090:
            if best is None or y1 < best[1]:
                caption = ' | '.join(sorted(t.get('text') for t in n.iter('node') if t.get('text')))
                best = ((x1 + x2) // 2, y1 + w // 2, n.get('bounds'), caption)
    return best


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


def on_page():
    ls = labels()
    return 'Back' in ls and 'Quick picks' not in ls


def start(theme, home=None):
    stop_app()
    prefs('appearance', APPEARANCE % theme)
    prefs('home', home)
    adb('shell', 'am', 'start', '-n', PKG + '/com.orismusic.client.MainActivity')


def header_top():
    for n in tree().iter('node'):
        if (n.get('text') or '').isupper() and ',' in (n.get('text') or '') and n.get('package') == PKG:
            return bounds(n)
    return None


def leg_states(theme):
    """Cold start: screenshots while loading, then populated, with where the date header sits."""
    start(theme)
    t0 = time.monotonic()
    seen = []
    for i in range(10):
        time.sleep(0.6)
        seen.append(round(time.monotonic() - t0, 1))
        if i in (1, 3):
            shot('%s_load_%d' % (theme, i))
    wait_label('Quick picks', seconds=60, pkg=PKG)
    time.sleep(2)
    print('LEG %s populated header-bounds=%s' % (theme, header_top()), flush=True)
    shot('%s_populated' % theme)


def fixture_cover(name, color):
    ensure_ffmpeg()
    jpg = '/tmp/%s.jpg' % name
    subprocess.run(['ffmpeg', '-y', '-v', 'error', '-f', 'lavfi', '-i', 'color=c=%s:s=600x600' % color,
                    '-vf', 'drawgrid=w=100:h=100:t=6:c=white@0.6', '-frames:v', '1', jpg])
    r = subprocess.run(['adb', 'shell', 'run-as', PKG, 'tee', 'cache/%s.jpg' % name],
                       input=open(jpg, 'rb').read(), capture_output=True)
    assert r.returncode == 0, 'cover fixture write failed'
    return 'file:///data/user/0/%s/cache/%s.jpg' % (PKG, name)


def seed_history(rows):
    import json
    from xml.sax.saxutils import escape
    xml = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map><string name=\"history\">"
           + escape(json.dumps(rows)) + '</string></map>')
    r = subprocess.run(['adb', 'shell', 'run-as', PKG, 'tee', 'shared_prefs/oris-library.xml'],
                       input=xml.encode(), capture_output=True)
    assert r.returncode == 0, 'history fixture write failed'


def offline_leg(tag, history):
    """Cold start with no network. No history: the loading placeholder must give way to a header
    and the failure message. With seeded history (local covers, so loaded art): the placeholder
    must give way to a banner built from that history."""
    adb('shell', 'cmd', 'connectivity', 'airplane-mode', 'enable')
    time.sleep(3)
    try:
        stop_app()
        adb('shell', 'run-as', PKG, 'rm', '-f', 'shared_prefs/oris-library.xml')
        if history:
            colors = ['0xC0392B', '0x2E86C1', '0x28B463', '0xD4AC0D', '0x8E44AD']
            rows = [{'id': 'fx%d' % i, 'title': 'Fixture song %d' % i, 'artist': 'Fixture artist',
                     'thumbnail': fixture_cover('fxcover%d' % i, c)} for i, c in enumerate(colors)]
            seed_history(rows)
        prefs('appearance', APPEARANCE % 'Light')
        prefs('home', None)
        record(tag + '.mp4', lambda: adb('shell', 'am', 'start', '-n', PKG + '/com.orismusic.client.MainActivity'),
               seconds=14, full=True)
        ls = labels()
        print('LEG %s settled labels-have-retry=%s header-bounds=%s' % (
            tag, any('Retry' in l or 'Try again' in l or 'could not' in l.lower() for l in ls), header_top()), flush=True)
        shot(tag + '_settled')
    finally:
        adb('shell', 'run-as', PKG, 'rm', '-f', 'shared_prefs/oris-library.xml')
        adb('shell', 'cmd', 'connectivity', 'airplane-mode', 'disable')
        time.sleep(5)


def leg_offline():
    offline_leg('offline_empty', False)


def leg_offline_history():
    offline_leg('offline_history', True)


def leg_legacy_minimal():
    start('Light', HOME_MINIMAL)
    wait_label('Quick picks', seconds=60, pkg=PKG)
    time.sleep(2)
    print('LEG legacy-minimal header-bounds=%s' % (header_top(),), flush=True)
    shot('legacy_minimal')
    prefs('home', None)


def leg_open_close(tag, theme, wait_art=False):
    start(theme)
    wait_label('Quick picks', seconds=60, pkg=PKG)
    time.sleep(2)
    card = find_card()
    if card is None:
        adb('shell', 'input', 'swipe', '540', '1500', '540', '1000', '300')
        time.sleep(1.5)
        card = find_card()
    if card is not None and wait_art:
        end = time.monotonic() + 45
        while time.monotonic() < end and (art_variance(card[2]) or 0) < 8:
            time.sleep(3)
            card = find_card() or card
    var = art_variance(card[2]) if card else None
    print('CARD ' + str(card) + ' art-variance-at-tap=' + str(var) + ' (flat placeholder is near 0)', flush=True)
    if card is None:
        shot(tag + '_nocard')
        return
    shot(tag + '_before')
    sig_before = sorted(n.get('text') for n in tree().iter('node') if n.get('text'))
    record(tag + '_open.mp4', lambda: adb('shell', 'input', 'tap', str(card[0]), str(card[1])))
    opened = on_page()
    print('LEG ' + tag + ' page-open-confirmed=' + str(opened), flush=True)
    shot(tag + '_page')
    if not opened:
        return
    record(tag + '_close.mp4', lambda: adb('shell', 'input', 'keyevent', '4'))
    time.sleep(1.5)
    after = find_card()
    sig_after = sorted(n.get('text') for n in tree().iter('node') if n.get('text'))
    print('LEG ' + tag + ' home-visible=' + str('Quick picks' in labels())
          + ' source-card-before=' + str(card[2:]) + ' source-card-after=' + str(after[2:] if after else None)
          + ' text-signature-equal=' + str(sig_before == sig_after)
          + ' only-before=' + str(sorted(set(sig_before) - set(sig_after))[:6])
          + ' only-after=' + str(sorted(set(sig_after) - set(sig_before))[:6]), flush=True)
    shot(tag + '_after')


def leg_reversal(theme):
    start(theme)
    wait_label('Quick picks', seconds=60, pkg=PKG)
    time.sleep(2)
    card = find_card()
    if card is None:
        print('LEG reversal no card', flush=True)
        return

    def act():
        adb('shell', 'input', 'tap', str(card[0]), str(card[1]))
        time.sleep(0.28)
        adb('shell', 'input', 'keyevent', '4')
        print('BACK pressed 280ms after tap (nominal; adb latency not measured)', flush=True)

    record('reversal.mp4', act)
    print('LEG reversal home-visible=' + str('Quick picks' in labels()) + ' page-visible=' + str(on_page()), flush=True)
    shot('reversal_after')


def main():
    adb('wait-for-device')
    original = adb('shell', 'settings', 'get', 'global', 'animator_duration_scale').decode().strip()
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '1')
    adb('shell', 'cmd', 'uimode', 'night', 'no')
    steps = [
        ('light states', lambda: leg_states('Light')),
        ('dark states', lambda: leg_states('Dark')),
        ('legacy minimal', leg_legacy_minimal),
        ('light open/close, art as it is at first tap', lambda: leg_open_close('light', 'Light')),
        ('dark open/close, art loaded', lambda: leg_open_close('dark', 'Dark', wait_art=True)),
        ('offline, no history', leg_offline),
        ('offline, seeded history', leg_offline_history),
    ]
    for name, step in steps:
        try:
            step()
        except Exception as e:
            print('LEG failed: ' + name + ' ' + repr(e), flush=True)
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '0')
    print('SCALE now=' + adb('shell', 'settings', 'get', 'global', 'animator_duration_scale').decode().strip(), flush=True)
    try:
        leg_open_close('noanim', 'Light')
    except Exception as e:
        print('LEG failed: noanim ' + repr(e), flush=True)
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', original if original not in ('null', '') else '1')


main()
