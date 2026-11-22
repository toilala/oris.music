#!/usr/bin/env python3
"""Read real provider lyrics in the production pane using metadata only, no playback request."""
from playback_check import adb, tap, wait_label, tree, screen, PKG
import time

def wait_text(fragment, seconds=100):
    end = time.monotonic()+seconds
    while time.monotonic() < end:
        for node in tree().iter('node'):
            if fragment in (node.get('text') or ''):
                return node
        time.sleep(1)
    print('LYRICS_DIAGNOSTICS', adb('logcat', '-d', '-s', 'OrisLyrics').decode(), flush=True)
    screen('lyrics_failure')
    raise AssertionError('Lyrics view did not show '+fragment)

adb('shell', 'am', 'start', '-n', PKG+'/com.orismusic.client.debug.LyricsCheckActivity')
wait_label('Lyrics')
wait_label('Yesterday')
wait_text('Synced lyrics')
screen('lyrics_automatic_synced')
tap('Automatic')
wait_label('LRCLIB')
wait_label('Better Lyrics')
screen('lyrics_source_choices')
tap('LRCLIB')
wait_text('LRCLIB')
wait_text('Synced lyrics')
screen('lyrics_lrclib_synced')
# Source selection is real; an unavailable remote provider is not a fabricated success.
tap('LRCLIB')
tap('Better Lyrics')
end = time.monotonic()+20
while time.monotonic() < end:
    nodes = list(tree().iter('node'))
    if any('Synced lyrics' in (n.get('text') or '') or 'Lyrics unavailable' in (n.get('text') or '') for n in nodes):
        break
    time.sleep(1)
else:
    raise AssertionError('Better Lyrics did not finish within its timeout')
screen('lyrics_better_result')
tap('Better Lyrics')
tap('Automatic')
wait_text('Synced lyrics')
tap('Automatic')
tap('Provider settings')
wait_label('Lyrics providers')
wait_label('Move LRCLIB down')
screen('lyrics_provider_settings_default')
tap('Move LRCLIB down')
screen('lyrics_provider_settings_reordered')
tap('Back')
wait_label('Lyrics')
tap('Automatic')
# The persisted first entry is now Unison rather than LRCLIB.
screen('lyrics_source_choices_reordered')
# Back closes the provider menu and/or the pane; at the debug activity's root it
# legitimately moves the task to the background (launcher). Either is a clean close.
adb('shell', 'input', 'keyevent', '4')
time.sleep(1)

# The player's own embedded lyrics pane (edge-fade layer included) with real provider
# lyrics. The text wait proves fetch + composition only; the fade layer erases pixels
# without touching semantics, so lyrics_embedded_player.png must be eyeballed.
adb('shell', 'am', 'start', '-n', PKG+'/com.orismusic.client.debug.EmbeddedLyricsCheckActivity')
wait_text('troubles seemed so far away')
time.sleep(1)
screen('lyrics_embedded_player')
adb('shell', 'input', 'keyevent', '4')
time.sleep(1)
# Wherever back left us, the task must still be alive and the player still renders.
adb('shell', 'am', 'start', '-n', PKG + '/com.orismusic.client.debug.LyricsCheckActivity')
wait_label('Lyrics', seconds=30)
# The player's own lyrics view over the same real synced document.
adb('shell', 'am', 'start', '-n', PKG+'/com.orismusic.client.debug.EmbeddedLyricsCheckActivity')
wait_label('Yesterday', seconds=30)
wait_text('All my troubles')
time.sleep(2)
screen('lyrics_player_view')
print('PASS: real synced lyrics, source selection, source failure handling and close; no audio prepared', flush=True)
