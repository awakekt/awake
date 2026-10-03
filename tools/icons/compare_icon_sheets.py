#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# INVESTIGATION: measures how far Awake's icon sheet is from Chromium's. Never records a baseline.
# Kinds are defined in tools/README.md. Only a GATE can fail a build; visual tooling tests cover
# the helper itself.
"""Compare Awake's Lucide icon sheet against Chromium rendering the same pinned SVGs.

A byte comparison says whether a render moved; this says how it differs from the reference:

- ink ratio: total coverage, Awake over Chromium. Above 1 is a heavier stroke.
- IoU: overlap of the two shapes thresholded at half coverage.
- centroid shift: where the ink's centre of mass moved, in pixels.
- side balance: ink left of, right of, above and below the glyph's centre, each over the
  reference's. Unequal halves on a symmetric glyph, such as a chevron's two arms, mean the
  anti-aliased fringe differs per edge.

Both sheets share the layout `capture_lucide_spritesheet_reference.py` draws and
`LucideShadcnSpritesheetPreviewTest` mirrors, so rows line up by name with no cropping step:

    python3 tools/icons/compare_icon_sheets.py \
        --reference ui-original-svg-browser.png --awake ui-lucide-icons-cpu.png

The render-evidence workflow publishes both images for every PR that touches icons. The command
writes `icon-sheet-metrics.json` and `icon-sheet-diff.png` (red: Awake heavier, blue: lighter) under
`build/reports/icon-sheet-compare`.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image

from capture_lucide_spritesheet_reference import HEADER_HEIGHT, ICONS, ROW_HEIGHT, name_from_path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUT_DIR = REPO_ROOT / "build" / "reports" / "icon-sheet-compare"
# Top-left of each glyph's box within its row: the 16 px glyph is centred in a 48 px cell after the
# 16 px padding and 192 px label, the 32 px one starts the next column. Rows are 48 px tall.
GLYPHS = {16: (224, 16), 32: (256, 8)}
# Room for the anti-aliased fringe and round caps that reach past the glyph box.
MARGIN = 2
SIDES = ("left", "right", "top", "bottom")
HEATMAP_ZOOM = 6


def coverage(image: Image.Image) -> tuple[list[float], int, int]:
    """Per-pixel ink in 0..1 over the sheet's own background, read from its top-left pixel."""
    grey = image.convert("L")
    width, height = grey.size
    pixels = list(grey.tobytes())
    background = pixels[0] / 255
    span = max(1e-6, 1 - background)
    return [min(1.0, max(0.0, (p / 255 - background) / span)) for p in pixels], width, height


def cell(values: list[float], width: int, x0: int, y0: int, size: int) -> list[list[float]]:
    return [[values[(y0 + y) * width + x0 + x] for x in range(size)] for y in range(size)]


def centroid(grid: list[list[float]]) -> tuple[float, float]:
    total = sum(map(sum, grid)) or 1.0
    cx = sum(v * x for row in grid for x, v in enumerate(row)) / total
    cy = sum(v * y for y, row in enumerate(grid) for v in row) / total
    return cx, cy


def halves(grid: list[list[float]]) -> dict[str, float]:
    """Ink on each side of the glyph's centre lines; the centre row and column count for neither."""
    middle = (len(grid) - 1) / 2
    return {
        "left": sum(v for row in grid for x, v in enumerate(row) if x < middle),
        "right": sum(v for row in grid for x, v in enumerate(row) if x > middle),
        "top": sum(v for y, row in enumerate(grid) for v in row if y < middle),
        "bottom": sum(v for y, row in enumerate(grid) for v in row if y > middle),
    }


