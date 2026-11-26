#!/usr/bin/env python3
"""Host tests for history_check's pure helpers: no device, no adb.
Run: python3 .github/runtime/history_check_test.py"""
import sys
sys.path.insert(0, '.github/runtime')
from history_check import wav_bytes, library_values, session_state, Q_ID, NEXT_ID, H_ID

fails = []
total = [0]


def check(name, cond):
    total[0] += 1
    print(('PASS ' if cond else 'FAIL ') + name, flush=True)
    if not cond:
        fails.append(name)


# wav_bytes: RIFF header, 8kHz 16-bit mono, exact data length.
w = wav_bytes(3, 440.0)
check('wav riff magic', w[:4] == b'RIFF' and w[8:12] == b'WAVE')
check('wav data length', len(w) == 44 + 8000 * 3 * 2)
check('wav declared size', int.from_bytes(w[4:8], 'little') == len(w) - 8)
check('wav data chunk', w[36:40] == b'data'
      and int.from_bytes(w[40:44], 'little') == 8000 * 3 * 2)

# library_values: parsed VALUES from prefs XML, ignoring serialization detail. The
# harness seeds with the same escaping SharedPreferences uses; entity-escaped quotes
# resolve to plain JSON after XML parsing.
XML = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>"
       '<string name="history">[{&quot;id&quot;:&quot;h&quot;,&quot;title&quot;:&quot;T&quot;,&quot;artist&quot;:&quot;A&quot;}]</string>'
       '<string name="counts">{&quot;h&quot;: 2}</string>'
       '</map>')
history, counts = library_values(XML)
check('history parsed in order', [s['id'] for s in history] == ['h'])
check('counts parsed as ints', counts == {'h': 2})

# Same values, different serialization (whitespace, key order): still equal values.
other = ("<?xml version='1.0' ?><map>"
         '<string name="counts">{"h":2}</string>'
         '<string name="history">[ { "id":"h", "title":"T", "artist":"A" } ]</string>'
         '</map>')
h2, c2 = library_values(other)
check('value comparison ignores serialization', h2 == history and c2 == counts)

# A value change is detected (this is what the restore phase asserts against).
changed = ("<?xml version='1.0' ?><map>"
           '<string name="history">[{"id":"' + Q_ID + '","title":"Q","artist":"A"},'
           '{"id":"h","title":"T","artist":"A"}]</string>'
           '<string name="counts">{"h":2,"' + Q_ID + '":1}</string></map>')
h3, c3 = library_values(changed)
check('restore-as-play signature detected', [s['id'] for s in h3] == [Q_ID, 'h'] and c3 != counts)

print('TOTAL ' + str(total[0]) + ', FAILURES ' + str(len(fails)), flush=True)
if fails:
    raise SystemExit('host test failures: ' + ', '.join(fails))
