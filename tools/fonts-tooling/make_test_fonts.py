#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GENERATOR: writes the font fixtures awake:core:text's loader tests read.
# Kinds are defined in tools/README.md. Only a GATE can fail a build; visual tooling tests cover
# the helper itself.
"""Builds three small Roboto Regular subsets for UiFonts.load's tests.

- roboto-subset.ttf keeps TrueType outlines: overlapping contours, the composite 'Ä', a format 4 cmap.
- roboto-subset.otf carries the same outlines as CFF Type 2 charstrings with a format 12 cmap.
  Its 'H' runs from a local subroutine and its 'g' from a global one, and U+25A0 maps to a
  600 by 500 rectangle drawn with flex, hflex, hflex1 and flex1, stem hints, a hintmask and a
  cntrmask, so the charstring operators the specializer never writes are covered too.
- roboto-kern.ttf has no GPOS; its pair kerning is a format 0 `kern` table instead, holding every
  nonzero pair of its characters with the value Roboto's own GPOS `kern` feature gives it (the
  pair positioning lookups applied in order, the first subtable that matches a pair deciding it
  within each lookup, as HarfBuzz applies them). Roboto has no `kern` table of its own.

Both are small enough to mutate byte by byte in a test. Timestamps are kept, so a re-run writes
the same bytes.

Usage:
    python3 tools/fonts-tooling/make_test_fonts.py
"""

from __future__ import annotations

from pathlib import Path

from fontTools import subset
from fontTools.cffLib import GlobalSubrsIndex, SubrsIndex
from fontTools.fontBuilder import FontBuilder
from fontTools.misc.psCharStrings import T2CharString
from fontTools.pens.t2CharStringPen import T2CharStringPen
from fontTools.ttLib import TTFont, newTable
from fontTools.ttLib.tables._c_m_a_p import CmapSubtable
from fontTools.ttLib.tables._k_e_r_n import KernTable_format_0

REPO = Path(__file__).resolve().parent.parent.parent
SOURCE = REPO / "awake/ui/font-atlas-generator/fonts/Roboto-Regular.ttf"
OUT_DIR = REPO / "awake/core/text/src/commonTest/resources/fonts"
TEXT = " Hg.:?Ä"
KERN_TEXT = " AVTWYaeory.,/"
RECT_CODEPOINT = 0x25A0
SUBR_BIAS = 107


def make_ttf() -> TTFont:
    options = subset.Options()
    options.layout_features = []
    options.hinting = False
    options.notdef_outline = True
    options.name_IDs = [1, 2]
    options.drop_tables += ["GDEF", "GPOS", "GSUB", "STAT", "gasp", "DSIG"]
    font = TTFont(SOURCE, recalcTimestamp=False)
    subsetter = subset.Subsetter(options)
    subsetter.populate(text=TEXT)
    subsetter.subset(font)
    return font


def gpos_kern_pairs(font: TTFont, glyphs: list[str]) -> dict[tuple[str, str], int]:
    """The x advance adjustment Roboto's GPOS `kern` feature gives each pair of [glyphs], nonzero only."""
    gpos = font["GPOS"].table
    lookups = sorted(
        {index for record in gpos.FeatureList.FeatureRecord if record.FeatureTag == "kern" for index in record.Feature.LookupListIndex}
    )
    pairs = {}
    for first in glyphs:
        for second in glyphs:
            total = 0
            for index in lookups:
                lookup = gpos.LookupList.Lookup[index]
                for subtable in lookup.SubTable:
                    if lookup.LookupType == 9:
                        subtable = subtable.ExtSubTable
                    if first not in subtable.Coverage.glyphs:
                        continue
                    if subtable.Format == 1:
                        pair_set = subtable.PairSet[subtable.Coverage.glyphs.index(first)]
                        record = next((r for r in pair_set.PairValueRecord if r.SecondGlyph == second), None)
                        if record is None:
                            continue
                    else:
                        first_class = subtable.ClassDef1.classDefs.get(first, 0)
                        second_class = subtable.ClassDef2.classDefs.get(second, 0)
                        record = subtable.Class1Record[first_class].Class2Record[second_class]
                    total += getattr(record.Value1, "XAdvance", 0) if record.Value1 else 0
                    break
            if total:
                pairs[(first, second)] = total
    return pairs


