#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GENERATOR: writes the FreeType reference renders awake:compose:ui-testing compares small text against.
# Kinds are defined in tools/README.md. Only a GATE can fail a build; visual tooling tests cover
# the helper itself.
"""Renders the small-text reference images that `TextReferenceMetricsTest` holds Awake's text against.

The reference is FreeType, pinned (freetype-py 2.5.1, which bundles FreeType 2.13.2; see
requirements-reference.txt), run with the settings FreeType documents for crisp text blended in linear
light:

- light hinting (`FT_LOAD_TARGET_LIGHT`): FreeType's auto-hinter snaps horizontal edges, the x-height,
  cap height and baseline, to the pixel grid and leaves glyph shapes and advances alone horizontally;
- stem darkening on (the auto-hinter's `no-stem-darkening` property set to false): small sizes are
  emboldened slightly, so thin strokes keep their weight once blending stops thickening them;
- 8-bit grayscale anti-aliasing (`FT_RENDER_MODE_LIGHT`), the glyph's coverage;
- coverage composited with gamma 1.8: both colours raised to the power 1.8, mixed by coverage, and raised
  back. FreeType documents stem darkening as designed for linear alpha blending with gamma correction, and
  names 1.8 as the gamma to use with it; it is also the classic text gamma. Full sRGB linearisation (about
  2.2) on top of darkening thins dark text on a light ground further than either was tuned for.

Pen positions follow Awake's own layout so the two images line up pixel for pixel: each glyph's advance is
the larger of its `hmtx` advance and its ink's right edge (`PackedUiFont.advanceFor`), the pen starts at
x 0, a glyph's bitmap sits at the pen rounded to a whole pixel, and the baseline is `ascender / unitsPerEm`
times the size, rounded. Only the glyph shapes, their hinting and the blending are FreeType's.

The cases are `CrispTextEvidenceTest`'s small ones: 11, 12 and 14 px, Roboto Regular (the default family)
and Roboto Medium (loaded from its file, as a project font is), dark on light and light on dark, the same
string, image size and colours. PNGs are written by hand with zlib, so the bytes depend only on this file,
FreeType and Python's zlib; a re-run writes the same bytes.

With `--glyph-pack DIR` it instead writes, for every Roboto weight and every size from 8 to 24 px, each
atlas character's FreeType bitmap (same hinting and darkening) to `DIR/roboto-<weight>-<size>.bin`. Nothing
commits those: `VisualBaseline` reads them when `AWAKE_TEXT_REFERENCE_GLYPHS` names DIR, and writes each
component baseline's text again in FreeType's glyphs, as a reference picture for a reviewer.

Usage:
    python3 -m pip install -r tools/fonts-tooling/requirements-reference.txt
    python3 tools/fonts-tooling/reference_render.py
    python3 tools/fonts-tooling/reference_render.py --glyph-pack build/text-reference-glyphs
"""

from __future__ import annotations

import ctypes
import struct
import sys
import zlib
from pathlib import Path

import freetype

REPO = Path(__file__).resolve().parent.parent.parent
FONT_DIR = REPO / "awake/ui/font-atlas-generator/fonts"
OUT_DIR = REPO / "awake/compose/ui-testing/src/desktopTest/resources/text-reference"

FREETYPE_VERSION = (2, 13, 2)
LABEL = "Mesh Terrain Texture Illi 0123"
WIDTH = 240
SIZES = (11, 12, 14)
FONTS = {"roboto": "Roboto-Regular.ttf", "ttf": "Roboto-Medium.ttf"}
DARK = (0.09, 0.09, 0.11)
LIGHT = (0.96, 0.96, 0.97)


TEXT_GAMMA = 1.8


def decode(value: float) -> float:
    return value**TEXT_GAMMA


def encode(value: float) -> float:
    return value ** (1 / TEXT_GAMMA)


def to_byte(value: float) -> int:
    """How Awake turns a colour channel into a byte (`(c * 255).toInt()`), so grounds and solid ink match exactly.

    The 1e-9 keeps a channel that a decode and re-encode returns a hair under its value from dropping a level.
    """
    return max(0, min(255, int(value * 255 + 1e-9)))


def enable_stem_darkening() -> None:
    value = ctypes.c_ubyte(0)  # FT_Bool false: darkening is on.
    error = freetype.FT_Property_Set(
        freetype.get_handle(), b"autofitter", b"no-stem-darkening", ctypes.byref(value)
    )
    if error:
        raise SystemExit(f"FT_Property_Set failed: {error}")


