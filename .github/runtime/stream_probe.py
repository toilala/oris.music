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
from playback_check import adb, tree, wait_label, screen, restart_app, PKG

FAIL_FRAGMENTS = ['refused', 'not a bot', 'No playable stream', 'TIMED OUT', 'NETWORK', 'no connection']
KEEP = ('OrisDiag', 'YTPlayerUtils', 'BitChord', 'InnerTube', 'PoToken', 'poToken', 'BotGuard',
        'AndroidRuntime', 'ExoPlayer', 'visitor', 'ITX')

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
print('PROBE ui-state=' + state + (' fragment=' + frag if frag else ''), flush=True)
print('PROBE positions p1=' + str(p1) + ' p2=' + str(p2), flush=True)
print('PROBE logcat evidence (' + str(len(lines)) + ' lines):', flush=True)
for line in lines[-140:]:
    print(line, flush=True)
joined = '\n'.join(lines)
started = 'playback started' in joined
if started and state == 'advancing':
    diag = 'RESOLVED: source accepted, playback advancing (%s -> %s ms)' % (p1, p2)
elif started:
    diag = 'RESOLVED: source accepted, position delta unavailable (p1=%s p2=%s)' % (p1, p2)
elif 'not a bot' in joined or 'LOGIN_REQUIRED' in joined:
    diag = 'REFUSED_BY_YOUTUBE_BOT_CHECK on this connection'
elif re.search(r'resolve r\d+ ok', joined):
    diag = 'RESOLVED (resolve ok, no playback-started line)'
elif 'timed out' in joined:
    diag = 'TIMED OUT'
elif 'no network' in joined:
    diag = 'NO NETWORK (OS)'
elif 'Extraction failed' in joined or 'StreamResolveException' in joined:
    diag = 'EXTRACTION FAILURE'
else:
    diag = 'INCONCLUSIVE (ui-state=' + state + ')'
print('DIAGNOSIS: ' + diag, flush=True)
assert state in ('advancing', 'stalled') or 'OrisDiag' in joined or frag, 'probe captured no resolution evidence'
