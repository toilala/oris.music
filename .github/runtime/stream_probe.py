#!/usr/bin/env python3
"""Online stream-resolution probe, evidence-only: play a real Quick pick so the app
runs its full resolve chain against YouTube from this connection, then print the app's
own diagnostics (OrisDiag resolve attempts, YTPlayerUtils refusals, InnerTubeX/PoToken
lines) plus the visible UI state. A mid-chain refusal fragment does not end the watch:
the catalog stage may still resolve after it. When playback starts, media-session
position is sampled twice so source acceptance and advancing playback are measured,
not assumed. Classifies what it captured; asserts only that real evidence exists."""
import re
import time
from playback_check import adb, tree, wait_label, screen, restart_app, PKG, OUT

FAIL_FRAGMENTS = ['refused', 'not a bot', 'No playable stream', 'TIMED OUT', 'NETWORK', 'no connection']
KEEP = ('OrisDiag', 'YTPlayerUtils', 'BitChord', 'InnerTube', 'PoToken', 'poToken', 'BotGuard',
        'AndroidRuntime', 'ExoPlayer', 'visitor', 'ITX', 'OrisHarness')

def media_position():
    out = adb('shell', 'dumpsys', 'media_session').decode(errors='replace')
    vals = [int(m) for m in re.findall(r'position=(\d+)', out)]
    return max(vals) if vals else None

restart_app('Home', seconds=60)

# Quick picks are real feed tracks with real video ids: tapping one runs the true
# resolve path (VISIONOS-direct, then InnerTubeX extraction with PoToken).
qp = wait_label('Quick picks', seconds=90, pkg=PKG)
points = list(map(int, re.findall(r'\d+', qp.get('bounds'))))
adb('shell', 'log', '-p', 'i', '-t', 'OrisHarness', 'phase=probe-tap')
adb('shell', 'input', 'tap', str((points[0] + points[2]) // 2), str(points[3] + 90))
t0 = time.monotonic()
state = 'unresolved'
frag = ''
while time.monotonic() - t0 < 75:
    root = tree()
    texts = ' | '.join((n.get('text') or '') for n in root.iter('node') if (n.get('text') or '').strip())
    descs = ' | '.join((n.get('content-desc') or '') for n in root.iter('node') if (n.get('content-desc') or '').strip())
    hit = next((f for f in FAIL_FRAGMENTS if f in texts or f in descs), None)
    if hit:
        frag = hit
    if 'Pause' in descs:
        state = 'playing'
        break
    time.sleep(2)
p1 = p2 = None
if state == 'playing':
    p1 = media_position()
    time.sleep(12)
    p2 = media_position()
    if p1 is not None and p2 is not None:
        state = 'advancing' if p2 > p1 else 'stalled'
elif frag:
    state = 'failed'
screen('stream_probe_end')
raw = adb('shell', 'logcat', '-d').decode(errors='replace')
lines = [l for l in raw.splitlines() if any(k in l for k in KEEP)]
# Retain the full filtered evidence in the artifact on every outcome; the job log
# prints only the tail, and resolve-phase lines were lost when it truncated.
with open(f'{OUT}/stream-probe-logcat.txt', 'w') as f:
    f.write('\n'.join(lines) + '\n')
print('PROBE ui-state=' + state + (' fragment=' + frag if frag else ''), flush=True)
print('PROBE positions p1=' + str(p1) + ' p2=' + str(p2), flush=True)
print('PROBE logcat evidence (' + str(len(lines)) + ' lines):', flush=True)
mark = next((i for i, l in enumerate(lines) if 'phase=probe-tap' in l), None)
print('PROBE tap marker at kept-line index ' + str(mark) + ' of ' + str(len(lines)), flush=True)
for line in (lines[mark:mark + 400] if mark is not None else lines[-140:]):
    print(line, flush=True)
# Classify only what happened after this probe's own tap. Lines from app start (the
# preload and warm-up) are kept in the artifact but never decide the label.
attempt = lines[mark:] if mark is not None else []
joined = '\n'.join(attempt)
started = 'playback started' in joined
fetch_refused = re.search(r'Response code: 403|responseCode=403', joined) is not None
terminal_stop = 'failed to load after retrying' in joined or 'failed in a row' in joined
if mark is None:
    diag = 'NO TAP MARKER: attempt window unknown, nothing classified'
elif started and state == 'advancing':
    diag = 'PLAYBACK ADVANCING: source accepted (%s -> %s ms)' % (p1, p2)
elif started and state == 'stalled':
    diag = 'PLAYBACK STARTED BUT STALLED (p1=%s p2=%s)' % (p1, p2)
elif started:
    diag = 'PLAYBACK STARTED, position delta unavailable (p1=%s p2=%s)' % (p1, p2)
elif terminal_stop and fetch_refused:
    diag = 'REFUSED_AT_MEDIA_FETCH: minted URL got HTTP 403, retries stopped after this tap'
elif terminal_stop:
    diag = 'RETRIES STOPPED after this tap, cause not in the lines'
elif re.search(r'resolve r\d+ ok', joined):
    diag = 'URL MINTED after this tap, playback not observed (ui-state=%s)' % state
elif 'not a bot' in joined or 'LOGIN_REQUIRED' in joined:
    diag = 'REFUSED_BY_YOUTUBE_BOT_CHECK after this tap'
elif 'TIMED OUT' in joined or 'timed out' in joined:
    diag = 'TIMED OUT after this tap'
elif 'no network' in joined:
    diag = 'NO NETWORK (OS)'
elif 'Extraction failed' in joined or 'StreamResolveException' in joined:
    diag = 'EXTRACTION FAILURE after this tap'
else:
    diag = 'INCONCLUSIVE after this tap (ui-state=' + state + ', lines-after-tap=' + str(len(attempt)) + ')'
print('DIAGNOSIS: ' + diag, flush=True)
assert state in ('advancing', 'stalled') or 'OrisDiag' in joined or frag or mark is not None, 'probe captured no resolution evidence'