def make_kern_ttf() -> TTFont:
    """A subset of KERN_TEXT whose kerning is Roboto's GPOS pairs moved into a format 0 `kern` table."""
    options = subset.Options()
    options.layout_features = []
    options.hinting = False
    options.notdef_outline = True
    options.name_IDs = [1, 2]
    options.drop_tables += ["GDEF", "GPOS", "GSUB", "STAT", "gasp", "DSIG"]
    source = TTFont(SOURCE, recalcTimestamp=False)
    cmap = source.getBestCmap()
    pairs = gpos_kern_pairs(source, [cmap[ord(char)] for char in KERN_TEXT])
    font = TTFont(SOURCE, recalcTimestamp=False)
    subsetter = subset.Subsetter(options)
    subsetter.populate(text=KERN_TEXT)
    subsetter.subset(font)
    table = KernTable_format_0()
    table.version, table.coverage, table.format = 0, 1, 0
    table.kernTable = dict(sorted(pairs.items()))
    kern = newTable("kern")
    kern.version = 0
    kern.kernTables = [table]
    font["kern"] = kern
    return font


def rect_program() -> list:
    """A 600 by 500 rectangle at x 100..700, y 0..500, every edge a flex variant."""
    return [
        800, 0, 500, "hstemhm", 100, 600, "vstemhm",
        "hintmask", bytes([0xC0]),
        100, 0, "rmoveto",
        100, 0, 100, 0, 100, 100, 100, 0, 100, "hflex1",
        0, 80, 0, 80, 0, 80, 0, 80, 0, 80, 100, "flex1",
        "cntrmask", bytes([0xC0]),
        -100, -100, 0, -100, -100, -100, -100, "hflex",
        0, -100, 0, -100, 0, -100, 0, -100, 0, -50, 0, -50, 50, "flex",
        "endchar",
    ]


def make_otf(ttf: TTFont) -> TTFont:
    order = ttf.getGlyphOrder() + ["rect"]
    glyph_set = ttf.getGlyphSet()
    metrics = dict(ttf["hmtx"].metrics)
    metrics["rect"] = (800, 100)
    charstrings = {}
    for name in ttf.getGlyphOrder():
        pen = T2CharStringPen(metrics[name][0], glyph_set, roundTolerance=0)
        glyph_set[name].draw(pen)
        charstrings[name] = pen.getCharString()
    charstrings["rect"] = T2CharString(program=rect_program())

    builder = FontBuilder(ttf["head"].unitsPerEm, isTTF=False)
    builder.setupGlyphOrder(order)
    cmap = dict(ttf.getBestCmap())
    cmap[RECT_CODEPOINT] = "rect"
    builder.setupCharacterMap(cmap)
    builder.setupHorizontalMetrics(metrics)
    builder.setupHorizontalHeader(ascent=ttf["hhea"].ascent, descent=ttf["hhea"].descent)
    builder.setupNameTable({"familyName": "Roboto Subset", "styleName": "Regular"})
    builder.setupOS2()
    builder.setupPost()
    builder.setupCFF("RobotoSubset-Regular", {"FullName": "Roboto Subset Regular"}, charstrings, {})
    builder.font["head"].created = ttf["head"].created
    builder.font["head"].modified = ttf["head"].modified

    format12 = CmapSubtable.newSubtable(12)
    format12.platformID, format12.platEncID, format12.language = 3, 10, 0
    format12.cmap = cmap
    builder.font["cmap"].tables = [format12]

    move_into_subroutines(builder.font)
    return builder.font


def move_into_subroutines(font: TTFont) -> None:
    """Moves 'H' into a local subroutine and 'g' into a global one, so both call paths run."""
    cff = font["CFF "].cff
    top = cff.topDictIndex[0]
    private = top.Private
    strings = top.CharStrings
    local, global_ = SubrsIndex(), GlobalSubrsIndex()
    for glyph, index, call in (("H", local, "callsubr"), ("g", global_, "callgsubr")):
        program = strings[glyph]
        program.decompile()
        body = list(program.program)
        assert body[-1] == "endchar", f"{glyph} does not end with endchar"
        index.append(T2CharString(program=body[:-1] + ["return"], private=private, globalSubrs=global_))
        strings[glyph] = T2CharString(program=[-SUBR_BIAS, call, "endchar"], private=private, globalSubrs=global_)
    private.Subrs = local
    cff.GlobalSubrs = global_
    for name in strings.keys():
        strings[name].private = private
        strings[name].globalSubrs = global_


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    ttf = make_ttf()
    ttf.save(OUT_DIR / "roboto-subset.ttf")
    ttf = TTFont(OUT_DIR / "roboto-subset.ttf", recalcTimestamp=False)
    otf = make_otf(ttf)
    otf.recalcTimestamp = False
    otf.save(OUT_DIR / "roboto-subset.otf")
    make_kern_ttf().save(OUT_DIR / "roboto-kern.ttf")
    for name in ("roboto-subset.ttf", "roboto-subset.otf", "roboto-kern.ttf"):
        print(f"{name:20} {(OUT_DIR / name).stat().st_size:>6,} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
