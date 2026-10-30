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
- awake-cjk-test.ttf keeps Roboto Regular's unkerned ASCII and common punctuation metrics, and adds
  1-em fullwidth and 0.5-em halfwidth TrueType glyphs across the Han, Hiragana, Katakana, Hangul
  and East Asian punctuation code points used by the CJK line-breaking conformance suite.
- roboto-numerals.ttf maps `0`..`9` in `cmap` to Roboto Regular's proportional (`pnum`) figures and
  maps them to Roboto Regular's equal-width (`1151`-unit) tabular figures through `GSUB` `tnum`
  using both `SingleSubst` format 1 (delta over a format 2 range coverage) and `SingleSubst` format 2
  wrapped in a LookupType 7 `ExtensionSubst` (over a format 1 list coverage).

Both are small enough to mutate byte by byte in a test. Timestamps are kept, so a re-run writes
the same bytes.

Usage:
    python3 tools/fonts-tooling/make_test_fonts.py
"""

from __future__ import annotations

import struct
from pathlib import Path

from fontTools import subset
from fontTools.cffLib import GlobalSubrsIndex, SubrsIndex
from fontTools.fontBuilder import FontBuilder
from fontTools.misc.psCharStrings import T2CharString
from fontTools.pens.t2CharStringPen import T2CharStringPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont, newTable
from fontTools.ttLib.tables.DefaultTable import DefaultTable
from fontTools.ttLib.tables._c_m_a_p import CmapSubtable
from fontTools.ttLib.tables._k_e_r_n import KernTable_format_0

REPO = Path(__file__).resolve().parent.parent.parent
SOURCE = REPO / "awake/ui/font-atlas-generator/fonts/Roboto-Regular.ttf"
OUT_DIR = REPO / "awake/core/text/src/commonTest/resources/fonts"
TEXT = " Hg.:?Ä"
KERN_TEXT = " AVTWYaeory.,/"
CJK_LATIN_TEXT = "".join(chr(c) for c in range(0x20, 0x7F)) + "\u00A0\u0301\u2010\u2014\u2018\u2019\u201C\u201D\u2026"
CJK_EXTRA_FULLWIDTH = (
    "港口小镇的灯火缓缓亮起石桥仓库与木船在水面映出暖光今晚风浪平稳潮位正常可以按计划启航"
    "船长翻开航行日志记录下潮汐时刻随后走向码头第号在开放会加载含盏灯再切换到店"
    "町桟橋朝霧漂灯台静揺北埠頭三搬入準備完了係員点検表確認出発合図送案内板記作業順番荷物運"
    "항구마을의등불이잔잔한바다위에비치고어부들은새벽출항을차분히준비합니다바람이고요해지면"
    "목선들이등대옆수로를따라천천히나아갑니다국제해상운송컨테이너터미널자동화관제시스템倉庫내안"
)
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


def _box_tt_glyph(x0: int, y0: int, x1: int, y1: int):
    pen = TTGlyphPen(None)
    pen.moveTo((x0, y0))
    pen.lineTo((x0, y1))
    pen.lineTo((x1, y1))
    pen.lineTo((x1, y0))
    pen.closePath()
    return pen.glyph()


def make_cjk_ttf() -> TTFont:
    """Roboto Regular's ASCII/punctuation plus deterministic 1-em fullwidth and 0.5-em halfwidth CJK glyphs."""
    options = subset.Options()
    options.layout_features = []
    options.hinting = False
    options.notdef_outline = True
    options.name_IDs = [1, 2, 4, 6]
    options.drop_tables += ["GDEF", "GPOS", "GSUB", "STAT", "gasp", "DSIG"]
    font = TTFont(SOURCE, recalcTimestamp=False)
    subsetter = subset.Subsetter(options)
    subsetter.populate(text=CJK_LATIN_TEXT)
    subsetter.subset(font)

    order = list(font.getGlyphOrder()) + ["cjk_full", "cjk_half"]
    font.setGlyphOrder(order)
    glyf = font["glyf"]
    hmtx = font["hmtx"]
    glyf["cjk_full"] = _box_tt_glyph(128, -200, 1920, 1600)
    glyf["cjk_half"] = _box_tt_glyph(64, -200, 960, 1600)
    hmtx.metrics["cjk_full"] = (2048, 128)
    hmtx.metrics["cjk_half"] = (1024, 64)

    cmap = dict(font.getBestCmap())
    fullwidth_cps = (
        list(range(0x3000, 0x3040))
        + list(range(0x3041, 0x3097))
        + list(range(0x3099, 0x3100))
        + list(range(0xFF01, 0xFF61))
        + [ord(ch) for ch in CJK_EXTRA_FULLWIDTH]
        + [0x20000]
    )
    for cp in sorted(set(fullwidth_cps)):
        cmap.setdefault(cp, "cjk_full")
    for cp in range(0xFF61, 0xFFA0):
        cmap.setdefault(cp, "cjk_half")

    format4 = CmapSubtable.newSubtable(4)
    format4.platformID, format4.platEncID, format4.language = 3, 1, 0
    format4.cmap = {cp: name for cp, name in cmap.items() if cp <= 0xFFFF}
    format12 = CmapSubtable.newSubtable(12)
    format12.platformID, format12.platEncID, format12.language = 3, 10, 0
    format12.cmap = cmap
    font["cmap"].tables = [format4, format12]
    font["maxp"].numGlyphs = len(order)
    font.recalcTimestamp = False
    return font


