#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: exit 1 means the icon sheet metrics regressed.
# Kinds are defined in tools/README.md. Only a GATE can fail a build; visual tooling tests cover
# the helper itself.
"""Unit tests for compare_icon_sheets.py on synthetic sheets, so they need no real capture."""
from __future__ import annotations

import pytest

pytest.importorskip("PIL")

from PIL import Image, ImageDraw  # noqa: E402

from compare_icon_sheets import GLYPHS, compare_sheets  # noqa: E402
from capture_lucide_spritesheet_reference import HEADER_HEIGHT, ROW_HEIGHT  # noqa: E402

BACKGROUND = (10, 10, 10)


def _sheet(extra_right_column: bool = False) -> Image.Image:
    """One row with a symmetric 4 px wide bar centred in the 32 px glyph box."""
    image = Image.new("RGB", (320, HEADER_HEIGHT + ROW_HEIGHT + 16), BACKGROUND)
    x, y = GLYPHS[32]
    top = HEADER_HEIGHT + y
    draw = ImageDraw.Draw(image)
    draw.rectangle((x + 14, top + 4, x + 17 + (1 if extra_right_column else 0), top + 27), fill=(255, 255, 255))
    return image


def _row(rows, size):
    return next(row for row in rows if row["size"] == size)


def test_identical_sheets_measure_as_identical():
    row = _row(compare_sheets(_sheet(), _sheet(), ["bar"]), 32)
    assert row["ink_ratio"] == 1.0
    assert row["iou"] == 1.0
    assert (row["centroid_dx"], row["centroid_dy"]) == (0.0, 0.0)
    assert all(row[f"{side}_ratio"] == 1.0 for side in ("left", "right", "top", "bottom"))


def test_one_sided_extra_ink_shows_on_that_side_only():
    # The defect this tool exists to find: one edge of a stroke heavier than the other.
    row = _row(compare_sheets(_sheet(), _sheet(extra_right_column=True), ["bar"]), 32)
    assert row["ink_ratio"] == pytest.approx(1.25)
    assert row["left_ratio"] == 1.0
    assert row["right_ratio"] == pytest.approx(1.5)
    assert row["centroid_dx"] > 0.4
    assert row["top_ratio"] == pytest.approx(row["bottom_ratio"])


def test_mismatched_sheet_sizes_are_refused():
    with pytest.raises(ValueError):
        compare_sheets(_sheet(), Image.new("RGB", (320, 10), BACKGROUND), ["bar"])
