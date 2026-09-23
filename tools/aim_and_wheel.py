#!/usr/bin/env python3
"""Aim the pointer at a target X window, verify it landed, then emit wheel notches.

Usage: aim_and_wheel.py <window-name-substring> <notches> [--device-name N] [--vendor V] [--product P]
"""
import ctypes
import fcntl
import os
import struct
import subprocess
import sys
import time

UI_SET_EVBIT = 0x40045564
UI_SET_KEYBIT = 0x40045565
UI_SET_RELBIT = 0x40045566
UI_DEV_SETUP = 0x405C5503
UI_SET_PROPBIT = 0x4004556E
UI_DEV_CREATE = 0x5501
UI_DEV_DESTROY = 0x5502

EV_SYN, EV_KEY, EV_REL = 0, 1, 2
REL_WHEEL, REL_HWHEEL, REL_WHEEL_HI_RES, REL_HWHEEL_HI_RES = 8, 6, 11, 12
REL_X, REL_Y = 0, 1
BTN_LEFT, BTN_RIGHT, BTN_MIDDLE, BTN_SIDE = 0x110, 0x111, 0x112, 0x113


class InputId(ctypes.Structure):
    _fields_ = [("bustype", ctypes.c_uint16), ("vendor", ctypes.c_uint16),
                ("product", ctypes.c_uint16), ("version", ctypes.c_uint16)]


class UinputSetup(ctypes.Structure):
    _fields_ = [("id", InputId), ("name", ctypes.c_char * 80), ("ff_effects_max", ctypes.c_uint32)]


def sh(*args):
    return subprocess.run(args, capture_output=True, text=True).stdout.strip()


def find_window(substr):
    out = sh("xdotool", "search", "--name", substr)
    return int(out.splitlines()[0]) if out else None


def window_at(x, y):
    sh("xdotool", "mousemove", str(x), str(y))
    time.sleep(0.08)
    loc = sh("xdotool", "getmouselocation")
    if "window:" not in loc:
        return None
    return int(loc.split("window:")[1].split()[0])


def aim(target):
    """Scan a grid for a point whose window-under-pointer is `target`."""
    for y in range(200, 1300, 60):
        for x in range(200, 2200, 60):
            if window_at(x, y) == target:
                return x, y
    return None


def emit_device(name, vendor, product, notches, delay_ms):
    fd = os.open("/dev/uinput", os.O_WRONLY | os.O_NONBLOCK)
    fcntl.ioctl(fd, UI_SET_EVBIT, EV_SYN)
    fcntl.ioctl(fd, UI_SET_EVBIT, EV_KEY)
    fcntl.ioctl(fd, UI_SET_EVBIT, EV_REL)
    for b in (BTN_LEFT, BTN_RIGHT, BTN_MIDDLE, BTN_SIDE):
        fcntl.ioctl(fd, UI_SET_KEYBIT, b)
    for r in (REL_X, REL_Y, REL_WHEEL, REL_HWHEEL, REL_WHEEL_HI_RES, REL_HWHEEL_HI_RES):
        fcntl.ioctl(fd, UI_SET_RELBIT, r)
    fcntl.ioctl(fd, UI_SET_PROPBIT, 0x00)
    s = UinputSetup()
    s.id = InputId(bustype=0x03, vendor=vendor, product=product, version=1)
    s.name = name.encode()
    s.ff_effects_max = 0
    fcntl.ioctl(fd, UI_DEV_SETUP, s)
    fcntl.ioctl(fd, UI_DEV_CREATE)
    try:
        time.sleep(1.5)
        for _ in range(notches):
            os.write(fd, struct.pack("llHHi", 0, 0, EV_REL, REL_WHEEL_HI_RES, 120))
            os.write(fd, struct.pack("llHHi", 0, 0, EV_REL, REL_WHEEL, 1))
            os.write(fd, struct.pack("llHHi", 0, 0, EV_SYN, 0, 0))
            time.sleep(delay_ms / 1000.0)
    finally:
        fcntl.ioctl(fd, UI_DEV_DESTROY)
        os.close(fd)


def main():
    target_name = sys.argv[1]
    notches = int(sys.argv[2])
    dev_name = "uinput-wheel-probe"
    vendor, product = 0x1234, 0x5678
    delay_ms = 200
    args = sys.argv[3:]
    i = 0
    while i < len(args):
        if args[i] == "--device-name":
            dev_name = args[i + 1]; i += 2
        elif args[i] == "--vendor":
            vendor = int(args[i + 1], 0); i += 2
        elif args[i] == "--product":
            product = int(args[i + 1], 0); i += 2
        elif args[i] == "--delay":
            delay_ms = int(args[i + 1]); i += 2
        else:
            raise SystemExit("bad arg " + args[i])

    win = find_window(target_name)
    if win is None:
        raise SystemExit(f"no window matching '{target_name}'")
    sh("xdotool", "windowraise", str(win))
    time.sleep(0.3)

    spot = aim(win)
    if spot is None:
        raise SystemExit(f"could not place pointer over window {win}")
    print(f"target window {win} ('{target_name}'), pointer aimed at {spot}, verified over target")

    print(f"emitting {notches} notches as device '{dev_name}' "
          f"(vendor=0x{vendor:04x} product=0x{product:04x}), {delay_ms} ms apart")
    emit_device(dev_name, vendor, product, notches, delay_ms)
    print("done")


if __name__ == "__main__":
    main()
