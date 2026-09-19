#!/usr/bin/env python3
"""Create a virtual wheel mouse via /dev/uinput and emit N physical notches.

Usage: wheel.py <notches> [--name NAME] [--vendor N] [--product N] [--delay MS]

Events emitted per notch (matching a real high-resolution wheel):
    REL_WHEEL_HI_RES +120 (or -120)  and  REL_WHEEL +1 (or -1)
"""
import ctypes
import fcntl
import os
import struct
import sys
import time

UI_SET_EVBIT = 0x40045564
UI_SET_KEYBIT = 0x40045565
UI_SET_RELBIT = 0x40045566
UI_DEV_SETUP = 0x405C5503
UI_DEV_CREATE = 0x5501
UI_DEV_DESTROY = 0x5502

EV_SYN, EV_KEY, EV_REL = 0, 1, 2
REL_WHEEL, REL_HWHEEL, REL_WHEEL_HI_RES, REL_HWHEEL_HI_RES = 8, 6, 11, 12
BTN_LEFT, BTN_MIDDLE, BTN_RIGHT = 0x110, 0x112, 0x111


class InputId(ctypes.Structure):
    _fields_ = [
        ("bustype", ctypes.c_uint16),
        ("vendor", ctypes.c_uint16),
        ("product", ctypes.c_uint16),
        ("version", ctypes.c_uint16),
    ]


class UinputSetup(ctypes.Structure):
    _fields_ = [
        ("id", InputId),
        ("name", ctypes.c_char * 80),
        ("ff_effects_max", ctypes.c_uint32),
    ]


def emit(fd, etype, code, value):
    os.write(fd, struct.pack("llHHi", 0, 0, etype, code, value))


def main():
    args = sys.argv[1:]
    notches = int(args[0])
    name = "uinput-wheel-probe"
    vendor, product = 0x1234, 0x5678
    delay_ms = 120
    i = 1
    while i < len(args):
        if args[i] == "--name":
            name = args[i + 1]
            i += 2
        elif args[i] == "--vendor":
            vendor = int(args[i + 1], 0)
            i += 2
        elif args[i] == "--product":
            product = int(args[i + 1], 0)
            i += 2
        elif args[i] == "--delay":
            delay_ms = int(args[i + 1])
            i += 2
        else:
            raise SystemExit(f"unknown arg {args[i]}")

    fd = os.open("/dev/uinput", os.O_WRONLY | os.O_NONBLOCK)

    fcntl.ioctl(fd, UI_SET_EVBIT, EV_SYN)
    fcntl.ioctl(fd, UI_SET_EVBIT, EV_KEY)
    fcntl.ioctl(fd, UI_SET_EVBIT, EV_REL)
    for b in (BTN_LEFT, BTN_MIDDLE, BTN_RIGHT):
        fcntl.ioctl(fd, UI_SET_KEYBIT, b)
    for r in (REL_WHEEL, REL_HWHEEL, REL_WHEEL_HI_RES, REL_HWHEEL_HI_RES):
        fcntl.ioctl(fd, UI_SET_RELBIT, r)

    setup = UinputSetup()
    setup.id = InputId(bustype=0x03, vendor=vendor, product=product, version=1)
    setup.name = name.encode()
    setup.ff_effects_max = 0
    fcntl.ioctl(fd, UI_DEV_SETUP, setup)
    fcntl.ioctl(fd, UI_DEV_CREATE)

    try:
        time.sleep(1.2)  # let the compositor enumerate the new device
        print(f"device '{name}' created (vendor=0x{vendor:04x} product=0x{product:04x}); "
              f"emitting {notches} notches", flush=True)
        for n in range(notches):
            emit(fd, EV_REL, REL_WHEEL_HI_RES, 120)
            emit(fd, EV_REL, REL_WHEEL, 1)
            emit(fd, EV_SYN, 0, 0)
            print(f"  emitted notch {n + 1}/{notches} at t={int(time.time() * 1000)}", flush=True)
            time.sleep(delay_ms / 1000.0)
    finally:
        fcntl.ioctl(fd, UI_DEV_DESTROY)
        os.close(fd)
        print("device destroyed", flush=True)


if __name__ == "__main__":
    main()
