#!/usr/bin/env python3
# Copyright (C) 2026 BunnyH
#
# This file is part of Mouse Scroll Fix (mousescrollfix), a Minecraft mod.
#
# Mouse Scroll Fix is free software: you can redistribute it and/or modify it under the terms
# of the GNU Lesser General Public License as published by the Free Software Foundation,
# version 3 of the License.
#
# Mouse Scroll Fix is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
# without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the GNU Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License along with
# Mouse Scroll Fix. If not, see <https://www.gnu.org/licenses/>.

"""Press keys on a virtual uinput keyboard.

Needed because XTest key events (xdotool key) never reach Wayland-native clients - the game only
sees input that arrives through a real input device like this one.

Usage: keys.py <key> [<key> ...] [--hold MS] [--delay MS]
Keys: esc, e, t, f3, f11, space, enter, 1..9, a..z
"""
import ctypes
import fcntl
import os
import struct
import sys
import time

UI_SET_EVBIT = 0x40045564
UI_SET_KEYBIT = 0x40045565
UI_DEV_SETUP = 0x405C5503
UI_DEV_CREATE = 0x5501
UI_DEV_DESTROY = 0x5502

EV_SYN, EV_KEY = 0, 1

KEYS = {"esc": 1, "1": 2, "2": 3, "3": 4, "4": 5, "5": 6, "6": 7, "7": 8, "8": 9, "9": 10,
        "e": 18, "t": 20, "space": 57, "f3": 61, "f11": 87, "enter": 28}


class InputId(ctypes.Structure):
    _fields_ = [("bustype", ctypes.c_uint16), ("vendor", ctypes.c_uint16),
                ("product", ctypes.c_uint16), ("version", ctypes.c_uint16)]


class UinputSetup(ctypes.Structure):
    _fields_ = [("id", InputId), ("name", ctypes.c_char * 80), ("ff_effects_max", ctypes.c_uint32)]


def main():
    args = sys.argv[1:]
    hold_ms, delay_ms = 40, 250
    names = []
    i = 0
    while i < len(args):
        if args[i] == "--hold":
            hold_ms = int(args[i + 1]); i += 2
        elif args[i] == "--delay":
            delay_ms = int(args[i + 1]); i += 2
        else:
            names.append(args[i]); i += 1

    codes = []
    for name in names:
        name = name.lower()
        for part in name.split("+"):          # crude modifier syntax: shift+e etc. (unused for now)
            if part not in KEYS:
                raise SystemExit(f"unknown key '{part}'")
            codes.append(KEYS[part])

    fd = os.open("/dev/uinput", os.O_WRONLY | os.O_NONBLOCK)
    fcntl.ioctl(fd, UI_SET_EVBIT, EV_SYN)
    fcntl.ioctl(fd, UI_SET_EVBIT, EV_KEY)
    for code in sorted(set(codes)):
        fcntl.ioctl(fd, UI_SET_KEYBIT, code)
    setup = UinputSetup()
    setup.id = InputId(bustype=0x03, vendor=0x1d6b, product=0x5678, version=1)
    setup.name = b"uinput-key-probe"
    setup.ff_effects_max = 0
    fcntl.ioctl(fd, UI_DEV_SETUP, setup)
    fcntl.ioctl(fd, UI_DEV_CREATE)
    try:
        time.sleep(1.2)
        for code in codes:
            os.write(fd, struct.pack("llHHi", 0, 0, EV_KEY, code, 1))
            os.write(fd, struct.pack("llHHi", 0, 0, EV_SYN, 0, 0))
            time.sleep(hold_ms / 1000.0)
            os.write(fd, struct.pack("llHHi", 0, 0, EV_KEY, code, 0))
            os.write(fd, struct.pack("llHHi", 0, 0, EV_SYN, 0, 0))
            time.sleep(delay_ms / 1000.0)
    finally:
        fcntl.ioctl(fd, UI_DEV_DESTROY)
        os.close(fd)
    print("pressed " + " ".join(names))


if __name__ == "__main__":
    main()
