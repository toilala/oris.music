#!/usr/bin/env python3
"""Host tests for eq_verdict: no device, no adb. Run: python3 .github/runtime/eq_verdict_test.py"""
import sys
sys.path.insert(0, '.github/runtime')
from eq_verdict import parse_prefs, clamp_reports, classify_preset, PRESET_VECTORS

PREFS_BASS = ('<?xml version="1.0" ?><map>'
              '<boolean name="enabled" value="true" />'
              '<string name="levels">1500,1050,0,0,0</string></map>')
CLAMP_1100 = '11-25 22:08:18.308 W OrisEQ  : band 1 clamped by the platform: 1050 -> 1100'

fails = []
total = [0]


def check(name, cond):
    total[0] += 1
    print(('PASS ' if cond else 'FAIL ') + name, flush=True)
    if not cond:
        fails.append(name)


# parse_prefs
check('parse enabled+levels', parse_prefs(PREFS_BASS) == (True, [1500, 1050, 0, 0, 0]))
check('parse missing levels', parse_prefs('<map><boolean name="enabled" value="false" /></map>') == (False, None))
check('parse empty', parse_prefs('') == (None, None))

# clamp_reports
check('clamp parse', clamp_reports(CLAMP_1100) == [{'band': 1, 'intended': 1050, 'observed': 1100, 'kind': 'clamped'}])
check('reject parse', clamp_reports('W OrisEQ: band 2 level 900 rejected by the platform')[0]['kind'] == 'rejected')
check('no clamps', clamp_reports('unrelated logcat') == [])

# classify: named preset passes
v = classify_preset('Bass boost', PREFS_BASS, '', 'Active preset: Bass boost')
check('named passes', v['verdict'] == 'pass')

# classify: Custom explained by the observed 1050->1100 clamp reports, not fails
v = classify_preset('Bass boost', PREFS_BASS, CLAMP_1100, 'Active preset: Custom')
check('custom explained reports', v['verdict'] == 'report' and v['differences'][0]['observed'] == 1100)

# classify: Custom without explanation fails
v = classify_preset('Bass boost', PREFS_BASS, '', 'Active preset: Custom')
check('custom unexplained fails', v['verdict'] == 'fail')

# classify: Unknown never passes as current
v = classify_preset('Bass boost', PREFS_BASS, CLAMP_1100,
                    'Unknown: the equalizer state could not be confirmed on this output.')
check('unknown never passes', v['verdict'] == 'fail')

# classify: missing state line fails
v = classify_preset('Bass boost', PREFS_BASS, CLAMP_1100, None)
check('missing state line fails', v['verdict'] == 'fail')

# classify: wrong persisted intent fails even when named
bad = PREFS_BASS.replace('1500,1050,0,0,0', '1500,1100,0,0,0')
v = classify_preset('Bass boost', bad, '', 'Active preset: Bass boost')
check('wrong intent fails', v['verdict'] == 'fail')

# classify: a state line naming a different preset fails even with a matching clamp
v = classify_preset('Bass boost', PREFS_BASS, CLAMP_1100, 'Active preset: Rock')
check('different named preset fails', v['verdict'] == 'fail')

# classify: a clamp log whose intended value is not this preset's intent does not explain
v = classify_preset('Bass boost', PREFS_BASS,
                    'W OrisEQ: band 3 level 700 clamped by the platform: 700 -> 800',
                    'Active preset: Custom')
check('unrelated clamp does not explain', v['verdict'] == 'fail')

# pinned vectors stay aligned with their source shape sizes
check('vectors are 5-band', all(len(v) == 5 for v in PRESET_VECTORS.values()))

print('RESULT: ' + ('PASS %d checks' % total[0] if not fails else 'FAILURES: ' + ', '.join(fails)), flush=True)
raise SystemExit(1 if fails else 0)