def _raw_gsub_table(pnum_ids: list[int], tnum_ids: list[int]) -> bytes:
    """Builds a deterministic binary GSUB v1.0 table for `tnum`:
    - Lookup 0 (LookupType 1, SingleSubst format 1 delta, Coverage format 2 range) for digits 0..4.
    - Lookup 1 (LookupType 7 Extension -> LookupType 1, SingleSubst format 2 array, Coverage format 1 list) for 5..9.
    """
    # Consecutive glyph IDs: pnum_ids = [g0..g9], tnum_ids = [t0..t9] with constant delta for 0..4.
    delta = (tnum_ids[0] - pnum_ids[0]) & 0xFFFF
    assert all(((pnum_ids[i] + delta) & 0xFFFF) == tnum_ids[i] for i in range(5))

    # Coverage 0 (format 2, 1 range: pnum_ids[0]..pnum_ids[4], startCoverageIndex = 0): 10 bytes
    cov0 = struct.pack(">HHHHHH", 2, 1, pnum_ids[0], pnum_ids[4], 0, 0)[:10]
    # SingleSubst 0 (format 1: format=1, coverageOffset=6, deltaGlyphID): 6 + 10 = 16 bytes
    sub0 = struct.pack(">HHH", 1, 6, delta) + cov0
    # Lookup 0 (type=1, flag=0, subTableCount=1, subtableOffset=8): 8 + 16 = 24 bytes
    lookup0 = struct.pack(">HHHH", 1, 0, 1, 8) + sub0

    # Coverage 1 (format 1, 5 glyphs: pnum_ids[5..9]): 4 + 10 = 14 bytes
    cov1 = struct.pack(">HH5H", 1, 5, *pnum_ids[5:10])
    # SingleSubst 1 (format 2: format=2, coverageOffset=16, glyphCount=5, 5 target IDs): 16 + 14 = 30 bytes
    sub1 = struct.pack(">HHH5H", 2, 16, 5, *tnum_ids[5:10]) + cov1
    # ExtensionSubst (format=1, extensionLookupType=1, extensionOffset=8): 8 + 30 = 38 bytes
    ext1 = struct.pack(">HHI", 1, 1, 8) + sub1
    # Lookup 1 (type=7, flag=0, subTableCount=1, subtableOffset=8): 8 + 38 = 46 bytes
    lookup1 = struct.pack(">HHHH", 7, 0, 1, 8) + ext1

    # Header (10 bytes): major=1, minor=0, scriptList=10, featureList=30, lookupList=48
    # ScriptList (20 bytes at 10): count=1, tag='DFLT', offset=8 -> Script(defaultLangSys=4, count=0)
    #   -> LangSys(lookupOrder=0, reqFeature=0xFFFF, featureCount=1, featureIndices=[0])
    script_list = struct.pack(">H4sHHHHHHH", 1, b"DFLT", 8, 4, 0, 0, 0xFFFF, 1, 0)
    # FeatureList (18 bytes at 30): count=1, tag='tnum', offset=8 -> Feature(params=0, count=2, lookups=[0, 1])
    feature_list = struct.pack(">H4sHHHHH", 1, b"tnum", 8, 0, 2, 0, 1)
    # LookupList (at 48): count=2, offsets=[6, 6 + len(lookup0)]
    lookup_list = struct.pack(">HHH", 2, 6, 6 + len(lookup0)) + lookup0 + lookup1

    header = struct.pack(">HHHHH", 1, 0, 10, 10 + len(script_list), 10 + len(script_list) + len(feature_list))
    return header + script_list + feature_list + lookup_list


