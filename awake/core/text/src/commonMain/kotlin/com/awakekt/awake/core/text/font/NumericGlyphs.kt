/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

/**
 * Single-glyph numeric substitutions (`tnum` and `pnum`) read from an OpenType `GSUB` table.
 *
 * Only `SingleSubst` lookups (LookupType 1, formats 1 and 2, directly or through LookupType 7 Extension)
 * of the requested numeric feature are applied, in lookup list order, with the first matching subtable in
 * each lookup deciding a glyph as a shaper does. Ligatures, contextual substitutions and general shaping
 * belong to #852.
 */
internal object NumericGlyphs {
    /**
     * Validates [font]'s `GSUB` header and its `tnum` and `pnum` single-substitution subtables when a
     * `GSUB` table is present, so a truncated or corrupt table fails [UiFonts.load] naming `'GSUB' table`.
     */
    fun validate(font: OpenTypeFont) {
        val gsub = font.tableOrNull("GSUB") ?: return
        val reader = GsubSingleSubst(gsub, font.glyphCount)
        reader.lookupsFor(PNUM_TAG)
        reader.lookupsFor(TNUM_TAG)
    }

    /**
     * Substituted glyph IDs for [codePoints] under [numerals], or null when [font] has no `GSUB` table,
     * does not list the feature, or leaves every code point's glyph unchanged.
     */
    fun read(font: OpenTypeFont, codePoints: IntArray, numerals: FontNumerals): IntArray? {
        val gsub = font.tableOrNull("GSUB") ?: return null
        val tag = when (numerals) {
            FontNumerals.Proportional -> PNUM_TAG
            FontNumerals.Tabular -> TNUM_TAG
        }
        val lookups = GsubSingleSubst(gsub, font.glyphCount).lookupsFor(tag)
        var changed = false
        val glyphs = if (lookups.isEmpty()) {
            IntArray(0)
        } else {
            IntArray(codePoints.size) { slot ->
                val base = font.glyphFor(codePoints[slot])
                var current = base
                if (base != 0) {
                    for (lookup in lookups) {
                        current = lookup.substitute(current)
                    }
                    if (current != base) changed = true
                }
                current
            }
        }
        return if (changed) glyphs else null
    }

    private const val PNUM_TAG = 0x706E756D
    private const val TNUM_TAG = 0x746E756D
}

private const val MAX_SUBTABLES = 1024

private class GsubSingleSubst(private val gsub: FontTable, private val glyphCount: Int) {
    private var subtablesRead = 0
    private val scriptList: Int
    private val featureList: Int
    private val lookupList: Int
    private val lookupCount: Int

    init {
        val major = gsub.u16(0)
        if (major != 1) gsub.fail("has major version $major, not 1")
        scriptList = gsub.u16(SCRIPT_LIST)
        featureList = gsub.u16(FEATURE_LIST)
        lookupList = gsub.u16(LOOKUP_LIST)
        gsub.u16(scriptList)
        gsub.u16(featureList)
        lookupCount = gsub.u16(lookupList)
    }

    fun lookupsFor(tag: Int): List<SingleSubstLookup> {
        val featureCount = gsub.u16(featureList)
        val matching = (0 until featureCount).filter { gsub.u32(featureList + 2 + FEATURE_RECORD * it) == tag }
        if (matching.isEmpty()) return emptyList()
        val listed = listedFeatures()
        val features = matching.filter { it in listed }.ifEmpty { matching }
        val indices = HashSet<Int>()
        for (feature in features) {
            val featureTable = featureList + gsub.u16(featureList + 2 + FEATURE_RECORD * feature + 4)
            for (index in 0 until gsub.u16(featureTable + 2)) {
                val lookupIndex = gsub.u16(featureTable + 4 + 2 * index)
                if (lookupIndex >= lookupCount) gsub.fail("names lookup $lookupIndex, past lookup count $lookupCount")
                indices += lookupIndex
            }
        }
        return indices.sorted().map { index ->
            SingleSubstLookup(singleSubtables(lookupList + gsub.u16(lookupList + 2 + 2 * index)))
        }
    }

