#!/usr/bin/env python3
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
# Patch the stock hall.ko (folio cover Hall sensor) to report the cover as
# EV_SW/SW_LID instead of EV_KEY scan codes 252/253. See
# docs/hall-ko-lid-switch.md for the analysis.
#
# Usage: tools/patch_hall_ko.py <stock hall.ko> <output hall.ko>
#
# The input must be the stock module (sha256 below). Every patched word is
# checked against its stock encoding first, so a different build of the
# module is rejected instead of being corrupted.

import hashlib
import struct
import sys

STOCK_SHA256 = 'd408d33835673cebd37e7ed67885310582839b717c0ce9c97d15092bb0593c6c'
PATCHED_SHA256 = 'aefb56828a7f364d26a79934857f1da3a6ca18478ea5ab45d6484b5fe2bce7f9'

# File offset of .text in the stock module (llvm-readelf -S).
TEXT_OFFSET = 0x11c8

# (.text address, stock instruction, patched instruction, note)
PATCHES = (
    # hall_pdrv_probe: input_set_capability(dev, EV_KEY, 252) -> (dev, EV_SW, SW_LID)
    (0x1a4, 0x52800021, 0x528000a1, 'mov w1, #1 -> mov w1, #5 (EV_SW)'),
    (0x1a8, 0x52801f82, 0x52800002, 'mov w2, #252 -> mov w2, #0 (SW_LID)'),
    # hall_pdrv_probe: dev->evbit[0] |= BIT(EV_KEY) -> BIT(EV_SW)
    (0x1b8, 0xb27f0108, 0xb27b0108, 'orr x8, x8, #0x2 -> orr x8, x8, #0x20'),
    # hall_pdrv_probe: input_set_capability(dev, EV_KEY, 253) -> (dev, EV_SW, SW_LID)
    (0x1c8, 0x52800021, 0x528000a1, 'mov w1, #1 -> mov w1, #5 (EV_SW)'),
    (0x1cc, 0x52801fa2, 0x52800002, 'mov w2, #253 -> mov w2, #0 (SW_LID)'),
    # hall_eint_work, GPIO low (closed): key 252 down -> SW_LID 1
    (0x340, 0x52800021, 0x528000a1, 'mov w1, #1 -> mov w1, #5 (EV_SW)'),
    (0x344, 0x52801f82, 0x52800002, 'mov w2, #252 -> mov w2, #0 (SW_LID)'),
    # hall_eint_work, GPIO low (closed): key 252 up -> SW_LID 1 again (dropped by
    # the input core as unchanged)
    (0x374, 0x52800021, 0x528000a1, 'mov w1, #1 -> mov w1, #5 (EV_SW)'),
    (0x378, 0x52801f82, 0x52800002, 'mov w2, #252 -> mov w2, #0 (SW_LID)'),
    (0x37c, 0x2a1f03e3, 0x52800023, 'mov w3, wzr -> mov w3, #1'),
    # hall_eint_work, GPIO high (open): key 253 down -> SW_LID 0
    (0x3d0, 0x52800021, 0x528000a1, 'mov w1, #1 -> mov w1, #5 (EV_SW)'),
    (0x3d4, 0x52801fa2, 0x52800002, 'mov w2, #253 -> mov w2, #0 (SW_LID)'),
    (0x3d8, 0x52800023, 0x2a1f03e3, 'mov w3, #1 -> mov w3, wzr'),
    # hall_eint_work, GPIO high (open): key 253 up -> SW_LID 0 again (dropped)
    (0x400, 0x52800021, 0x528000a1, 'mov w1, #1 -> mov w1, #5 (EV_SW)'),
    (0x404, 0x52801fa2, 0x52800002, 'mov w2, #253 -> mov w2, #0 (SW_LID)'),
)


def main():
    if len(sys.argv) != 3:
        sys.exit(f'usage: {sys.argv[0]} <stock hall.ko> <output hall.ko>')
    data = bytearray(open(sys.argv[1], 'rb').read())
    sha = hashlib.sha256(data).hexdigest()
    if sha == PATCHED_SHA256:
        sys.exit('input is already patched')
    if sha != STOCK_SHA256:
        sys.exit(f'unexpected input sha256 {sha}')
    for addr, stock, patched, note in PATCHES:
        off = TEXT_OFFSET + addr
        (word,) = struct.unpack_from('<I', data, off)
        if word != stock:
            sys.exit(f'.text+{addr:#x}: found {word:#010x}, expected {stock:#010x}')
        struct.pack_into('<I', data, off, patched)
        print(f'.text+{addr:#05x}: {note}')
    sha = hashlib.sha256(data).hexdigest()
    if sha != PATCHED_SHA256:
        sys.exit(f'patched sha256 {sha} does not match {PATCHED_SHA256}')
    open(sys.argv[2], 'wb').write(data)
    print(f'wrote {sys.argv[2]} (sha256 {sha})')


if __name__ == '__main__':
    main()
