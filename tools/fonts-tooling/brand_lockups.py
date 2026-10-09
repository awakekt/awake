#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Generates the AwakeKt name lockups in website/landing/brand/lockups/ (served at awakekt.com/brand/).

Each lockup is the unmodified Ember Signal mark beside its product name, set in Roboto (shipped in
awake/core/text) and converted to outlines, so the SVG renders the same everywhere. The mark keeps
its dark field on every surface; only the name changes colour between the dark and light variants.

Needs fontTools and HarfBuzz's hb-shape (`brew install harfbuzz`). Run from the repository root:

    python3 tools/fonts-tooling/brand_lockups.py
"""
import json
import re
import subprocess
from pathlib import Path

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[2]
MARK = ROOT / "samples/engine-showcase/src/commonMain/resources/brand/awake-mark.svg"
FONTS = ROOT / "awake/ui/font-atlas-generator/fonts"
OUT = ROOT / "website/landing/brand/lockups"

MARK_SIZE = 256
GAP = 64
CAP_HEIGHT = 104  # The name's capitals are about 40% of the mark, centred on it.
SURFACES = {"dark": "#ECEAE6", "light": "#16161C"}
PRODUCTS = {"awakekt": None, "awakekt-engine": "Engine", "awakekt-studio": "Studio"}


def shaped_outline(font_path: Path, text: str, x0: float, baseline: float, px: float):
    """Returns (svg path data, advance) for [text] shaped by HarfBuzz, kerning included."""
    font = TTFont(font_path)
    units = font["head"].unitsPerEm
    scale = px / units
    shaped = json.loads(subprocess.check_output(
        ["hb-shape", "--output-format=json", "--no-glyph-names", str(font_path), text],
    ))
    glyphs = font.getGlyphSet()
    pen = SVGPathPen(glyphs)
    x = 0
    for g in shaped:
        name = font.getGlyphName(g["g"])
        # Font units are y-up; SVG is y-down.
        transform = (scale, 0, 0, -scale, x0 + (x + g["dx"]) * scale, baseline - g["dy"] * scale)
        glyphs[name].draw(TransformPen(pen, transform))
        x += g["ax"]
    return pen.getCommands(), x * scale


def cap_height_px(font_path: Path, px: float) -> float:
    font = TTFont(font_path)
    return font["OS/2"].sCapHeight / font["head"].unitsPerEm * px


def mark_symbol() -> str:
    """The master mark as a nested svg, its title and description dropped (the lockup has its own)."""
    svg = MARK.read_text()
    svg = re.sub(r"<title[^>]*>.*?</title>|<desc[^>]*>.*?</desc>", "", svg, flags=re.S)
    svg = re.sub(r'\s(role|aria-labelledby)="[^"]*"', "", svg)
    return svg.replace("<svg ", f'<svg x="0" y="0" width="{MARK_SIZE}" height="{MARK_SIZE}" ', 1)


# The master is full-bleed for platform icon masks; beside a name it reads as a tile, rounded like one.
TILE_RADIUS = round(MARK_SIZE * 0.225)


def lockup(product: str | None, colour: str) -> str:
    bold, regular = FONTS / "Roboto-SemiBold.ttf", FONTS / "Roboto-Regular.ttf"
    px = CAP_HEIGHT / cap_height_px(bold, 1)
    baseline = MARK_SIZE / 2 + CAP_HEIGHT / 2
    x = MARK_SIZE + GAP
    name, advance = shaped_outline(bold, "AwakeKt", x, baseline, px)
    paths = [name]
    x += advance
    if product:
        space = px * 0.26
        word, advance = shaped_outline(regular, product, x + space, baseline, px)
        paths.append(word)
        x += space + advance
    width = round(x + 4)
    label = "AwakeKt" + (f" {product}" if product else "")
    body = "\n".join(f'  <path fill="{colour}" d="{d}"/>' for d in paths)
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {MARK_SIZE}" '
        f'width="{width}" height="{MARK_SIZE}" role="img" aria-label="{label}">\n'
        f'  <clipPath id="tile"><rect width="{MARK_SIZE}" height="{MARK_SIZE}" rx="{TILE_RADIUS}"/></clipPath>\n'
        f'  <g clip-path="url(#tile)">{mark_symbol()}</g>\n{body}\n</svg>\n'
    )


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for slug, product in PRODUCTS.items():
        for surface, colour in SURFACES.items():
            (OUT / f"{slug}-on-{surface}.svg").write_text(lockup(product, colour))
            print(f"wrote {slug}-on-{surface}.svg")


if __name__ == "__main__":
    main()