def make_numerals_ttf() -> TTFont:
    """Proportional (`pnum`) digits by default in `cmap`, switched to equal-width tabular (`tnum`) via GSUB."""
    source = TTFont(SOURCE, recalcTimestamp=False)
    cmap = source.getBestCmap()
    gsub = source["GSUB"].table

    def feature_map(tag: str) -> dict[str, str]:
        mapping: dict[str, str] = {}
        for rec in gsub.FeatureList.FeatureRecord:
            if rec.FeatureTag == tag:
                for lidx in rec.Feature.LookupListIndex:
                    for st in gsub.LookupList.Lookup[lidx].SubTable:
                        mapping.update(st.mapping)
        return mapping

    pnum_map = feature_map("pnum")
    tnum_map = feature_map("tnum")
    digits = "0123456789"
    pnum_sources = [pnum_map[cmap[ord(d)]] for d in digits]
    tnum_sources = [tnum_map.get(cmap[ord(d)], cmap[ord(d)]) for d in digits]

    options = subset.Options()
    options.layout_features = ["kern"]
    options.hinting = False
    options.notdef_outline = True
    options.name_IDs = [1, 2]
    options.drop_tables += ["GDEF", "GSUB", "STAT", "gasp", "DSIG"]
    font = TTFont(SOURCE, recalcTimestamp=False)
    subsetter = subset.Subsetter(options)
    subsetter.populate(text=" Hx0123456789.,:?$-")
    subsetter.subset(font)

    # Copy exact proportional and tabular digit outlines & advances in consecutive glyph order so
    # Format 1 delta (0..4) and Format 2 array + Extension (5..9) are both exercised deterministically.
    glyf = font["glyf"]
    hmtx = font["hmtx"]
    src_glyf = source["glyf"]
    src_hmtx = source["hmtx"]
    order = list(font.getGlyphOrder())
    p_names = [f"num_p_{d}" for d in digits]
    t_names = [f"num_t_{d}" for d in digits]
    for dst, src_name in zip(p_names, pnum_sources):
        g = src_glyf[src_name]
        pen = TTGlyphPen(source.getGlyphSet())
        source.getGlyphSet()[src_name].draw(pen)
        glyf[dst] = pen.glyph()
        hmtx.metrics[dst] = src_hmtx.metrics[src_name]
    for dst, src_name in zip(t_names, tnum_sources):
        pen = TTGlyphPen(source.getGlyphSet())
        source.getGlyphSet()[src_name].draw(pen)
        glyf[dst] = pen.glyph()
        hmtx.metrics[dst] = src_hmtx.metrics[src_name]

    order.extend(p_names)
    order.extend(t_names)
    font.setGlyphOrder(order)
    font["maxp"].numGlyphs = len(order)

    new_cmap = dict(font.getBestCmap())
    for d, p_name in zip(digits, p_names):
        new_cmap[ord(d)] = p_name
    format4 = CmapSubtable.newSubtable(4)
    format4.platformID, format4.platEncID, format4.language = 3, 1, 0
    format4.cmap = {cp: name for cp, name in new_cmap.items() if cp <= 0xFFFF}
    font["cmap"].tables = [format4]

    p_ids = [order.index(name) for name in p_names]
    t_ids = [order.index(name) for name in t_names]
    gsub_table = DefaultTable("GSUB")
    gsub_table.data = _raw_gsub_table(p_ids, t_ids)
    font["GSUB"] = gsub_table
    font.recalcTimestamp = False
    return font


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    ttf = make_ttf()
    ttf.save(OUT_DIR / "roboto-subset.ttf")
    ttf = TTFont(OUT_DIR / "roboto-subset.ttf", recalcTimestamp=False)
    otf = make_otf(ttf)
    otf.recalcTimestamp = False
    otf.save(OUT_DIR / "roboto-subset.otf")
    make_kern_ttf().save(OUT_DIR / "roboto-kern.ttf")
    make_cjk_ttf().save(OUT_DIR / "awake-cjk-test.ttf")
    make_numerals_ttf().save(OUT_DIR / "roboto-numerals.ttf")
    for name in ("roboto-subset.ttf", "roboto-subset.otf", "roboto-kern.ttf", "awake-cjk-test.ttf", "roboto-numerals.ttf"):
        print(f"{name:20} {(OUT_DIR / name).stat().st_size:>6,} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