    private fun listedFeatures(): Set<Int> {
        val listed = HashSet<Int>()
        for (script in 0 until gsub.u16(scriptList)) {
            val scriptTable = scriptList + gsub.u16(scriptList + 2 + SCRIPT_RECORD * script + 4)
            val defaultLangSys = gsub.u16(scriptTable)
            if (defaultLangSys != 0) collectLangSysFeatures(scriptTable + defaultLangSys, listed)
            for (record in 0 until gsub.u16(scriptTable + 2)) {
                val langSys = scriptTable + gsub.u16(scriptTable + 4 + LANG_SYS_RECORD * record + 4)
                collectLangSysFeatures(langSys, listed)
            }
        }
        return listed
    }

    private fun collectLangSysFeatures(langSys: Int, out: MutableSet<Int>) {
        for (index in 0 until gsub.u16(langSys + 4)) out += gsub.u16(langSys + 6 + 2 * index)
    }

    private fun singleSubtables(at: Int): List<SingleSubtable> {
        val type = gsub.u16(at)
        if (type != SINGLE_SUBST && type != EXTENSION_SUBST) return emptyList()
        val count = gsub.u16(at + 4)
        return (0 until count).mapNotNull { index ->
            if (++subtablesRead > MAX_SUBTABLES) gsub.fail("lists more than $MAX_SUBTABLES substitution subtables")
            val subtable = resolveSubtable(at, type, index) ?: return@mapNotNull null
            readSingleSubtable(subtable)
        }
    }

    private fun resolveSubtable(lookupAt: Int, type: Int, index: Int): Int? {
        var subtable = lookupAt + gsub.u16(lookupAt + 6 + 2 * index)
        if (type == EXTENSION_SUBST) {
            val format = gsub.u16(subtable)
            if (format != 1) gsub.fail("has an extension subtable in format $format")
            if (gsub.u16(subtable + 2) != SINGLE_SUBST) return null
            val offset = gsub.u32(subtable + 4)
            if (offset < 0) gsub.fail("has an extension offset past 2 GB")
            subtable += offset
        }
        return subtable
    }

    private fun readSingleSubtable(at: Int): SingleSubtable = when (val format = gsub.u16(at)) {
        1 -> DeltaSingleSubtable(gsub, at, glyphCount)
        2 -> ArraySingleSubtable(gsub, at, glyphCount)
        else -> gsub.fail("has a single substitution subtable in format $format")
    }

    private companion object {
        const val SCRIPT_LIST = 4
        const val FEATURE_LIST = 6
        const val LOOKUP_LIST = 8
        const val SCRIPT_RECORD = 6
        const val FEATURE_RECORD = 6
        const val LANG_SYS_RECORD = 6
        const val SINGLE_SUBST = 1
        const val EXTENSION_SUBST = 7
    }
}

private class SingleSubstLookup(private val subtables: List<SingleSubtable>) {
    fun substitute(glyph: Int): Int {
        for (subtable in subtables) {
            val target = subtable.substitute(glyph)
            if (target >= 0) return if (target == 0) glyph else target
        }
        return glyph
    }
}

private sealed class SingleSubtable(protected val table: FontTable, coverageAt: Int) {
    protected val coverage = GsubCoverage(table, coverageAt)

    /** Returns the substitute glyph ID when [glyph] is covered, or -1 when it is not. */
    fun substitute(glyph: Int): Int {
        val index = coverage.indexOf(glyph)
        return if (index < 0) -1 else substituteAt(glyph, index)
    }

    protected abstract fun substituteAt(glyph: Int, coverageIndex: Int): Int
}

