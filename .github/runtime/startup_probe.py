#!/usr/bin/env python3
"""Cold-restart stability probe: 10 force-stop -> start cycles through the hardened
restart_app transition. Run 7 evidence already ruled the app-crash reading out for
that failure (launcher screenshot, zero FATAL lines in the full AndroidRuntime dump);
this measures recurrence under the fixed transition and captures full evidence if it
still fails."""
from playback_check import adb, restart_app, PKG

failures = 0
for cycle in range(1, 11):
    try:
        restart_app('Home', seconds=45)
        print('cycle %d ok' % cycle, flush=True)
    except AssertionError as e:
        failures += 1
        print('cycle %d FAILED: %s' % (cycle, e), flush=True)
        raw = adb('shell', 'logcat', '-d').decode(errors='replace')
        keep = [l for l in raw.splitlines()
                if 'AndroidRuntime' in l or 'ActivityManager' in l or 'FATAL' in l or 'OrisDiag' in l]
        print('cycle %d logcat evidence (%d lines):' % (cycle, len(keep)), flush=True)
        for line in keep[-60:]:
            print(line, flush=True)
print('PROBE: %d/10 cold restarts failed' % failures, flush=True)
if failures:
    raise AssertionError('cold restart failed %d/10 cycles; evidence above' % failures)
print('PASS: 10/10 cold restarts foregrounded the app', flush=True)
