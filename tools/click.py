#!/usr/bin/env python3
"""Left-click at the current pointer position using a real uinput mouse.

Needed because Minecraft runs as a native Wayland client here, so XTest events
(xdotool click) are never delivered to it -- only real kernel input devices are.
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
UI_SET_PROPBIT = 0x4004556E
UI_DEV_SETUP = 0x405C5503
UI_DEV_CREATE = 0x5501
UI_DEV_DESTROY = 0x5502

EV_SYN, EV_KEY, EV_REL = 0, 1, 2
REL_X, REL_Y, REL_WHEEL, REL_HWHEEL, REL_WHEEL_HI_RES, REL_HWHEEL_HI_RES = 0, 1, 8, 6, 11, 12
BTN_LEFT, BTN_RIGHT, BTN_MIDDLE, BTN_SIDE = 0x110, 0x111, 0x112, 0x113


class InputId(ctypes.Structure):
    _fields_ = [("bustype", ctypes.c_uint16), ("vendor", ctypes.c_uint16),
                ("product", ctypes.c_uint16), ("version", ctypes.c_uint16)]


class UinputSetup(ctypes.Structure):
    _fields_ = [("id", InputId), ("name", ctypes.c_char * 80), ("ff_effects_max", ctypes.c_uint32)]


def ev(fd, etype, code, value):
    os.write(fd, struct.pack("llHHi", 0, 0, etype, code, value))


def main():
    clicks = int(sys.argv[1]) if len(sys.argv) > 1 else 1
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
    s.id = InputId(bustype=0x03, vendor=0x1234, product=0x9999, version=1)
    s.name = b"uinput-click-probe"
    s.ff_effects_max = 0
    fcntl.ioctl(fd, UI_DEV_SETUP, s)
    fcntl.ioctl(fd, UI_DEV_CREATE)
    try:
        time.sleep(1.5)
        for _ in range(clicks):
            ev(fd, EV_KEY, BTN_LEFT, 1)
            ev(fd, EV_SYN, 0, 0)
            time.sleep(0.06)
            ev(fd, EV_KEY, BTN_LEFT, 0)
            ev(fd, EV_SYN, 0, 0)
            time.sleep(0.35)
    finally:
        fcntl.ioctl(fd, UI_DEV_DESTROY)
        os.close(fd)
    print(f"sent {clicks} left click(s)")


if __name__ == "__main__":
    main()
