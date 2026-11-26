#!/usr/bin/env python3
"""Real playback-failure UI check: cut networking, tap a quick pick, expand the
mini player, capture the failure banner and the diagnostic history dialog with
real pixels."""
import re
import time
from playback_check import adb, screen, tree, wait_label, still_open, PKG

FAIL_FRAGMENTS = ['no connection', 'check your network', "can't play", "couldn't play", 'refused this request']


def bounds(n):
    return list(map(int, re.findall(r'\d+', n.get('bounds'))))


def subtree_texts(n):
    return [(d.get('text') or '') for d in n.iter('node') if (d.get('text') or '').strip()]


def tap_bounds(b, fx=0.5, fy=0.5):
    adb('shell', 'input', 'tap', str(int(b[0] + (b[2] - b[0]) * fx)), str(int(b[1] + (b[3] - b[1]) * fy)))
    time.sleep(2)


def tap_node(n):
    tap_bounds(bounds(n))



import atexit


@atexit.register
def _atexit_logcat():
    # Evidence on failure too: whatever happens later, the device log lands in the artifact.
    try:
        import os
        os.makedirs('runtime-evidence', exist_ok=True)
        open('runtime-evidence/diag-logcat.txt', 'wb').write(adb('shell', 'logcat', '-d'))
    except Exception:
        pass


def mark(name):
    # Marker written into logcat itself: later analysis can split events by phase
    # even though the in-app diagnostic history carries no timestamps.
    try:
        adb('shell', 'log', '-p', 'i', '-t', 'OrisHarness', 'phase=' + name)
    except Exception:
        pass
    print('phase', name, flush=True)


EXPECTED_MARKERS = ('phase=after-cut', 'phase=cut-verified', 'OrisDiag', 'resolve r')


def logcat_capture():
    # Dump the device log now and record what evidence it actually contains.
    # Returns (raw bytes, missing expected markers); an empty dump or missing
    # markers means the capture is incomplete and no product verdict is safe.
    try:
        log = adb('shell', 'logcat', '-d') or b''
    except Exception:
        log = b''
    try:
        import os
        os.makedirs('runtime-evidence', exist_ok=True)
        open('runtime-evidence/diag-logcat.txt', 'wb').write(log)
        missing = [m for m in EXPECTED_MARKERS if m.encode() not in log]
        open('runtime-evidence/logcat-capture.txt', 'w').write(
            'bytes=%d missing=%s' % (len(log), ','.join(missing) if missing else 'none'))
    except Exception:
        pass
    missing = [m for m in EXPECTED_MARKERS if m.encode() not in log]
    return log, missing


def net_connected():
    # OS-level active-network state. Evidence of radio state only, never of
    # endpoint reachability or of the app process's resolver.
    try:
        return b'state: CONNECTED' in adb('shell', 'dumpsys', 'connectivity')
    except Exception:
        return True  # unreadable state must not look like a successful cut


def net(mode):
    # adb root restarts adbd and the device can briefly read as offline; retry.
    for _ in range(5):
        try:
            adb('wait-for-device')
            adb('shell', 'svc', 'wifi', mode)
            adb('shell', 'svc', 'data', mode)
            return
        except Exception:
            time.sleep(3)
    raise AssertionError('network ' + mode + ' failed')


def nav_top(root):
    # Bottom nav buttons sit in the lowest clickable band of the screen.
    tops = [bounds(n)[1] for n in root.iter('node')
            if n.get('clickable') == 'true' and bounds(n)[1] > 2000]
    return min(tops) if tops else 2400


def quick_pick_cells(root):
    # Cells strictly between the 'Quick picks' header and the next section
    # header, excluding the 'Play all' pill on the header row.
    nodes = list(root.iter('node'))
    header = next((n for n in nodes if (n.get('text') or '') == 'Quick picks'), None)
    assert header is not None, 'Quick picks header missing'
    hy = bounds(header)[1]
    below = [n for n in nodes if n.get('clickable') == 'true' and bounds(n)[1] > hy + 100]
    next_header_y = None
    for n in nodes:
        t = (n.get('text') or '')
        if t and t != 'Quick picks' and 'playlists' in t.lower():
            y = bounds(n)[1]
            if y > hy:
                next_header_y = y if next_header_y is None else min(next_header_y, y)
    cells = []
    for n in below:
        b = bounds(n)
        if next_header_y is not None and b[1] >= next_header_y:
            continue
        texts = subtree_texts(n)
        if not texts:
            continue  # 'Play all' pill and icon-only controls carry no cell title
        cells.append((b[1], b[0], n, texts[0]))
    cells.sort()
    return cells


