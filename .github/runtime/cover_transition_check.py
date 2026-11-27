#!/usr/bin/env python3
"""Card to page cover transition, as recordings and dense frame sheets: open, back,
reversal (back pressed mid-open), and open again with animations removed. Whether the page
opens at all depends on the network from this runner, so each leg reports what it
confirmed and never claims a result it did not see."""
import os
import re
import subprocess
import time

from playback_check import (adb, tree, screen, emit_image, ensure_ffmpeg, restart_app,
                            wait_label, pidof, PKG, OUT)

SHEET_FROM = 1.3
SHEET_SPAN = 2.0


def labels():
    out = set()
    for n in tree().iter('node'):
        for k in ('text', 'content-desc'):
            if n.get(k):
                out.add(n.get(k))
    return out


def find_card():
    """First clickable, portrait-ish card below the Quick picks header: a cover with a
    caption under it. Returns (cx, cy, bounds)."""
    root = tree()
    qp = next((n for n in root.iter('node') if 'Quick picks' in (n.get('text') or '')), None)
    top = int(re.findall(r'\d+', qp.get('bounds'))[3]) if qp is not None else 0
    best = None
    for n in root.iter('node'):
        if n.get('clickable') != 'true':
            continue
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        w, h = x2 - x1, y2 - y1
        if y1 > top + 200 and 250 <= w <= 700 and h > w and x1 >= 0 and x2 <= 1090:
            if best is None or y1 < best[1]:
                best = ((x1 + x2) // 2, y1 + w // 2, n.get('bounds'))
    return best


def sheet(name, mp4):
    if not ensure_ffmpeg() or not os.path.exists(mp4):
        print('SHEET SKIPPED ' + name, flush=True)
        return
    jpg = f'{OUT}/{name}_sheet.jpg'
    subprocess.run(['ffmpeg', '-y', '-v', 'error', '-ss', str(SHEET_FROM), '-t', str(SHEET_SPAN), '-i', mp4,
                    '-vf', 'fps=10,scale=200:-1,tile=10x2', '-frames:v', '1', '-q:v', '5', jpg])
    if os.path.exists(jpg):
        print('SHEET ' + name + ' bytes=' + str(os.path.getsize(jpg)), flush=True)
        emit_image(name + '_sheet', open(jpg, 'rb').read())


def record(name, action, seconds=9):
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
    sheet(name[:-4], OUT + '/' + name)


def open_card(card):
    adb('shell', 'input', 'tap', str(card[0]), str(card[1]))
    print('TAP card ' + card[2], flush=True)


def on_page():
    ls = labels()
    return 'Back' in ls and 'Quick picks' not in ls


def home_ready():
    wait_label('Quick picks', seconds=40, pkg=PKG)


def leg_open_close(tag):
    restart_app('Home', seconds=60)
    home_ready()
    time.sleep(2)
    card = find_card()
    print('CARD ' + str(card), flush=True)
    if card is None:
        screen(tag + '_nocard')
        return
    screen(tag + '_before')
    record(tag + '_open.mp4', lambda: open_card(card))
    opened = on_page()
    print('LEG ' + tag + ' page-open-confirmed=' + str(opened), flush=True)
    screen(tag + '_page')
    if opened:
        record(tag + '_close.mp4', lambda: adb('shell', 'input', 'keyevent', '4'))
        back_home = 'Quick picks' in labels()
        print('LEG ' + tag + ' return-to-home-confirmed=' + str(back_home), flush=True)
        screen(tag + '_after')


def leg_reversal():
    restart_app('Home', seconds=60)
    home_ready()
    time.sleep(2)
    card = find_card()
    if card is None:
        print('LEG reversal no card', flush=True)
        return

    def act():
        open_card(card)
        time.sleep(0.28)
        adb('shell', 'input', 'keyevent', '4')
        print('BACK pressed 280ms after tap (nominal; adb latency not measured)', flush=True)

    record('reversal.mp4', act)
    print('LEG reversal home-visible=' + str('Quick picks' in labels()) + ' page-visible=' + str(on_page()), flush=True)
    screen('reversal_after')


def main():
    adb('wait-for-device')
    original = adb('shell', 'settings', 'get', 'global', 'animator_duration_scale').decode().strip()
    print('SCALE initial=' + original, flush=True)
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '1')
    for leg in (lambda: leg_open_close('default'), leg_reversal):
        try:
            leg()
        except Exception as e:
            print('LEG failed: ' + repr(e), flush=True)
            screen('leg_failure')
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '0')
    print('SCALE now=' + adb('shell', 'settings', 'get', 'global', 'animator_duration_scale').decode().strip(), flush=True)
    try:
        leg_open_close('noanim')
    except Exception as e:
        print('LEG failed: ' + repr(e), flush=True)
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', original if original not in ('null', '') else '1')


main()
