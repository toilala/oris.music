#!/usr/bin/env python3
"""Network-disabled entry for the runtime suite: cut the emulator's radios, prove the
state from dumpsys connectivity before and after, then run only the checks that need
no network. This is network-disabled testing: OS state is the evidence, not any
script's promise, and a reconnection during the session invalidates the run."""
from playback_check import adb, OUT
import glob
import re
import subprocess
import time


def connected_lines():
    conn = adb('shell', 'dumpsys', 'connectivity').decode()
    return conn, [line for line in conn.splitlines()
                  if re.search(r'\bCONNECTED\b', line) and 'DISCONNECTED' not in line]


adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
adb('shell', 'svc', 'wifi', 'disable')
adb('shell', 'svc', 'data', 'disable')
time.sleep(3)
conn, live = connected_lines()
open(OUT + '/connectivity-before.txt', 'w').write(conn)
assert not live, 'network still live, refusing to run: ' + repr(live[:2])
print('NETWORK-DISABLED: no connected network before the checks', flush=True)

adb('install', '-r', glob.glob('app/build/outputs/apk/foss/debug/*.apk')[0])

failures = []
for check in ['eq_check.py', 'large_text_check.py', 'reduced_motion_check.py']:
    result = subprocess.run(['python3', '.github/runtime/' + check])
    if result.returncode:
        failures.append(check)

conn, live = connected_lines()
open(OUT + '/connectivity-after.txt', 'w').write(conn)
assert not live, 'network RECONNECTED during the session: ' + repr(live[:2])
print('NETWORK-DISABLED: still no connected network after the checks', flush=True)

if failures:
    raise AssertionError('Network-disabled checks failed: ' + ', '.join(failures))
print('PASS: network-disabled subset (equalizer, large text, reduced motion)', flush=True)