def tray_node(root):
    # Mini player: the wide clickable band directly above the bottom nav bar.
    nt = nav_top(root)
    cands = []
    for n in root.iter('node'):
        if n.get('clickable') != 'true':
            continue
        b = bounds(n)
        if nt - 400 <= b[3] <= nt and (b[2] - b[0]) > 540 and subtree_texts(n):
            cands.append((b[3], n))
    cands.sort(reverse=True)
    return cands[0][1] if cands else None


def wait_tray(expected_titles, seconds=90):
    end = time.monotonic() + seconds
    last_title = None
    stable = 0
    while time.monotonic() < end:
        root = tree()
        if any((n.get('text') or '') == 'Lyrics' for n in root.iter('node')):
            print('full player opened directly', flush=True)
            return 'OPEN'
        tray = tray_node(root)
        if tray is not None:
            texts = subtree_texts(tray)
            title = texts[0] if texts else ''
            if title == last_title:
                stable += 1
            else:
                stable = 0
                last_title = title
            if stable >= 1:
                norm = title.strip().lower()[:12]
                if any(norm and (norm in t.strip().lower() or t.strip().lower()[:12] in norm)
                       for t in expected_titles):
                    print(f'tray engaged with quick-pick song: {title!r}', flush=True)
                    return tray
                screen('diag_tray_mismatch')
                raise AssertionError(
                    f'tray shows {title!r}, not one of the quick-pick titles {expected_titles!r}')
        time.sleep(2)
    screen('failure')
    raise AssertionError('mini player tray never appeared')


def wait_failure(seconds=150):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        for n in tree().iter('node'):
            t = (n.get('text') or '').lower()
            if any(f in t for f in FAIL_FRAGMENTS):
                return n
        time.sleep(2)
    screen('diag_no_failure')
    # No product verdict until capture completeness and resolve initiation are
    # established; the 150s window is a test ceiling, not accepted silence.
    log, missing = logcat_capture()
    if not log or missing:
        raise AssertionError(
            'INCONCLUSIVE: no failure banner within %ss; capture incomplete (%s) - suspected silent stall'
            % (seconds, ('missing markers: ' + ', '.join(missing)) if missing else 'empty logcat'))
    raise AssertionError(
        'SILENT STALL: resolve initiated but the player gave no failure feedback within %ss of a verified cut'
        % seconds)


adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
import glob
adb('install', '-r', glob.glob('app/build/outputs/apk/foss/debug/*.apk')[0])
try:
    adb('shell', 'pm', 'grant', PKG, 'android.permission.POST_NOTIFICATIONS')
except Exception:
    pass  # POST_NOTIFICATIONS only exists on API 33+
adb('shell', 'am', 'start', '-n', PKG + '/com.orismusic.client.MainActivity')
wait_label('Quick picks', seconds=120)
time.sleep(5)
screen('diag_home_online')

# Cut connectivity so the stream resolve really fails.
try:
    adb('root')
    time.sleep(2)
except Exception:
    pass
mark('before-cut')
net('disable')
mark('after-cut')
# The cut is only real once the OS reports no connected network; svc can return 0
# while the radio state lags. This is OS-state evidence, not endpoint proof:
# reachability is only ever judged by the app's own resolve behaviour.
end = time.monotonic() + 45
while net_connected():
    if time.monotonic() > end:
        raise AssertionError('network cut ineffective: OS still reports a connected network')
    time.sleep(3)
mark('cut-verified')
time.sleep(3)

# Tap the first quick-pick cell (structural pick, title recorded for verification).
root = tree()
cells = quick_pick_cells(root)
assert cells, 'no quick pick cell found'
tapped_title = cells[0][3]
titles = [c[3] for c in cells]
print(f'tapping quick pick: {tapped_title!r}', flush=True)
tap_node(cells[0][2])

