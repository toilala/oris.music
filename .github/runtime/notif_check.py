#!/usr/bin/env python3
"""Verifies the media notification is really posted and the service is foreground while playing,
and that playback survives a short stint in the background. Uses the debug local tone, no streaming."""
from playback_check import adb, wait_label, PKG, OUT
import glob
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
from playback_check import screen
screen('notification_shade')
adb('shell', 'cmd', 'statusbar', 'collapse')
time.sleep(1)

# Late capture: the full media-style notification can land a few seconds after playback
# starts, so capture again before judging what the shade really shows during steady play.
time.sleep(10)
open(OUT + '/dumpsys-notification-late.txt', 'w').write(adb('shell', 'dumpsys', 'notification').decode())
adb('shell', 'cmd', 'statusbar', 'expand-notifications')
time.sleep(2)
screen('notification_shade_late')
adb('shell', 'cmd', 'statusbar', 'collapse')
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
