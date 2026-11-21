#!/usr/bin/env python3
"""Verifies the media notification is really posted and the service is foreground while playing,
and that playback survives a short stint in the background. Uses the debug local tone, no streaming."""
from playback_check import adb, wait_label, PKG, OUT
import glob
import subprocess
import re
import time

COMPONENT = PKG + '/com.orismusic.client.debug.LocalPlaybackCheckActivity'

def services():
    return adb('shell', 'dumpsys', 'activity', 'services', PKG).decode()

def session_states():
    return re.findall(r'state=(\d)', adb('shell', 'dumpsys', 'media_session').decode())

adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
adb('install', '-r', glob.glob('app/build/outputs/apk/foss/debug/*.apk')[0])
adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'am', 'start', '-n', COMPONENT, '-f', '0x10008000')
wait_label('Local audio playing', seconds=30)

notifications = adb('shell', 'dumpsys', 'notification').decode()
open(OUT + '/dumpsys-notification.txt', 'w').write(notifications)
svc = services()
open(OUT + '/dumpsys-services.txt', 'w').write(svc)
active = [line for line in notifications.splitlines() if 'NotificationRecord(' in line and PKG in line]
assert active, 'no Oris notification record while playing'
assert 'isForeground=true' in svc, 'media service not foreground while playing'
print('NOTIF: notification posted and service foreground while playing', flush=True)

# Visual proof of the media notification design: open the shade and capture it.
adb('shell', 'cmd', 'statusbar', 'expand-notifications')
time.sleep(2)
from playback_check import screen, tap
screen('notification_shade')
adb('shell', 'cmd', 'statusbar', 'collapse')
time.sleep(1)

# Late capture: the full media-style notification can land a few seconds after playback
# starts, so capture again before judging what the shade really shows during steady play.
time.sleep(10)
late_dump = adb('shell', 'dumpsys', 'notification').decode()
open(OUT + '/dumpsys-notification-late.txt', 'w').write(late_dump)
# The steady-state notification must be a real media notification: transport category and a
# live session token. dumpsys prints extra TYPES, never values, so the title itself is
# judged on the rendered shade below.
assert 'category=transport' in late_dump, \
    'steady-state notification is not a media notification (no transport category)'
assert 'android.mediaSession=Token' in late_dump, \
    'steady-state notification has no live media session token'
media_sessions = adb('shell', 'dumpsys', 'media_session').decode()
open(OUT + '/dumpsys-media-session.txt', 'w').write(media_sessions)
assert 'Runtime local tone' in media_sessions, 'media session is not carrying the track title'
assert 'Oris test' in media_sessions, 'media session is not carrying the track artist'
# The shade chronometer ticks while playing, so uiautomator never reaches idle; pause,
# read the rendered shade, then resume before the survival checks below.
adb('shell', 'input', 'keyevent', '127')
time.sleep(2)
adb('shell', 'cmd', 'statusbar', 'expand-notifications')
time.sleep(2)
shade_xml = ''
for _ in range(3):
    try:
        adb('shell', 'uiautomator', 'dump', '/data/local/tmp/oris_shade.xml')
        shade_xml = adb('shell', 'cat', '/data/local/tmp/oris_shade.xml').decode()
        if shade_xml.strip():
            break
    except subprocess.CalledProcessError:
        time.sleep(3)
open(OUT + '/uiautomator-shade.xml', 'w').write(shade_xml)
screen('notification_shade_late')
adb('shell', 'cmd', 'statusbar', 'collapse')
time.sleep(1)
adb('shell', 'input', 'keyevent', '126')
time.sleep(2)
assert '3' in session_states(), 'playback did not resume after the shade read'
assert shade_xml.strip(), 'could not dump the expanded shade UI'
assert 'Runtime local tone' in shade_xml, 'steady-state shade does not render the track title'
assert 'Oris test' in shade_xml, 'steady-state shade does not render the track artist'
print('NOTIF: steady-state shade renders the track title and artist', flush=True)

# Photograph the player screen with the tone playing: real title, artist, seek bar
# and controls, so layout changes are judged on pixels, not intentions.
adb('shell', 'am', 'start', '-n', PKG + '/com.orismusic.client.MainActivity')
wait_label('Home', seconds=60)
tap('Library')
tap('Queue')
wait_label('Open player', seconds=30)
tap('Open player')
wait_label('Lyrics', seconds=90)
time.sleep(1)
screen('player_playing_layout')
adb('shell', 'input', 'keyevent', '4')
time.sleep(1)

adb('shell', 'input', 'keyevent', '3')
for i in range(6):
    time.sleep(10)
    alive = ''
    try:
        alive = adb('shell', 'pidof', PKG).decode().strip()
    except Exception:
        pass
    print(f'NOTIF: {(i+1)*10}s alive={alive} states={session_states()}', flush=True)
assert '3' in session_states(), 'playback stopped within 60s in background'
assert 'isForeground=true' in services(), 'service dropped foreground within 60s in background'

# Screen off: the wake lock is the only thing keeping the CPU alive for the stream now.
adb('shell', 'input', 'keyevent', '26')
for i in range(6):
    time.sleep(10)
    alive = ''
    try:
        alive = adb('shell', 'pidof', PKG).decode().strip()
    except Exception:
        pass
    print(f'NOTIF: screen-off {(i+1)*10}s alive={alive} states={session_states()}', flush=True)
adb('shell', 'input', 'keyevent', '26')
adb('shell', 'input', 'keyevent', '82')
assert '3' in session_states(), 'playback stopped within 60s with the screen off'
assert 'isForeground=true' in services(), 'service dropped foreground with the screen off'
adb('shell', 'am', 'force-stop', PKG)
print('PASS: media notification posted, service foreground, playback survived 60s in background and 60s with the screen off', flush=True)
