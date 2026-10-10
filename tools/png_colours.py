#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Counts the distinct colours in a PNG and fails below a minimum, to tell a drawn frame from a blank one.

Reads the 8-bit, non-interlaced greyscale, RGB and RGBA PNGs that a frame capture writes, with the
standard library only, so a CI runner needs nothing installed.

    tools/png_colours.py <file.png> [minimum]
"""
import struct
import sys
import zlib

CHANNELS = {0: 1, 2: 3, 4: 2, 6: 4}


def paeth(left, up, up_left):
    estimate = left + up - up_left
    by_left, by_up, by_up_left = abs(estimate - left), abs(estimate - up), abs(estimate - up_left)
    if by_left <= by_up and by_left <= by_up_left:
        return left
    return up if by_up <= by_up_left else up_left


def rows(path):
    with open(path, "rb") as file:
        data = file.read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(f"{path} is not a PNG")
    at, chunks, header = 8, [], None
    while at < len(data):
        length, kind = struct.unpack(">I4s", data[at:at + 8])
        body = data[at + 8:at + 8 + length]
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", body)
        elif kind == b"IDAT":
            chunks.append(body)
        at += 12 + length
    width, height, depth, colour, _, _, interlace = header
    if depth != 8 or colour not in CHANNELS or interlace:
        raise SystemExit(f"{path}: only 8-bit, non-interlaced greyscale, RGB or RGBA PNGs are read")
    stride = CHANNELS[colour]
    raw = zlib.decompress(b"".join(chunks))
    line, previous, at = width * stride, bytearray(width * stride), 0
    for _ in range(height):
        kind, row = raw[at], bytearray(raw[at + 1:at + 1 + line])
        at += 1 + line
        for i in range(line):
            left = row[i - stride] if i >= stride else 0
            up_left = previous[i - stride] if i >= stride else 0
            row[i] = (row[i] + (0, left, previous[i], (left + previous[i]) // 2, paeth(left, previous[i], up_left))[kind]) & 0xFF
        yield width, stride, row
        previous = row


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    path, minimum = sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 2
    colours, size = set(), None
    for width, stride, row in rows(path):
        size = width
        colours.update(bytes(row[i:i + stride]) for i in range(0, len(row), stride))
    print(f"{path}: {size} px wide, {len(colours)} distinct colours")
    if len(colours) < minimum:
        raise SystemExit(f"{path} has {len(colours)} colour(s), fewer than {minimum}: nothing was drawn")


if __name__ == "__main__":
    main()
