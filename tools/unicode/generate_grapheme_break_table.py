#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GENERATOR: writes the grapheme break table awake:core:text ships, and the Unicode test data its
# tests run. Kinds are defined in tools/README.md.
"""Builds `GraphemeBreakTable.kt` and copies `GraphemeBreakTest.txt` from the Unicode Character Database.

The table holds, for every code point, the three properties UAX #29's extended grapheme cluster
rules read: Grapheme_Cluster_Break, Extended_Pictographic and Indic_Conjunct_Break. Code points
with equal properties are merged into ranges, and each range is one Int: its first code point
shifted left by 7, ORed with its property bits. Hangul syllables U+AC00..U+D7A3 are one LV range;
the iterator tells LV from LVT arithmetically, which keeps 798 alternating ranges out of the table.

The UCD files come from https://www.unicode.org/Public/<version>/ucd/, or from --ucd, a directory
holding GraphemeBreakProperty.txt, emoji-data.txt, DerivedCoreProperties.txt and
GraphemeBreakTest.txt. A re-run with the same version writes the same bytes.

Usage:
    python3 tools/unicode/generate_grapheme_break_table.py
    python3 tools/unicode/generate_grapheme_break_table.py --ucd path/to/ucd
"""

from __future__ import annotations

import argparse
import re
import sys
import urllib.request
from pathlib import Path

UNICODE_VERSION = "18.0.0"

REPO = Path(__file__).resolve().parents[2]
TABLE_OUT = REPO / "awake/core/text/src/commonMain/kotlin/com/awakekt/awake/core/text/unicode/GraphemeBreakTable.kt"
TEST_OUT = REPO / "awake/core/text/src/commonTest/resources/unicode/GraphemeBreakTest.txt"

SOURCES = {
    "GraphemeBreakProperty.txt": "auxiliary/GraphemeBreakProperty.txt",
    "GraphemeBreakTest.txt": "auxiliary/GraphemeBreakTest.txt",
    "emoji-data.txt": "emoji/emoji-data.txt",
    "DerivedCoreProperties.txt": "DerivedCoreProperties.txt",
}

# Must match the constants in GraphemeClusters.kt.
GCB = {
    "Other": 0,
    "CR": 1,
    "LF": 2,
    "Control": 3,
    "Extend": 4,
    "ZWJ": 5,
    "Regional_Indicator": 6,
    "Prepend": 7,
    "SpacingMark": 8,
    "L": 9,
    "V": 10,
    "T": 11,
    "LV": 12,
    "LVT": 13,
}
EXT_PICT = 1 << 4
INCB = {"Linker": 1 << 5, "Consonant": 2 << 5, "Extend": 3 << 5}
VALUE_BITS = 7
MAX_CODE_POINT = 0x10FFFF
HANGUL_FIRST = 0xAC00
HANGUL_LAST = 0xD7A3
HANGUL_T_COUNT = 28

LINE = re.compile(r"^([0-9A-F]+)(?:\.\.([0-9A-F]+))?\s*;\s*([^#]+?)\s*(?:#.*)?$")


def read_source(name: str, ucd: Path | None) -> str:
    if ucd is not None:
        return (ucd / name).read_text(encoding="utf-8")
    url = f"https://www.unicode.org/Public/{UNICODE_VERSION}/ucd/{SOURCES[name]}"
    with urllib.request.urlopen(url) as response:  # noqa: S310 - a fixed https URL
        return response.read().decode("utf-8")


def check_version(name: str, text: str) -> None:
    head = "\n".join(text.splitlines()[:12])
    if f"-{UNICODE_VERSION}" not in head and f"Version: {UNICODE_VERSION}" not in head:
        sys.exit(f"{name} is not Unicode {UNICODE_VERSION}:\n{head}")


def ranges(text: str):
    for line in text.splitlines():
        match = LINE.match(line)
        if match:
            first = int(match.group(1), 16)
            last = int(match.group(2) or match.group(1), 16)
            yield first, last, [field.strip() for field in match.group(3).split(";")]


def build_values(sources: dict[str, str]) -> list[int]:
    values = [0] * (MAX_CODE_POINT + 1)
    for first, last, fields in ranges(sources["GraphemeBreakProperty.txt"]):
        for code_point in range(first, last + 1):
            values[code_point] = GCB[fields[0]]
    for first, last, fields in ranges(sources["emoji-data.txt"]):
        if fields[0] == "Extended_Pictographic":
            for code_point in range(first, last + 1):
                values[code_point] |= EXT_PICT
    for first, last, fields in ranges(sources["DerivedCoreProperties.txt"]):
        if fields[0] == "InCB":
            for code_point in range(first, last + 1):
                values[code_point] |= INCB[fields[1]]
    for code_point in range(HANGUL_FIRST, HANGUL_LAST + 1):
        expected = GCB["LV"] if (code_point - HANGUL_FIRST) % HANGUL_T_COUNT == 0 else GCB["LVT"]
        if values[code_point] != expected:
            sys.exit(f"U+{code_point:04X} breaks the Hangul LV/LVT pattern the iterator computes")
        values[code_point] = GCB["LV"]
    return values


def merge(values: list[int]) -> list[int]:
    packed = []
    previous = None
    for code_point, value in enumerate(values):
        if value != previous:
            packed.append((code_point << VALUE_BITS) | value)
            previous = value
    return packed


def kotlin_table(packed: list[int]) -> str:
    lines = []
    per_line = 8
    for start in range(0, len(packed), per_line):
        chunk = packed[start:start + per_line]
        lines.append("        " + ", ".join(f"0x{value:08X}" for value in chunk) + ",")
    body = "\n".join(lines)
    return f'''/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 * SPDX-FileCopyrightText: 1991-2026 Unicode, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0 AND Unicode-3.0
 */
package com.awakekt.awake.core.text.unicode

// GENERATED by tools/unicode/generate_grapheme_break_table.py from Unicode {UNICODE_VERSION}:
// GraphemeBreakProperty.txt, emoji-data.txt (Extended_Pictographic) and DerivedCoreProperties.txt
// (Indic_Conjunct_Break). Do not edit; re-run the generator for a new Unicode version.

/** The Unicode version [GraphemeBreakTable] was generated from. */
internal const val GRAPHEME_BREAK_UNICODE_VERSION: String = "{UNICODE_VERSION}"

/**
 * Grapheme break properties by code point range, sorted: each entry is a range's first code point
 * shifted left by [GRAPHEME_VALUE_BITS], ORed with the properties every code point up to the next
 * entry has.
 */
internal object GraphemeBreakTable {{
    val ranges: IntArray = intArrayOf(
{body}
    )
}}
'''


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--ucd", type=Path, help="a directory with the UCD files, instead of downloading them")
    args = parser.parse_args()

    sources = {name: read_source(name, args.ucd) for name in SOURCES}
    for name in ("GraphemeBreakProperty.txt", "GraphemeBreakTest.txt", "DerivedCoreProperties.txt", "emoji-data.txt"):
        check_version(name, sources[name])

    packed = merge(build_values(sources))
    TABLE_OUT.parent.mkdir(parents=True, exist_ok=True)
    TABLE_OUT.write_text(kotlin_table(packed), encoding="utf-8", newline="\n")
    TEST_OUT.parent.mkdir(parents=True, exist_ok=True)
    TEST_OUT.write_text(sources["GraphemeBreakTest.txt"], encoding="utf-8", newline="\n")
    print(f"{TABLE_OUT.relative_to(REPO)}: {len(packed)} ranges, Unicode {UNICODE_VERSION}")
    print(f"{TEST_OUT.relative_to(REPO)}")


if __name__ == "__main__":
    main()
