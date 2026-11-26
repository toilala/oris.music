#!/usr/bin/env python3
"""Host tests for playback_check's pidof process-state helper: no device, no adb.
pidof exits 1 exactly when no process matches; only that quiet rc means 'dead'.
Run: python3 .github/runtime/playback_check_test.py"""
import subprocess
import sys
sys.path.insert(0, '.github/runtime')
import playback_check

fails = []
total = [0]


def check(name, cond):
    total[0] += 1
    print(('PASS ' if cond else 'FAIL ') + name, flush=True)
    if not cond:
        fails.append(name)


real_run = subprocess.run


def fake(rc, out=b'', err=b''):
    def _run(args, **kwargs):
        assert args[:3] == ['adb', 'shell', 'pidof'], args
        assert kwargs.get('timeout') == 30
        return subprocess.CompletedProcess(args, rc, out, err)
    return _run


try:
    playback_check.subprocess.run = fake(0, b'1234 1235\n')
    check('rc0 returns the pid list', playback_check.pidof('x') == ['1234', '1235'])

    playback_check.subprocess.run = fake(1)
    check('rc1 with no output means dead', playback_check.pidof('x') == [])

    playback_check.subprocess.run = fake(1, b'1234\n')
    try:
        playback_check.pidof('x')
        check('rc1 with output raises', False)
    except AssertionError:
        check('rc1 with output raises', True)

    playback_check.subprocess.run = fake(2, err=b'permission denied')
    try:
        playback_check.pidof('x')
        check('unexpected rc raises with stderr', False)
    except AssertionError as e:
        check('unexpected rc raises with stderr', 'permission denied' in str(e))

    def _timeout(args, **kwargs):
        raise subprocess.TimeoutExpired(args, kwargs.get('timeout'))
    playback_check.subprocess.run = _timeout
    try:
        playback_check.pidof('x')
        check('timeout propagates', False)
    except subprocess.TimeoutExpired:
        check('timeout propagates', True)
finally:
    playback_check.subprocess.run = real_run

print('TOTAL ' + str(total[0]) + ', FAILURES ' + str(len(fails)), flush=True)
if fails:
    raise SystemExit('host test failures: ' + ', '.join(fails))
