#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: exit 1 means the tool itself is broken; runs under `awake verify`'s tool-tests gate.
"""A staleness check that stops matching is a check that always passes."""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

SPEC = Path(__file__).resolve().parent / "verify_detekt_baselines.py"
_spec = importlib.util.spec_from_file_location("verify_detekt_baselines", SPEC)
verify_detekt_baselines = importlib.util.module_from_spec(_spec)
sys.modules["verify_detekt_baselines"] = verify_detekt_baselines
_spec.loader.exec_module(verify_detekt_baselines)


def test_the_file_name_is_extracted_from_a_real_entry() -> None:
    # The whole check hinges on this regex. A detekt format change would silently make every
    # baseline look clean.
    entry = "LongParameterList:GlyphAtlasSource.kt$GlyphAtlasSource$( rows: IntArray )"
    match = verify_detekt_baselines.FILE_IN_ENTRY.match(entry)

    assert match is not None
    assert match.group(1) == "GlyphAtlasSource.kt"


def test_an_entry_with_a_dollar_in_its_symbol_still_yields_the_file() -> None:
    entry = "MagicNumber:BitmapFont.kt$BitmapFont$0.125f"

    assert verify_detekt_baselines.FILE_IN_ENTRY.match(entry).group(1) == "BitmapFont.kt"


def test_entries_are_found_in_a_baseline_document() -> None:
    doc = "<SmellBaseline><CurrentIssues><ID>A:B.kt$C</ID><ID>D:E.kt$F</ID></CurrentIssues></SmellBaseline>"

    assert verify_detekt_baselines.ENTRY.findall(doc) == ["A:B.kt$C", "D:E.kt$F"]


def test_the_repo_is_clean() -> None:
    assert verify_detekt_baselines.main() == 0


def test_it_actually_inspects_baselines() -> None:
    # Guards the vacuous pass: if SKIP or the glob stopped matching, the repo would look clean
    # because nothing was read at all.
    root = verify_detekt_baselines.REPO_ROOT
    scanned = [
        b for b in root.rglob("detekt-baseline.xml")
        if not verify_detekt_baselines.skipped(b) and (b.parent / "src").exists()
    ]
    assert len(scanned) >= 20, f"only {len(scanned)} baselines scanned"
