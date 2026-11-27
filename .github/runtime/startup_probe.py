#!/usr/bin/env python3
"""Cold-restart stability probe: 10 force-stop -> start cycles. Each cycle measures the
FIRST start on its own (launch stdout, process alive, resumed activity) before any
retry, so a retry can never hide a recurrence. Run 7 evidence (launcher screenshot,
zero FATAL lines in the full AndroidRuntime dump) made a dropped start the leading
hypothesis; this run produces the launch/process/activity evidence for or against it."""
import re
import time
import subprocess
from playback_check import adb, stop_app, pidof, PKG, OUT, screen

def foreground_pkg():
    out = adb('shell', 'dumpsys', 'activity', 'activities').decode(errors='replace')
    m = re.search(r'mResumedActivity:.*?([a-zA-Z0-9_.]+)/', out)
    return m.group(1) if m else None

first_failures = 0
retry_recovered = 0
for cycle in range(1, 11):
    stop_app()
    start_out = subprocess.run(['adb', 'shell', 'am', 'start', '-n',
                                PKG + '/com.orismusic.client.MainActivity'],
                               capture_output=True, timeout=30)
    launch = (start_out.stdout.decode(errors='replace') + start_out.stderr.decode(errors='replace')).strip().replace('\n', ' | ')
    # First-start measurement window: 15s to foreground.
    fg = None
    alive = False
    end = time.monotonic() + 15
    while time.monotonic() < end:
        fg = foreground_pkg()
        alive = bool(pidof(PKG))
        if fg == PKG:
            break
        time.sleep(1)
    if fg == PKG:
        print('cycle %d: first start foregrounded (launch=%s)' % (cycle, launch), flush=True)
        continue
    first_failures += 1
    print('cycle %d: FIRST START FAILED foreground=%s alive=%s launch=%s' % (cycle, fg, alive, launch), flush=True)
    screen('startup-fail-%d' % cycle)
    raw = adb('shell', 'logcat', '-d').decode(errors='replace')
    keep = [l for l in raw.splitlines()
            if 'AndroidRuntime' in l or 'ActivityManager' in l or 'FATAL' in l or 'OrisDiag' in l]
    print('cycle %d logcat evidence (%d lines):' % (cycle, len(keep)), flush=True)
    for line in keep[-60:]:
        print(line, flush=True)
    # Recovery path, reported separately.
    adb('shell', 'am', 'start', '-n', PKG + '/com.orismusic.client.MainActivity')
    fg2 = None
    end = time.monotonic() + 45
    while time.monotonic() < end:
        fg2 = foreground_pkg()
        if fg2 == PKG:
            break
        time.sleep(2)
    if fg2 == PKG:
        retry_recovered += 1
        print('cycle %d: retry recovered' % cycle, flush=True)
    else:
        print('cycle %d: RETRY ALSO FAILED foreground=%s' % (cycle, fg2), flush=True)
print('PROBE: first-start failures %d/10, retry recovered %d, unrecovered %d'
      % (first_failures, retry_recovered, first_failures - retry_recovered), flush=True)
if first_failures - retry_recovered:
    raise AssertionError('cold restart unrecoverable in %d cycles; evidence above' % (first_failures - retry_recovered))
print('PASS: every cycle foregrounded the app (first-start failures and retries reported above)', flush=True)
