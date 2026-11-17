#!/usr/bin/env python3
"""Real OS task-removal checks with a debug-only local tone, never streamed audio."""
from playback_check import adb, tap, wait_label, tree, screen, PKG
import time
import glob

COMPONENT = PKG + '/com.orismusic.client.debug.LocalPlaybackCheckActivity'
MAIN = PKG + '/com.orismusic.client.MainActivity'

def services():
    return adb('shell', 'dumpsys', 'activity', 'services', PKG).decode()

def assert_running():
    assert 'ServiceRecord{' in services(), 'Playback service missing'
    sessions = adb('shell', 'dumpsys', 'media_session').decode()
    assert PKG in sessions and 'state=3' in sessions, sessions

def assert_stopped():
    end = time.monotonic() + 20
    while time.monotonic() < end:
        if 'com.orismusic.client.playback.OrisMediaService}' not in services():
            break
        time.sleep(1)
    dump = services()
    print('SERVICE_AFTER_REMOVAL', dump, flush=True)
    print('SHUTDOWN_TRACE', adb('logcat', '-d', '-s', 'OrisMediaService').decode(), flush=True)
    assert 'com.orismusic.client.playback.OrisMediaService}' not in dump, dump
    notifications = adb('shell', 'dumpsys', 'notification').decode()
    active = [line for line in notifications.splitlines() if 'NotificationRecord(' in line and PKG in line]
    assert not active, active
    jobs = adb('shell', 'dumpsys', 'jobscheduler').decode()
    assert not any(PKG in line and 'JOB #' in line for line in jobs.splitlines()), jobs
    log = adb('logcat', '-d', '-s', 'OrisMediaService').decode()
    print('SHUTDOWN_LOG', log, flush=True)
    assert 'OrisMediaService destroyed' in log, log
    assert 'Playback resources released' in log, log

def settings():
    adb('shell', 'am', 'start', '-n', MAIN)
    wait_label('OrisMusic')
    tap('Settings')
    wait_label('Playback')

def switch_node():
    return next(n for n in tree().iter('node') if n.get('checkable') == 'true')

def set_keep_playing(value):
    node = switch_node()
    if (node.get('checked') == 'true') != value:
        import re
        xy = list(map(int, re.findall(r'\d+', node.get('bounds'))))
        adb('shell', 'input', 'tap', str((xy[0]+xy[2])//2), str((xy[1]+xy[3])//2))
        time.sleep(1)
    assert (switch_node().get('checked') == 'true') == value

def local_audio():
    # Start a fresh task with this activity as its root. Removing a child activity
    # above MainActivity does not remove the app task and cannot test onTaskRemoved.
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', COMPONENT, '--activity-new-task', '--activity-clear-task')
    wait_label('Local audio playing', seconds=30)
    assert_running()

adb('wait-for-device')
adb('shell', 'input', 'keyevent', '82')
adb('install', '-r', glob.glob('app/build/outputs/apk/foss/debug/*.apk')[0])
settings()
assert switch_node().get('checked') == 'false', 'New installation must default OFF'
screen('settings_default_off')
local_audio()
screen('local_tone_playing')
# Ordinary backgrounding is not task removal.
adb('shell', 'input', 'keyevent', '3')
time.sleep(3)
assert_running()
adb('shell', 'am', 'start', '-n', COMPONENT, '--activity-single-top')
wait_label('Local audio playing')
# This calls Android's finishAndRemoveTask, not a direct service stop.
tap('Remove this task')
assert_stopped()
screen('task_removed_default_off')
print('PASS: default OFF; Home preserves real playback; removal destroys service and notification', flush=True)
settings()
set_keep_playing(True)
screen('settings_opted_in')
local_audio()
tap('Remove this task')
time.sleep(3)
assert_running()
print('PASS: explicit ON retains service and real playing session after task removal', flush=True)
settings()
assert switch_node().get('checked') == 'true', 'Opt-in must persist'
set_keep_playing(False)
screen('settings_opted_back_out')
local_audio()
tap('Remove this task')
assert_stopped()
print('PASS: opting back OFF restores full shutdown; no active jobs or playback notification', flush=True)