/** `SingleSubst` format 1: adds a signed 16-bit delta modulo 65536 to every covered glyph ID. */
private class DeltaSingleSubtable(
    table: FontTable,
    at: Int,
    private val glyphCount: Int,
) : SingleSubtable(table, at + table.u16(at + 2)) {
    private val delta = table.s16(at + 4)

    init {
        coverage.validateDelta(delta, glyphCount)
    }

    override fun substituteAt(glyph: Int, coverageIndex: Int): Int {
        val target = (glyph + delta) and GLYPH_ID_MASK
        if (target >= glyphCount) table.fail("substitutes glyph $target, past glyph count $glyphCount")
        return target
    }

    private companion object {
        const val GLYPH_ID_MASK = 0xFFFF
    }
}

/** `SingleSubst` format 2: maps each coverage index to an explicit substitute glyph ID. */
private class ArraySingleSubtable(
    table: FontTable,
    at: Int,
    glyphCount: Int,
) : SingleSubtable(table, at + table.u16(at + 2)) {
    private val substitutes: IntArray

    init {
        val count = table.u16(at + 4)
        substitutes = IntArray(count) { index ->
            table.u16(at + 6 + 2 * index).also { target ->
                if (target >= glyphCount) table.fail("substitutes glyph $target, past glyph count $glyphCount")
            }
        }
    }

    override fun substituteAt(glyph: Int, coverageIndex: Int): Int {
        if (coverageIndex >= substitutes.size) {
            table.fail("lists coverage index $coverageIndex, past ${substitutes.size} substitutes")
        }
        return substitutes[coverageIndex]
    }
}

private class GsubCoverage(private val table: FontTable, private val at: Int) {
    private val format = table.u16(at)
    private val count = table.u16(at + 2)

    init {
        when (format) {
            1 -> if (count > 0) table.u16(at + 4 + 2 * (count - 1))
            2 -> for (index in 0 until count) {
                val record = at + 4 + RANGE_RECORD * index
                val start = table.u16(record)
                val end = table.u16(record + 2)
                table.u16(record + 4)
                if (start > end) table.fail("has a coverage range $start..$end out of order")
            }
            else -> table.fail("has a coverage table in format $format")
        }
    }

    fun validateDelta(delta: Int, glyphCount: Int) {
        fun check(glyph: Int) {
            val target = (glyph + delta) and GLYPH_ID_MASK
            if (target >= glyphCount) table.fail("substitutes glyph $target, past glyph count $glyphCount")
        }
        when (format) {
            1 -> for (index in 0 until count) check(table.u16(at + 4 + 2 * index))
            2 -> for (index in 0 until count) {
                val record = at + 4 + RANGE_RECORD * index
                val start = table.u16(record)
                val end = table.u16(record + 2)
                check(start)
                check(end)
                val wrapPeak = (GLYPH_ID_MASK - delta) and GLYPH_ID_MASK
                if (wrapPeak in start..end) check(wrapPeak)
            }
        }
    }

    fun indexOf(glyph: Int): Int = when (format) {
        1 -> searchCoverage(count) { table.u16(at + 4 + 2 * it) - glyph }
        2 -> {
            val range = searchCoverage(count) { index ->
                val record = at + 4 + RANGE_RECORD * index
                when {
                    glyph < table.u16(record) -> 1
                    glyph > table.u16(record + 2) -> -1
                    else -> 0
                }
            }
            if (range < 0) {
                -1
            } else {
                val record = at + 4 + RANGE_RECORD * range
                table.u16(record + 4) + glyph - table.u16(record)
            }
        }
        else -> -1
    }

    private inline fun searchCoverage(size: Int, compare: (Int) -> Int): Int {
        var low = 0
        var high = size - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val order = compare(middle)
            when {
                order < 0 -> low = middle + 1
                order > 0 -> high = middle - 1
                else -> return middle
            }
        }
        return -1
    }

    private companion object {
        const val RANGE_RECORD = 6
        const val GLYPH_ID_MASK = 0xFFFF
    }
}