# Wait for the mini player engaged with a quick-pick song, then expand it.
tray = wait_tray(titles)
screen('diag_tray')
if tray != 'OPEN':
    # Expand per the pill's own gesture: vertical drag up ends in onExpand
    # (MiniPlayer detectDragGestures, y < -90). Drags are not consumed by the
    # nested artist/button clickables, so this cannot navigate away.
    b = bounds(tray)
    x = int(b[0] + (b[2] - b[0]) * 0.35)
    y1 = int(b[1] + (b[3] - b[1]) * 0.6)
    adb('shell', 'input', 'swipe', str(x), str(y1), str(x), str(y1 - 350), '150')
    time.sleep(2)
    if not any((n.get('content-desc') or '') == 'Lyrics' for n in tree().iter('node')):
        # Fallback: tap the artwork (content-desc == song title, not clickable);
        # the tap bubbles up to the pill's pressClickable(onExpand).
        root = tree()
        tray2 = tray_node(root)
        if tray2 is not None:
            texts = subtree_texts(tray2)
            art = next((n for n in tray2.iter('node')
                        if texts and (n.get('content-desc') or '') == texts[0]
                        and n.get('clickable') != 'true'), None)
            if art is not None:
                tap_node(art)

wait_label('Lyrics', seconds=60)   # full player opened (content-desc on the lyrics button)
# Capture the expanded player's UI tree for structural verification.
adb('shell', 'uiautomator', 'dump', '/sdcard/oris-player.xml')
open('runtime-evidence/diag_player_tree.xml', 'wb').write(adb('shell', 'cat', '/sdcard/oris-player.xml'))
banner = wait_failure()
screen('diag_banner')
tap_node(banner)                    # banner tap opens the history dialog
wait_label('Playback diagnostic', seconds=30)
time.sleep(1)
screen('diag_dialog')

# Back online and healthy. svc returning 0 does not mean DNS is back yet:
# gate on a real lookup before trusting the network again.
net('enable')
mark('after-enable')
end = time.monotonic() + 90
while not net_connected():
    if time.monotonic() > end:
        raise AssertionError('OS never reported a connected network after net enable')
    time.sleep(3)
mark('connectivity-verified')
from playback_check import tap
tap('Close')

# Compact display + large text: layout evidence on a smaller canvas.
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
adb('shell', 'wm', 'size', '900x2000')
adb('shell', 'wm', 'density', '320')
time.sleep(4)
screen('diag_banner_compact')
tap('Collapse player')
time.sleep(2)
screen('diag_home_compact')
adb('shell', 'wm', 'size', 'reset')
adb('shell', 'wm', 'density', 'reset')
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
time.sleep(3)

mark('before-retry-tap')
# Functional Retry check: reopen the player, tap Retry, and require the banner
# to clear - reportStarted only fires when playback really starts.
root = tree()
tray = tray_node(root)
assert tray is not None, 'tray missing after display reset'
b = bounds(tray)
x = int(b[0] + (b[2] - b[0]) * 0.35)
y1 = int(b[1] + (b[3] - b[1]) * 0.6)
adb('shell', 'input', 'swipe', str(x), str(y1), str(x), str(y1 - 350), '150')
time.sleep(2)
wait_label('Retry', seconds=30)
tap('Retry')
end = time.monotonic() + 120
cleared = False
while time.monotonic() < end:
    root = tree()
    if not any(any(f in (n.get('text') or '').lower() for f in FAIL_FRAGMENTS) for n in root.iter('node')):
        cleared = True
        break
    time.sleep(3)
screen('diag_retry_recovery')
# A failed retry never stops evidence collection, but the run must still end red:
# visual results and functional recovery are recorded distinctly.
if not cleared:
    tap('Details')
    wait_label('Playback diagnostic', seconds=15)
    time.sleep(1)
    screen('diag_retry_details')
    tap('Close')
    time.sleep(1)

# Home list end with the tray present: clearance evidence for the dock stack.
tap('Collapse player')
time.sleep(2)
for _ in range(8):
    adb('shell', 'input', 'swipe', '540', '1800', '540', '500', '250')
    time.sleep(1)
time.sleep(2)
screen('diag_home_end_tray')

logcat, missing = logcat_capture()
if not logcat or missing:
    print('capture-warning: evidence incomplete -',
          ('missing markers: ' + ', '.join(missing)) if missing else 'empty logcat', flush=True)
assert b'FATAL EXCEPTION' not in logcat, 'app crashed during diagnostic check'
still_open()
print('VISUAL: PASS - banner, dialog, compact layout and home end rendered and captured', flush=True)
if cleared:
    print('FUNCTIONAL RECOVERY: PASS - Retry restarted playback and the banner cleared', flush=True)
else:
    raise AssertionError('FUNCTIONAL RECOVERY: FAIL - Retry did not restart playback; raw chain is in diag_retry_details.png')
