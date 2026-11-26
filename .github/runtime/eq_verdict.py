"""Pure verdict logic for eq_check.py, kept importable so host tests can pin it without
a device. Nothing here talks to adb: values in, verdict out.

Grounding:
- Preset shapes and the presetLevels computation live in
  app/src/main/kotlin/com/orismusic/client/playback/EqualizerController.kt (presets map,
  float32 arithmetic). The pinned vectors below match that source for the emulator's
  5-band, -1500..1500 layout; the Bass boost band-1 value 1050 was confirmed by the
  app's own clamp log on run 45709763983 ("band 1 clamped by the platform: 1050 ->
  1100"), which is also the only quantization data point: no grid rule is assumed.
- The app publishes only platform-read-back values and derives "Custom" when the
  observed levels match no preset exactly. So a Custom state line after a preset tap is
  not automatically failure: it is failure only when nothing explains the difference.
"""

# Pinned from EqualizerController.presets for bandCount=5, min=-1500, max=1500.
PRESET_VECTORS = {
    'Flat': [0, 0, 0, 0, 0],
    'Bass boost': [1500, 1050, 0, 0, 0],
    'Treble boost': [0, 0, 0, 900, 1500],
    'Vocal': [-600, 0, 1050, 750, -300],
    'Rock': [1050, 450, -450, 600, 1200],
}


def parse_prefs(prefs_xml):
    """The persisted INTENT: (enabled, levels) from shared_prefs text, Nones when absent.
    Intent is what the user asked for, never the device's answer."""
    import re
    enabled = None
    m = re.search(r'name="enabled"\s+value="(true|false)"', prefs_xml)
    if m:
        enabled = m.group(1) == 'true'
    levels = None
    m = re.search(r'name="levels">([^<]*)<', prefs_xml)
    if m:
        try:
            levels = [int(x.strip()) for x in m.group(1).split(',') if x.strip()]
        except ValueError:
            levels = None
    return enabled, levels


def clamp_reports(logcat_text):
    """The app's own platform-write logs for this action window, newest last:
    [{band, intended, observed}] for clamps and [{band, intended, observed: None}] for
    rejected writes. These lines are written by EqualizerController after real writes,
    so they pair intent with the platform's answer for the same action."""
    import re
    out = []
    for line in logcat_text.splitlines():
        m = re.search(r'band (\d+) clamped by the platform: (-?\d+) -> (-?\d+)', line)
        if m:
            out.append({'band': int(m.group(1)), 'intended': int(m.group(2)),
                        'observed': int(m.group(3)), 'kind': 'clamped'})
            continue
        m = re.search(r'band (\d+) level (-?\d+) rejected by the platform', line)
        if m:
            out.append({'band': int(m.group(1)), 'intended': int(m.group(2)),
                        'observed': None, 'kind': 'rejected'})
    return out


def classify_preset(target, prefs_xml, logcat_text, state_line):
    """Verdict for one preset tap. Inputs are the persisted intent, the app's clamp log
    for the same action window, and the app state line (all platform observations).

    Returns {verdict, reasons, differences}:
    - 'pass'   state line names the tapped preset AND prefs hold its intended vector.
    - 'report' state line is Custom and every intent/observation difference
               is explained by the app's own clamp/reject log: the platform's answer
               differs from intent legitimately. Reported, not failed.
    - 'fail'   intent was not persisted, the state line is missing/Unknown (stale can
               never pass as current), or differences have no platform explanation.
    """
    reasons = []
    enabled, levels = parse_prefs(prefs_xml)
    intended = PRESET_VECTORS[target]
    if levels != intended:
        reasons.append('persisted intent ' + repr(levels) + ' != target vector '
                       + repr(intended))
        return {'verdict': 'fail', 'reasons': reasons, 'differences': []}
    reasons.append('persisted intent matches the target vector ' + repr(intended))

    if state_line is None:
        reasons.append('no app state line found')
        return {'verdict': 'fail', 'reasons': reasons, 'differences': []}
    if state_line.startswith('Unknown'):
        reasons.append('app reported unconfirmed state: ' + repr(state_line)
                       + '; a stale read can never pass as current')
        return {'verdict': 'fail', 'reasons': reasons, 'differences': []}
    if state_line == 'Active preset: ' + target:
        reasons.append('app state line names the tapped preset')
        return {'verdict': 'pass', 'reasons': reasons, 'differences': []}
    if state_line != 'Active preset: Custom':
        reasons.append('state line names a different preset ' + repr(state_line)
                       + ': the platform answer matched another preset exactly, which no'
                       + ' clamp of this tap\'s intent can explain')
        return {'verdict': 'fail', 'reasons': reasons, 'differences': []}

    clamps = clamp_reports(logcat_text)
    explained = [{'band': c['band'], 'intended': c['intended'], 'observed': c['observed'],
                  'kind': c['kind']} for c in clamps
                 if 0 <= c['band'] < len(intended) and intended[c['band']] == c['intended']]
    if explained:
        reasons.append('state line ' + repr(state_line) + ' instead of the preset name; '
                       'every difference is explained by the platform answer: '
                       + repr(explained))
        return {'verdict': 'report', 'reasons': reasons, 'differences': explained}
    reasons.append('state line ' + repr(state_line) + ' instead of the preset name and '
                   'no clamp/reject log explains the difference')
    return {'verdict': 'fail', 'reasons': reasons, 'differences': []}