def compare_cells(reference: list[list[float]], awake: list[list[float]]) -> dict[str, float]:
    ref_ink = sum(map(sum, reference))
    awake_ink = sum(map(sum, awake))
    both = sum(1 for r, a in zip(sum(reference, []), sum(awake, [])) if r >= 0.5 and a >= 0.5)
    either = sum(1 for r, a in zip(sum(reference, []), sum(awake, [])) if r >= 0.5 or a >= 0.5)
    (rx, ry), (ax, ay) = centroid(reference), centroid(awake)
    ref_halves, awake_halves = halves(reference), halves(awake)
    return {
        "ink_ratio": round(awake_ink / ref_ink, 4) if ref_ink else 0.0,
        "iou": round(both / either, 4) if either else 1.0,
        "centroid_dx": round(ax - rx, 3),
        "centroid_dy": round(ay - ry, 3),
        **{
            f"{side}_ratio": round(awake_halves[side] / ref_halves[side], 4) if ref_halves[side] else 0.0
            for side in SIDES
        },
        "max_abs_error": round(max(abs(a - r) for r, a in zip(sum(reference, []), sum(awake, []))), 3),
    }


def compare_sheets(reference: Image.Image, awake: Image.Image, names: list[str]) -> list[dict]:
    if reference.size != awake.size:
        raise ValueError(f"sheet sizes differ: reference {reference.size}, awake {awake.size}")
    ref_values, width, _ = coverage(reference)
    awake_values, _, _ = coverage(awake)
    rows = []
    for index, name in enumerate(names):
        for size, (x, y) in GLYPHS.items():
            x0, y0, span = x - MARGIN, HEADER_HEIGHT + ROW_HEIGHT * index + y - MARGIN, size + 2 * MARGIN
            rows.append({
                "icon": name,
                "size": size,
                **compare_cells(cell(ref_values, width, x0, y0, span), cell(awake_values, width, x0, y0, span)),
            })
    return rows


def heatmap(reference: Image.Image, awake: Image.Image) -> Image.Image:
    """Reference, Awake and their signed difference side by side, enlarged so single pixels read."""
    ref_values, width, height = coverage(reference)
    awake_values, _, _ = coverage(awake)
    diff = Image.new("RGB", (width, height))
    diff.putdata([
        (min(255, int(max(0.0, a - r) * 3 * 255)), 0, min(255, int(max(0.0, r - a) * 3 * 255)))
        for r, a in zip(ref_values, awake_values)
    ])
    panels = [reference.convert("RGB"), awake.convert("RGB"), diff]
    out = Image.new("RGB", (width * 3 * HEATMAP_ZOOM, height * HEATMAP_ZOOM))
    for i, panel in enumerate(panels):
        out.paste(panel.resize((width * HEATMAP_ZOOM, height * HEATMAP_ZOOM), Image.NEAREST), (i * width * HEATMAP_ZOOM, 0))
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--reference", type=Path, required=True, help="Chromium sheet (ui-original-svg-browser.png)")
    parser.add_argument("--awake", type=Path, required=True, help="Awake sheet (ui-lucide-icons-cpu.png)")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT_DIR)
    args = parser.parse_args()

    names = [name_from_path(p) for p in sorted(ICONS.glob("*.svg"), key=name_from_path)]
    reference, awake = Image.open(args.reference), Image.open(args.awake)
    rows = compare_sheets(reference, awake, names)

    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "icon-sheet-metrics.json").write_text(json.dumps(rows, indent=2) + "\n")
    heatmap(reference, awake).save(args.out / "icon-sheet-diff.png")

    print(f"{'icon':20} {'px':>3} {'ink':>6} {'IoU':>5} {'dx':>6} {'dy':>6} " + " ".join(f"{s:>6}" for s in SIDES))
    for row in rows:
        print(
            f"{row['icon']:20} {row['size']:>3} {row['ink_ratio']:6.3f} {row['iou']:5.2f} "
            f"{row['centroid_dx']:+6.2f} {row['centroid_dy']:+6.2f} "
            + " ".join(f"{row[f'{s}_ratio']:6.3f}" for s in SIDES)
        )
    print(f"wrote {args.out / 'icon-sheet-metrics.json'} and {args.out / 'icon-sheet-diff.png'}")


if __name__ == "__main__":
    main()
