#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# INVESTIGATION: perceptual diff of two PNGs. A signal, not a lock.
# Kinds are defined in docs/tasks/2026-08-23-ui-tooling-formalization-plan.md. Only a GATE can fail
# a build; `scripts/awake verify` runs every one.
"""Compares an Awake-rendered preview PNG against a real shadcn/ui reference PNG.

The two images are never pixel-identical in size or layout (different fonts, different
component ordering/padding, different capture framing) -- this is a perceptual fidelity
signal, not a golden-image lock (see ShadcnParityScreenshotTest for that). The comparison:

  1. Trim each image's own uniform-color outer border independently (down to its real content
     bounding box) -- handles both a tight white-background Awake preview and a full-viewport
     shadcn capture with a solid backdrop (e.g. dialog_states_light.png's gray overlay).
  2. Search a small window of (x, y) offsets (+/- ALIGN_SEARCH_PX) between the two trimmed crops
     and keep the one with the lowest mismatch rate, then crop both to that intersection.
     Independent trimming rarely lands on the exact same content pixel (anti-aliased edges can
     trim a pixel or two differently per image), so anchoring both crops at (0, 0) unconditionally
     compares real content against a 1-2px-shifted copy of itself -- which reads as a wall of
     mismatched pixels even when the two images are near-identical. This is a bounded local search
     for the least-bad alignment, not template matching or cross-correlation.
  3. Diff per-pixel: mismatch % (share of pixels whose mean channel delta exceeds
     PIXEL_MISMATCH_DELTA), max single-channel delta, mean delta.
  4. Write a red/blue heatmap PNG of the per-pixel delta.

Usage:
    python3 tools/shadcn/compare_parity.py <awake.png> <reference.png> --name button [--out-dir DIR]
    python3 tools/shadcn/compare_parity.py --all [--awake-dir DIR] [--out-dir DIR]

Requires Pillow (`pip3 install pillow`).
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
DEFAULT_REFERENCE_DIR = REPO_ROOT / "docs" / "reference" / "shadcn-previews"
DEFAULT_AWAKE_DIR = REPO_ROOT / "samples" / "ui-showcase" / "build" / "ui-previews"
DEFAULT_OUT_DIR = REPO_ROOT / "build" / "shadcn-parity"
PAIRS_MANIFEST = Path(__file__).resolve().parent / "shadcn_parity_pairs.json"
THRESHOLDS_FILE = Path(__file__).resolve().parent / "shadcn_parity_thresholds.json"

BORDER_TOLERANCE = 6  # per-channel; a corner-color scan counts a row/col as "border" within this
PIXEL_MISMATCH_DELTA = 24  # mean |dR|+|dG|+|dB|/3 above this counts one pixel as "mismatched"
ALIGN_SEARCH_PX = 5  # +/- window searched for the best-aligning offset between two trims
ALIGN_SCORE_SAMPLES = 60  # per-axis sample stride cap while scoring a candidate offset


def trim_uniform_border(img, tolerance: int = BORDER_TOLERANCE):
    """Crops away rows/cols that match the image's own corner color, from every edge inward.
    Returns (cropped_image, (left, top, right, bottom)_trimmed_px). Falls back to the original
    image (no crop) if the whole image is uniform, so a degenerate all-background input never
    collapses to a zero-size crop."""
    w, h = img.size
    px = img.load()
    bg = px[0, 0]

    def close(c) -> bool:
        return all(abs(c[i] - bg[i]) <= tolerance for i in range(3))

    left = 0
    while left < w and all(close(px[left, y]) for y in range(h)):
        left += 1
    right = w
    while right > left and all(close(px[right - 1, y]) for y in range(h)):
        right -= 1
    top = 0
    while top < h and all(close(px[x, top]) for x in range(left, right)):
        top += 1
    bottom = h
    while bottom > top and all(close(px[x, bottom - 1]) for x in range(left, right)):
        bottom -= 1

    if left >= right or top >= bottom:
        return img, (0, 0, 0, 0)
    return img.crop((left, top, right, bottom)), (left, top, w - right, h - bottom)


def _mismatch_rate(a_px, r_px, ax0: int, ay0: int, rx0: int, ry0: int, w: int, h: int) -> float:
    """Strided mismatch-rate estimate for one candidate offset, used only to rank alignments --
    the real per-pixel diff runs once, at the winning offset."""
    step_x = max(1, w // ALIGN_SCORE_SAMPLES)
    step_y = max(1, h // ALIGN_SCORE_SAMPLES)
    mismatches = 0
    total = 0
    for y in range(0, h, step_y):
        for x in range(0, w, step_x):
            ca, cr = a_px[ax0 + x, ay0 + y], r_px[rx0 + x, ry0 + y]
            delta = (abs(ca[0] - cr[0]) + abs(ca[1] - cr[1]) + abs(ca[2] - cr[2])) / 3.0
            total += 1
            if delta > PIXEL_MISMATCH_DELTA:
                mismatches += 1
    return mismatches / total if total else 1.0


def find_best_offset(awake_trim, ref_trim, search: int = ALIGN_SEARCH_PX) -> tuple[int, int]:
    """Finds the (dx, dy) shift of ref_trim relative to awake_trim, within +/-search px, that
    minimizes mismatch rate. Independent border trimming can land on a different content pixel
    per image (anti-aliased edges), so (0, 0) is just one candidate, not an assumption."""
    aw, ah = awake_trim.size
    rw, rh = ref_trim.size
    a_px = awake_trim.load()
    r_px = ref_trim.load()

    # visit (0, 0) first, then grow outward, so a tie (e.g. two solid-color images, every offset
    # scoring identically) keeps the no-shift default instead of drifting to a search-order artifact.
    offsets_by_distance = sorted(range(-search, search + 1), key=abs)

    best_offset = (0, 0)
    best_score = None
    for dy in offsets_by_distance:
        for dx in offsets_by_distance:
            ax0, ay0 = max(0, -dx), max(0, -dy)
            rx0, ry0 = max(0, dx), max(0, dy)
            w = min(aw - ax0, rw - rx0)
            h = min(ah - ay0, rh - ry0)
            if w <= 0 or h <= 0:
                continue
            score = _mismatch_rate(a_px, r_px, ax0, ay0, rx0, ry0, w, h)
            if best_score is None or score < best_score:
                best_score = score
                best_offset = (dx, dy)
    return best_offset


def _heatmap_pixel(delta: float) -> tuple[int, int, int]:
    # 0 delta -> blue, PIXEL_MISMATCH_DELTA*4-ish -> full red. Clamped, not linear-precise --
    # this is a human-eyeball diagnostic image, not a measurement.
    t = max(0.0, min(1.0, delta / (PIXEL_MISMATCH_DELTA * 4)))
    return (int(255 * t), 0, int(255 * (1 - t)))


def compare(awake_path: Path, reference_path: Path, out_dir: Path, name: str) -> dict:
    from PIL import Image

    awake_img = Image.open(awake_path).convert("RGBA")
    ref_img = Image.open(reference_path).convert("RGBA")

    awake_trim, awake_off = trim_uniform_border(awake_img)
    ref_trim, ref_off = trim_uniform_border(ref_img)

    dx, dy = find_best_offset(awake_trim, ref_trim)
    ax0, ay0 = max(0, -dx), max(0, -dy)
    rx0, ry0 = max(0, dx), max(0, dy)
    w = min(awake_trim.width - ax0, ref_trim.width - rx0)
    h = min(awake_trim.height - ay0, ref_trim.height - ry0)
    a = awake_trim.crop((ax0, ay0, ax0 + w, ay0 + h)).load()
    r = ref_trim.crop((rx0, ry0, rx0 + w, ry0 + h)).load()

    out_dir.mkdir(parents=True, exist_ok=True)
    heatmap = Image.new("RGB", (max(w, 1), max(h, 1)))
    hp = heatmap.load()

    mismatches = 0
    max_channel_delta = 0
    sum_delta = 0.0
    total = w * h
    for y in range(h):
        for x in range(w):
            ca, cr = a[x, y], r[x, y]
            dr, dg, db = abs(ca[0] - cr[0]), abs(ca[1] - cr[1]), abs(ca[2] - cr[2])
            delta = (dr + dg + db) / 3.0
            sum_delta += delta
            max_channel_delta = max(max_channel_delta, dr, dg, db)
            if delta > PIXEL_MISMATCH_DELTA:
                mismatches += 1
            hp[x, y] = _heatmap_pixel(delta)
    heatmap.save(out_dir / f"{name}_diff.png")

    return {
        "name": name,
        "awake_path": str(awake_path),
        "reference_path": str(reference_path),
        "awake_size": list(awake_img.size),
        "reference_size": list(ref_img.size),
        "awake_trim_px": list(awake_off),
        "reference_trim_px": list(ref_off),
        "align_offset_px": [dx, dy],
        "compared_size": [w, h],
        "mismatch_pct": round(100.0 * mismatches / total, 2) if total else 100.0,
        "max_channel_delta": max_channel_delta,
        "mean_delta": round(sum_delta / total, 2) if total else 0.0,
        "diff_image": str(out_dir / f"{name}_diff.png"),
    }


def load_thresholds() -> dict:
    if THRESHOLDS_FILE.exists():
        return json.loads(THRESHOLDS_FILE.read_text())
    return {"default": {"mismatch_pct_max": 60.0}}


def threshold_for(thresholds: dict, name: str) -> dict:
    return thresholds.get(name, thresholds.get("default", {"mismatch_pct_max": 60.0}))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("awake_png", nargs="?", type=Path, help="Awake-rendered preview PNG")
    parser.add_argument("reference_png", nargs="?", type=Path, help="Real shadcn/ui reference PNG")
    parser.add_argument("--name", help="pair name, used for the diff PNG filename and threshold lookup")
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT_DIR)
    parser.add_argument("--all", action="store_true", help="compare every pair in shadcn_parity_pairs.json")
    parser.add_argument("--awake-dir", type=Path, default=DEFAULT_AWAKE_DIR, help="dir holding <awake id>.png, used with --all")
    parser.add_argument("--reference-dir", type=Path, default=DEFAULT_REFERENCE_DIR)
    args = parser.parse_args()

    try:
        import PIL  # noqa: F401
    except ImportError:
        print("BLOCKED: Pillow is not installed. Run: pip3 install pillow", file=sys.stderr)
        return 1

    thresholds = load_thresholds()
    results = []

    if args.all:
        manifest = json.loads(PAIRS_MANIFEST.read_text())["pairs"]
        for pair in manifest:
            awake_path = args.awake_dir / f"{pair['awake']}.png"
            reference_path = args.reference_dir / pair["reference"]
            if not awake_path.exists() or not reference_path.exists():
                results.append({"name": pair["name"], "error": f"missing file(s): awake={awake_path.exists()} reference={reference_path.exists()}"})
                continue
            results.append(compare(awake_path, reference_path, args.out_dir, pair["name"]))
    else:
        if not args.awake_png or not args.reference_png:
            parser.error("awake_png and reference_png are required unless --all is given")
        name = args.name or args.awake_png.stem
        results.append(compare(args.awake_png, args.reference_png, args.out_dir, name))

    results.sort(key=lambda r: r.get("mismatch_pct", -1), reverse=True)
    print(f"{'name':10s} {'mismatch%':>10s} {'max_delta':>10s} {'mean_delta':>11s}  status")
    for r in results:
        if "error" in r:
            print(f"{r['name']:10s} {'--':>10s} {'--':>10s} {'--':>11s}  SKIPPED ({r['error']})")
            continue
        limit = threshold_for(thresholds, r["name"]).get("mismatch_pct_max", 60.0)
        status = "OK" if r["mismatch_pct"] <= limit else f"OVER (>{limit})"
        print(f"{r['name']:10s} {r['mismatch_pct']:>9.2f}% {r['max_channel_delta']:>10d} {r['mean_delta']:>11.2f}  {status}")

    args.out_dir.mkdir(parents=True, exist_ok=True)
    (args.out_dir / "shadcn-parity-metrics.json").write_text(json.dumps(results, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
