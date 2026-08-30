#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: exit 1 means the offset-alignment search regressed.
# Kinds are defined in docs/tasks/2026-08-23-ui-tooling-formalization-plan.md. Only a GATE can fail
# a build; `scripts/awake verify` runs every one.
"""Unit tests for find_best_offset in compare_parity.py.

Anchoring two independently-trimmed crops at (0, 0) treats a 1-2px trim discrepancy as a
genuine pixel mismatch, which is the bug this search fixes. These tests build synthetic images
in memory so they run anywhere without a real Awake/shadcn capture pair.
"""
from __future__ import annotations

import pytest

pytest.importorskip("PIL")

from compare_parity import find_best_offset  # noqa: E402


def _solid(size, color):
    from PIL import Image

    return Image.new("RGB", size, color)


def _with_dot(size, color, dot_xy, dot_color):
    img = _solid(size, color)
    img.putpixel(dot_xy, dot_color)
    return img


def test_identical_content_shifted_by_two_pixels_still_aligns():
    # Same content, but the reference crop starts 2px later on each axis -- the exact shape of an
    # anti-aliased trim landing on a different pixel per image.
    awake = _with_dot((20, 20), (255, 255, 255), (5, 5), (0, 0, 0))
    ref = _with_dot((20, 20), (255, 255, 255), (7, 7), (0, 0, 0))

    dx, dy = find_best_offset(awake, ref)

    assert (dx, dy) == (2, 2)


def test_already_aligned_content_keeps_zero_offset():
    awake = _with_dot((20, 20), (255, 255, 255), (5, 5), (0, 0, 0))
    ref = _with_dot((20, 20), (255, 255, 255), (5, 5), (0, 0, 0))

    assert find_best_offset(awake, ref) == (0, 0)


def test_genuinely_different_content_does_not_fake_an_alignment():
    # A real content bug (not a trim artifact) shouldn't be searched away just because some
    # offset happens to score marginally better within the window.
    awake = _solid((20, 20), (255, 255, 255))
    ref = _solid((20, 20), (0, 0, 0))

    # any offset scores equally badly (uniform full-image mismatch) -- (0, 0) stays the winner.
    assert find_best_offset(awake, ref) == (0, 0)
