#!/usr/bin/env python3
"""While a game runs, watch for the Home button and ask RetroArch to quit.

Home on a controller, Select and Start held together for a second, or the Home key on a
keyboard. RetroArch saves the game as it quits (Shelf turns on its auto-save), and the
session script takes it from there.
"""
import os
import socket
import subprocess
import sys
import time

try:
    import evdev
    from evdev import ecodes as E
except ImportError:
    sys.exit("homewatch: python3-evdev is missing (sudo apt install python3-evdev)")

COMBO_SECONDS = 1.0


def quit_game():
    print("homewatch: Home pressed, asking RetroArch to quit", flush=True)
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.sendto(b"QUIT", ("127.0.0.1", 55355))
    # if RetroArch didn't listen (network commands off), ask it to close the way a shutdown would;
    # it still saves on the way out. The session script stops this watcher as soon as RetroArch exits.
    time.sleep(4)
    subprocess.run(["pkill", "-TERM", "-x", "retroarch"])
    time.sleep(4)


def input_devices():
    found = {}
    for path in evdev.list_devices():
        try:
            dev = evdev.InputDevice(path)
            keys = dev.capabilities().get(E.EV_KEY, [])
            if E.BTN_MODE in keys or E.BTN_START in keys or E.KEY_HOME in keys:
                found[path] = dev
            else:
                dev.close()
        except OSError:
            pass
    return found


def main():
    devices, last_scan, held, combo_since = input_devices(), time.time(), set(), None
    while True:
        if time.time() - last_scan > 2:   # controllers can be plugged in or switched on mid-game
            for path, dev in input_devices().items():
                if path not in devices:
                    devices[path] = dev
                else:
                    dev.close()
            last_scan = time.time()
        for path, dev in list(devices.items()):
            try:
                while True:
                    event = dev.read_one()
                    if event is None:
                        break
                    if event.type != E.EV_KEY:
                        continue
                    if event.value == 1:
                        held.add(event.code)
                        if event.code in (E.BTN_MODE, E.KEY_HOME):
                            return quit_game()
                    elif event.value == 0:
                        held.discard(event.code)
            except (OSError, BlockingIOError):
                if not os.path.exists(path):
                    devices.pop(path, None)
        if E.BTN_SELECT in held and E.BTN_START in held:
            combo_since = combo_since or time.time()
            if time.time() - combo_since >= COMBO_SECONDS:
                return quit_game()
        else:
            combo_since = None
        time.sleep(0.02)


if __name__ == "__main__":
    main()