def advance_px(face: freetype.Face, char: str, size: int) -> float:
    """Awake's advance: the `hmtx` advance, or the ink's right edge when that is wider."""
    face.load_char(char, freetype.FT_LOAD_NO_SCALE | freetype.FT_LOAD_NO_HINTING)
    metrics = face.glyph.metrics
    advance = metrics.horiAdvance
    right = metrics.horiBearingX + metrics.width if metrics.width > 0 else 0
    return max(advance, right) * size / face.units_per_EM


def render(face: freetype.Face, size: int, text_colour, ground) -> list[list[tuple[int, int, int]]]:
    height = int(size * 1.6) + 8
    face.set_pixel_sizes(0, size)
    baseline = int(face.ascender * size / face.units_per_EM + 0.5)
    text_linear = [decode(c) for c in text_colour]
    ground_linear = [decode(c) for c in ground]
    ground_bytes = tuple(to_byte(c) for c in ground)
    coverage = [[0] * WIDTH for _ in range(height)]
    pen = 0.0
    for char in LABEL:
        if char != " ":
            face.load_char(char, freetype.FT_LOAD_TARGET_LIGHT)
            face.glyph.render(freetype.FT_RENDER_MODE_LIGHT)
            bitmap = face.glyph.bitmap
            left = int(pen + 0.5) + face.glyph.bitmap_left
            top = baseline - face.glyph.bitmap_top
            for row in range(bitmap.rows):
                for column in range(bitmap.width):
                    x, y = left + column, top + row
                    if 0 <= x < WIDTH and 0 <= y < height:
                        value = bitmap.buffer[row * bitmap.pitch + column]
                        coverage[y][x] = min(255, coverage[y][x] + value)
        pen += advance_px(face, char, size)
    image = []
    for y in range(height):
        line = []
        for x in range(WIDTH):
            alpha = coverage[y][x] / 255
            if alpha == 0:
                line.append(ground_bytes)
                continue
            line.append(
                tuple(to_byte(encode(g + (t - g) * alpha)) for t, g in zip(text_linear, ground_linear))
            )
        image.append(line)
    return image


def write_png(path: Path, image: list[list[tuple[int, int, int]]]) -> None:
    height, width = len(image), len(image[0])
    raw = b"".join(b"\x00" + bytes(channel for pixel in row for channel in pixel) for row in image)

    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


# Every Roboto weight, by the CSS weight `FontWeight.value` gives it.
WEIGHTS = {100: "Thin", 300: "Light", 400: "Regular", 500: "Medium", 600: "SemiBold", 700: "Bold", 900: "Black"}
ATLAS_CHARS = "".join(chr(code) for code in range(32, 127)) + "•·…–—×−"


def write_glyph_pack(out_dir: Path) -> None:
    """Each glyph as big-endian code point (i32), left and top from pen and baseline (i16), width and height (u16), coverage bytes."""
    out_dir.mkdir(parents=True, exist_ok=True)
    for weight, style in WEIGHTS.items():
        face = freetype.Face(str(FONT_DIR / f"Roboto-{style}.ttf"))
        for size in range(8, 25):
            face.set_pixel_sizes(0, size)
            records = []
            for char in ATLAS_CHARS:
                face.load_char(char, freetype.FT_LOAD_TARGET_LIGHT)
                face.glyph.render(freetype.FT_RENDER_MODE_LIGHT)
                bitmap = face.glyph.bitmap
                pixels = bytes(bitmap.buffer[row * bitmap.pitch + column] for row in range(bitmap.rows) for column in range(bitmap.width))
                records.append(struct.pack(">ihhHH", ord(char), face.glyph.bitmap_left, face.glyph.bitmap_top, bitmap.width, bitmap.rows) + pixels)
            (out_dir / f"roboto-{weight}-{size}.bin").write_bytes(b"".join(records))
    print(f"Wrote glyph packs to {out_dir}")


def main() -> int:
    if freetype.version() != FREETYPE_VERSION:
        raise SystemExit(f"FreeType {freetype.version()} is not the pinned {FREETYPE_VERSION}; install requirements-reference.txt")
    enable_stem_darkening()
    if len(sys.argv) == 3 and sys.argv[1] == "--glyph-pack":
        write_glyph_pack(Path(sys.argv[2]).resolve())
        return 0
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, file in FONTS.items():
        face = freetype.Face(str(FONT_DIR / file))
        for size in SIZES:
            for case, (text_colour, ground) in {"dark-on-light": (DARK, LIGHT), "light-on-dark": (LIGHT, DARK)}.items():
                out = OUT_DIR / f"{name}-{size}-{case}.png"
                write_png(out, render(face, size, text_colour, ground))
                print(f"Wrote {out.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
