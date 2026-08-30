#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# INVESTIGATION: crops by semantic node and diffs. Never records a baseline.
# Kinds are defined in docs/tasks/2026-08-23-ui-tooling-formalization-plan.md. Only a GATE can fail
# a build; `scripts/awake verify` runs every one.
"""Crop an Awake preview by semantic node and compare it with a shadcn case PNG.

The local shadcn reference app already emits component-hugging screenshots through
Playwright (``#case`` or an explicit portal selector). Awake previews are full-scene
PNGs, but their sibling ``.json`` design report contains semantic node bounds. This
tool joins those two representations without hand-cropping screenshots.

Single case:
    python3 skills/awake-ui-verification/scripts/compare_component_crops.py \
      --awake-png samples/ui-showcase/build/ui-previews/ui-showcase-theming.png \
      --semantic-json samples/ui-showcase/build/ui-previews/ui-showcase-theming.json \
      --node-id theming.badge \
      --reference-png docs/reference/shadcn-previews-local/badge-variants_light.png \
      --name theming-badge \
      --padding 4

Batch manifest (paths are relative to the repository root):
    python3 skills/awake-ui-verification/scripts/compare_component_crops.py \
      --manifest skills/awake-ui-verification/scripts/ui_component_parity_cases.example.json

The command writes one Awake crop, one diff heatmap, one per-case metrics JSON, and a
combined ``component-parity-metrics.json`` under ``build/reports/ui-component-parity``.
It deliberately does not update any baseline. A before/after comparison proves drift;
the shadcn reference PNG is the correctness reference.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path
from typing import Any

from PIL import Image


REPO_ROOT = Path(__file__).resolve().parents[4]
DEFAULT_OUT_DIR = REPO_ROOT / "build" / "reports" / "ui-component-parity"
DEFAULT_CHANNEL_TOLERANCE = 2
ALIGN_SEARCH_PX = 5  # +/- window searched for the best-aligning offset between actual and expected
# Component crops are a few hundred px at most (unlike compare_parity's full-page screenshots),
# so this stays high enough that step_x/step_y round down to 1 -- an exact score, not a sample --
# for every real case; it only kicks in as a safety cap on a pathologically large crop.
ALIGN_SCORE_SAMPLES = 512


def resolve_path(value: str | Path, base: Path = REPO_ROOT) -> Path:
    path = Path(value)
    return path if path.is_absolute() else base / path


def load_semantic_nodes(semantic_json: Path, node_ids: list[str]) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    document = json.loads(semantic_json.read_text())
    selected = []
    for node_id in node_ids:
        matches = [node for node in document.get("semantics", []) if node.get("id") == node_id]
        if not matches:
            raise ValueError(f"semantic node id {node_id!r} was not found in {semantic_json}")
        if len(matches) > 1:
            raise ValueError(
                f"semantic node id {node_id!r} is duplicated {len(matches)} times in {semantic_json}; "
                "fix duplicate IDs before comparing a crop"
            )
        selected.append(matches[0])
    return document, selected


def load_semantic_node(semantic_json: Path, node_id: str) -> tuple[dict[str, Any], dict[str, Any]]:
    document, nodes = load_semantic_nodes(semantic_json, [node_id])
    return document, nodes[0]


def semantic_crop_box(
    document: dict[str, Any],
    node: dict[str, Any],
    image_size: tuple[int, int],
    padding: float,
) -> tuple[int, int, int, int]:
    """Resolve logical UiBounds to raster pixels and clamp to the PNG."""
    metadata_width = float(document.get("width", 0))
    metadata_height = float(document.get("height", 0))
    if metadata_width <= 0 or metadata_height <= 0:
        raise ValueError("semantic report must contain positive width and height metadata")

    image_width, image_height = image_size
    scale_x = image_width / metadata_width
    scale_y = image_height / metadata_height
    if not math.isclose(scale_x, scale_y, rel_tol=0.01, abs_tol=0.01):
        raise ValueError(
            f"non-uniform preview scale is unsupported: PNG={image_size}, "
            f"logical=({metadata_width:g}, {metadata_height:g})"
        )
    scale = (scale_x + scale_y) / 2.0

    bounds = node.get("bounds")
    if not isinstance(bounds, dict):
        raise ValueError(f"semantic node {node.get('id')!r} has no bounds")
    try:
        x = float(bounds["x"])
        y = float(bounds["y"])
        width = float(bounds["w"])
        height = float(bounds["h"])
    except (KeyError, TypeError, ValueError) as error:
        raise ValueError(f"semantic node {node.get('id')!r} has invalid bounds: {bounds}") from error
    if width < 0 or height < 0:
        raise ValueError(f"semantic node {node.get('id')!r} has negative bounds: {bounds}")

    padding_px = max(0.0, padding) * scale
    left = max(0, math.floor((x * scale) - padding_px))
    top = max(0, math.floor((y * scale) - padding_px))
    right = min(image_width, math.ceil(((x + width) * scale) + padding_px))
    bottom = min(image_height, math.ceil(((y + height) * scale) + padding_px))
    if right <= left or bottom <= top:
        raise ValueError(f"semantic node {node.get('id')!r} resolves to an empty crop")
    return left, top, right, bottom


def semantic_crop_box_for_nodes(
    document: dict[str, Any],
    nodes: list[dict[str, Any]],
    image_size: tuple[int, int],
    padding: float,
) -> tuple[int, int, int, int]:
    """Resolve the union of several semantic nodes to one raster crop."""
    if not nodes:
        raise ValueError("at least one semantic node is required")
    bounds = [node.get("bounds") for node in nodes]
    if any(not isinstance(value, dict) for value in bounds):
        raise ValueError("every semantic node in a grouped crop must have bounds")
    left = min(float(value["x"]) for value in bounds)
    top = min(float(value["y"]) for value in bounds)
    right = max(float(value["x"]) + float(value["w"]) for value in bounds)
    bottom = max(float(value["y"]) + float(value["h"]) for value in bounds)
    union = {"id": ",".join(str(node.get("id")) for node in nodes), "bounds": {"x": left, "y": top, "w": right - left, "h": bottom - top}}
    return semantic_crop_box(document, union, image_size, padding)


def heatmap_pixel(delta: float) -> tuple[int, int, int, int]:
    # Blue means equal; red means a large difference. The heatmap is diagnostic only.
    t = max(0.0, min(1.0, delta / 96.0))
    return int(255 * t), 0, int(255 * (1.0 - t)), 255


def _mismatch_rate(
    actual_pixels: Any,
    expected_pixels: Any,
    ax0: int,
    ay0: int,
    ex0: int,
    ey0: int,
    w: int,
    h: int,
    channel_tolerance: int,
) -> float:
    """Strided mismatch-rate estimate for one candidate offset, used only to rank alignments --
    the real per-pixel diff runs once, at the winning offset."""
    step_x = max(1, w // ALIGN_SCORE_SAMPLES)
    step_y = max(1, h // ALIGN_SCORE_SAMPLES)
    mismatches = 0
    total = 0
    for y in range(0, h, step_y):
        for x in range(0, w, step_x):
            a = actual_pixels[ax0 + x, ay0 + y]
            e = expected_pixels[ex0 + x, ey0 + y]
            total += 1
            if any(abs(a[i] - e[i]) > channel_tolerance for i in range(4)):
                mismatches += 1
    return mismatches / total if total else 1.0


def find_best_offset(
    actual: Image.Image,
    expected: Image.Image,
    channel_tolerance: int,
    search: int = ALIGN_SEARCH_PX,
) -> tuple[int, int]:
    """Finds the (dx, dy) shift of expected relative to actual, within +/-search px, that
    minimizes mismatch rate. The Awake crop's semantic-bounds floor/ceil rounding rarely lands on
    the exact same pixel as the reference PNG's own crop boundary, so (0, 0) is just one
    candidate, not an assumption."""
    actual_width, actual_height = actual.size
    expected_width, expected_height = expected.size
    actual_pixels = actual.load()
    expected_pixels = expected.load()

    # visit (0, 0) first, then grow outward, so a tie (e.g. two solid-color crops, every offset
    # scoring identically) keeps the no-shift default instead of drifting to a search-order artifact.
    offsets_by_distance = sorted(range(-search, search + 1), key=abs)

    best_offset = (0, 0)
    best_score = None
    for dy in offsets_by_distance:
        for dx in offsets_by_distance:
            ax0, ay0 = max(0, -dx), max(0, -dy)
            ex0, ey0 = max(0, dx), max(0, dy)
            w = min(actual_width - ax0, expected_width - ex0)
            h = min(actual_height - ay0, expected_height - ey0)
            if w <= 0 or h <= 0:
                continue
            score = _mismatch_rate(actual_pixels, expected_pixels, ax0, ay0, ex0, ey0, w, h, channel_tolerance)
            if best_score is None or score < best_score:
                best_score = score
                best_offset = (dx, dy)
    return best_offset


def compare_images(
    actual: Image.Image,
    expected: Image.Image,
    channel_tolerance: int,
) -> tuple[dict[str, Any], Image.Image]:
    actual = actual.convert("RGBA")
    expected = expected.convert("RGBA")
    actual_width, actual_height = actual.size
    expected_width, expected_height = expected.size

    dx, dy = find_best_offset(actual, expected, channel_tolerance)
    # Shift expected lookups to the winning offset, then diff only the resulting overlap --
    # mirrors compare_parity.compare()'s crop-then-diff so the sliver an offset uncovers isn't
    # double-penalized as a fake mismatch on top of the (already tolerance-checked) overlap.
    ax0, ay0 = max(0, -dx), max(0, -dy)
    ex0, ey0 = max(0, dx), max(0, dy)
    compared_width = max(0, min(actual_width - ax0, expected_width - ex0))
    compared_height = max(0, min(actual_height - ay0, expected_height - ey0))

    heatmap = Image.new("RGBA", (max(compared_width, 1), max(compared_height, 1)), (255, 0, 0, 255))
    actual_pixels = actual.load()
    expected_pixels = expected.load()
    heatmap_pixels = heatmap.load()

    mismatches = 0
    max_channel_delta = 0
    sum_delta = 0.0
    total = compared_width * compared_height

    for y in range(compared_height):
        for x in range(compared_width):
            actual_pixel = actual_pixels[ax0 + x, ay0 + y]
            expected_pixel = expected_pixels[ex0 + x, ey0 + y]
            channel_deltas = [abs(actual_pixel[i] - expected_pixel[i]) for i in range(4)]
            delta = sum(channel_deltas[:3]) / 3.0
            sum_delta += delta
            max_channel_delta = max(max_channel_delta, *channel_deltas)
            if any(channel_delta > channel_tolerance for channel_delta in channel_deltas):
                mismatches += 1
            heatmap_pixels[x, y] = heatmap_pixel(delta)

    metrics = {
        "awake_size": [actual_width, actual_height],
        "reference_size": [expected_width, expected_height],
        "compared_size": [compared_width, compared_height],
        "size_match": actual.size == expected.size,
        "size_delta": [actual_width - expected_width, actual_height - expected_height],
        "align_offset_px": [dx, dy],
        "mismatch_pct": round(100.0 * mismatches / total, 2) if total else 100.0,
        "mismatched_pixels": mismatches,
        "max_channel_delta": max_channel_delta,
        "mean_rgb_delta": round(sum_delta / total, 2) if total else 0.0,
        "channel_tolerance": channel_tolerance,
    }
    return metrics, heatmap


def run_case(case: dict[str, Any], out_dir: Path, default_max_mismatch: float, default_tolerance: int) -> dict[str, Any]:
    name = str(case.get("comparisonName") or case.get("name") or case.get("nodeId") or "component")
    awake_png = resolve_path(case["awakePng"])
    semantic_json = resolve_path(case.get("semanticJson", awake_png.with_suffix(".json")))
    reference_png = resolve_path(case["referencePng"])
    # paintNodeIds narrows the pixel crop below the full nodeIds set used for geometry checks
    # (see tools/shadcn/shadcn_parity_manifest.json's popover case for why the two can differ).
    node_ids = [str(node_id) for node_id in case.get("paintNodeIds", case.get("nodeIds", [case.get("nodeId")])) if node_id is not None]
    if not node_ids:
        raise ValueError(f"case {name!r} requires nodeId or nodeIds")
    padding = float(case.get("padding", 0.0))
    has_threshold = "maxMismatchPct" in case
    max_mismatch = float(case["maxMismatchPct"]) if has_threshold else default_max_mismatch
    channel_tolerance = int(case.get("channelTolerance", default_tolerance))

    document, nodes = load_semantic_nodes(semantic_json, node_ids)
    awake_image = Image.open(awake_png).convert("RGBA")
    crop_box = semantic_crop_box_for_nodes(document, nodes, awake_image.size, padding)
    awake_crop = awake_image.crop(crop_box)
    reference_image = Image.open(reference_png).convert("RGBA")
    metrics, heatmap = compare_images(awake_crop, reference_image, channel_tolerance)
    metrics.update(
        {
            "name": name,
            "awake_png": str(awake_png),
            "semantic_json": str(semantic_json),
            "reference_png": str(reference_png),
            "node_ids": node_ids,
            "crop_box": list(crop_box),
            "padding_logical_px": padding,
            "max_mismatch_pct": max_mismatch if has_threshold else None,
            "status": (
                "REVIEW"
                if not has_threshold
                else "OK" if metrics["size_match"] and metrics["mismatch_pct"] <= max_mismatch else "OVER"
            ),
        }
    )

    out_dir.mkdir(parents=True, exist_ok=True)
    awake_crop_path = out_dir / f"{name}_awake.png"
    diff_path = out_dir / f"{name}_diff.png"
    metrics_path = out_dir / f"{name}.json"
    awake_crop.save(awake_crop_path)
    heatmap.save(diff_path)
    metrics["awake_crop"] = str(awake_crop_path)
    metrics["diff_image"] = str(diff_path)
    metrics_path.write_text(json.dumps(metrics, indent=2) + "\n")
    return metrics


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--manifest", type=Path, help="JSON manifest containing a cases array")
    parser.add_argument("--awake-png", type=Path)
    parser.add_argument("--semantic-json", type=Path)
    parser.add_argument("--node-id", action="append", help="semantic node ID; repeat to crop a grouped layout")
    parser.add_argument("--reference-png", type=Path)
    parser.add_argument("--name")
    parser.add_argument("--padding", type=float, default=0.0, help="logical px added on every side of the Awake crop")
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT_DIR)
    parser.add_argument("--max-mismatch-pct", type=float, default=0.0)
    parser.add_argument("--channel-tolerance", type=int, default=DEFAULT_CHANNEL_TOLERANCE)
    parser.add_argument("--fail-on-mismatch", action="store_true", help="return non-zero when a case exceeds its threshold")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        if args.manifest:
            manifest_path = resolve_path(args.manifest)
            manifest = json.loads(manifest_path.read_text())
            cases = manifest.get("cases")
            if not isinstance(cases, list) or not cases:
                raise ValueError(f"{manifest_path} must contain a non-empty cases array")
        else:
            required = (args.awake_png, args.node_id, args.reference_png)
            if any(value is None for value in required):
                raise ValueError("single-case mode requires --awake-png, --node-id, and --reference-png")
            cases = [
                {
                    "name": args.name or "-".join(args.node_id),
                    "awakePng": str(args.awake_png),
                    "semanticJson": str(args.semantic_json) if args.semantic_json else None,
                    "nodeIds": args.node_id,
                    "referencePng": str(args.reference_png),
                    "padding": args.padding,
                }
            ]

        results = []
        for case in cases:
            case = {key: value for key, value in case.items() if value is not None}
            results.append(run_case(case, resolve_path(args.out_dir), args.max_mismatch_pct, args.channel_tolerance))

        combined_path = resolve_path(args.out_dir) / "component-parity-metrics.json"
        combined_path.write_text(json.dumps(results, indent=2) + "\n")
        print(f"{'name':32s} {'awake':>12s} {'reference':>12s} {'mismatch%':>10s} {'status'}")
        for result in results:
            print(
                f"{result['name'][:32]:32s} "
                f"{str(tuple(result['awake_size'])):>12s} "
                f"{str(tuple(result['reference_size'])):>12s} "
                f"{result['mismatch_pct']:>9.2f}% {result['status']}"
            )
        if args.fail_on_mismatch and any(result["status"] == "OVER" for result in results):
            return 1
        return 0
    except (OSError, KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
