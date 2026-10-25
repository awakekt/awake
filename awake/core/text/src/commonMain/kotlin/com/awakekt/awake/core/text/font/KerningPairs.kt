/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

/**
 * A font's pair kerning between the code points it draws, in em: what to add to the pen after the
 * left glyph's advance when the right one follows it, usually negative.
 *
 * Read once, when the font loads ([read]), and kept as one sorted key array and one value array, so
 * a lookup during layout is a binary search with no allocation. Only the code points the font's atlas
 * holds are kept: a pair a font can never draw is never asked for.
 */
internal class KerningPairs internal constructor(private val keys: LongArray, private val valuesEm: FloatArray) {
    /** The number of kerned pairs. */
    val size: Int get() = keys.size

    /** The adjustment, in em, after [left] when [right] follows it; 0 for a pair the font does not kern. */
    fun em(left: Int, right: Int): Float {
        val keys = keys
        var low = 0
        var high = keys.size - 1
        val key = pairKey(left, right)
        while (low <= high) {
            val middle = (low + high) ushr 1
            val probe = keys[middle]
            when {
                probe < key -> low = middle + 1
                probe > key -> high = middle - 1
                else -> return valuesEm[middle]
            }
        }
        return 0f
    }

    companion object {
        /** No kerning: a font with no kerning data, or one that has nothing to kern. */
        val EMPTY: KerningPairs = KerningPairs(LongArray(0), FloatArray(0))

        /**
         * The pairs [font] kerns among [codePoints]: the x-advance adjustments of the pair positioning
         * (`PairPos`, formats 1 and 2) lookups of the `kern` feature in `GPOS`, or, for a font whose
         * `GPOS` has no `kern` feature, the format 0 subtables of its `kern` table. A browser reads the same
         * two sources in the same order.
         *
         * Pair kerning only: placement, vertical and second-glyph adjustments, device tables and contextual
         * positioning are left to full shaping (#852).
         *
         * @throws IllegalArgumentException when a kerning table is truncated or corrupt, naming the table.
         */
        fun read(font: OpenTypeFont, codePoints: IntArray): KerningPairs {
            val atlas = AtlasGlyphs(font, codePoints)
            val pairs = PairCollector(codePoints)
            val gpos = font.tableOrNull("GPOS")
            val fromGpos = gpos != null && GposKerning(gpos, atlas, pairs).collect()
            if (!fromGpos) font.tableOrNull("kern")?.let { KernTable(it, atlas, pairs).collect() }
            return pairs.build(font.unitsPerEm)
        }

        /** Both code points in one sortable key, left in the high half. */
        fun pairKey(left: Int, right: Int): Long = (left.toLong() shl Int.SIZE_BITS) or (right.toLong() and LOW_HALF)

        private const val LOW_HALF = 0xFFFFFFFFL
    }
}

/** The most kerning subtables one font may make a reader visit, so a few bytes cannot ask for billions. */
private const val MAX_SUBTABLES = 1024

/** Pairs gathered in font units, keyed by atlas indices. */
private class PairCollector(private val codePoints: IntArray) {
    private val units = HashMap<Long, Int>()

    private fun key(left: Int, right: Int): Long = left.toLong() * codePoints.size + right

    /** Adds [value] to the pair's adjustment, as a later lookup or `kern` subtable does. */
    fun add(left: Int, right: Int, value: Int) {
        if (value != 0) units[key(left, right)] = (units[key(left, right)] ?: 0) + value
    }

    /** Replaces the pair's adjustment, as a `kern` subtable with its override bit set does. */
    fun replace(left: Int, right: Int, value: Int) {
        units[key(left, right)] = value
    }

    fun build(unitsPerEm: Int): KerningPairs {
        val entries = units.entries
            .filter { it.value != 0 }
            .map { (key, value) ->
                val left = codePoints[(key / codePoints.size).toInt()]
                val right = codePoints[(key % codePoints.size).toInt()]
                KerningPairs.pairKey(left, right) to value / unitsPerEm.toFloat()
            }
            .sortedBy { it.first }
        if (entries.isEmpty()) return KerningPairs.EMPTY
        return KerningPairs(LongArray(entries.size) { entries[it].first }, FloatArray(entries.size) { entries[it].second })
    }
}

/** The glyph of each atlas code point, and the atlas indices each glyph is drawn for. */
private class AtlasGlyphs(font: OpenTypeFont, codePoints: IntArray) {
    val size: Int = codePoints.size

    /** Each atlas index's glyph, 0 for a code point the font lacks, which never kerns. */
    val glyphs: IntArray = IntArray(size) { font.glyphFor(codePoints[it]) }

    private val firstIndexOfGlyph = HashMap<Int, Int>()
    private val nextIndexOfSameGlyph = IntArray(size) { -1 }

    init {
        for (index in size - 1 downTo 0) {
            val glyph = glyphs[index]
            if (glyph == 0) continue
            firstIndexOfGlyph[glyph]?.let { nextIndexOfSameGlyph[index] = it }
            firstIndexOfGlyph[glyph] = index
        }
    }

    /** Calls [action] with every atlas index drawn with [glyph]; none for `.notdef` or a glyph no atlas code point maps to. */
    inline fun forEachIndexOf(glyph: Int, action: (Int) -> Unit) {
        var index = if (glyph == 0) -1 else firstIndexOfGlyph[glyph] ?: -1
        while (index >= 0) {
            action(index)
            index = nextIndexOfSameGlyph[index]
        }
    }
}

/**
 * The pair positioning lookups of `GPOS`'s `kern` feature, applied as a shaper applies them: lookups in
 * lookup list order, each adding its adjustment, and within a lookup the first subtable that matches a
 * pair deciding it. A format 1 subtable matches a pair it lists; a format 2 subtable matches every pair
 * whose first glyph it covers, so a class pair it gives no value stops later subtables from kerning it.
 *
 * The feature is taken from every script's default language system, so Latin, Greek and Cyrillic text
 * in one font are all kerned. A lookup's flags are not read: the atlas pairs are base glyphs, and a
 * mark inside a grapheme cluster is skipped by layout, which kerns one cluster's first glyph to the next's.
 */
private class GposKerning(private val gpos: FontTable, private val atlas: AtlasGlyphs, private val out: PairCollector) {
    private var subtablesRead = 0

    /** Adds the `kern` feature's pairs to [out]; false when `GPOS` has no `kern` feature. */
    fun collect(): Boolean {
        val lookups = kernLookups()
        if (lookups.isEmpty()) return false
        val lookupList = gpos.u16(LOOKUP_LIST)
        val lookupCount = gpos.u16(lookupList)
        val subtables = lookups.filter { it < lookupCount }.map { index -> pairSubtables(lookupList + gpos.u16(lookupList + 2 + 2 * index)) }
        val row = IntArray(atlas.size)
        val decided = BooleanArray(atlas.size)
        for (left in 0 until atlas.size) {
            if (atlas.glyphs[left] == 0 || subtables.none { lookup -> lookup.any { it.covers(left) } }) continue
            row.fill(0)
            for (lookup in subtables) {
                decided.fill(false)
                for (subtable in lookup) subtable.apply(left, row, decided)
            }
            for (right in row.indices) if (atlas.glyphs[right] != 0) out.add(left, right, row[right])
        }
        return true
    }

    /** The lookup indices of every `kern` feature a script's default language system lists, in lookup list order. */
    private fun kernLookups(): List<Int> {
        if (gpos.u16(0) != 1) gpos.fail("has major version ${gpos.u16(0)}, not 1")
        val scriptList = gpos.u16(SCRIPT_LIST)
        val featureList = gpos.u16(FEATURE_LIST)
        val featureCount = gpos.u16(featureList)
        val kernFeatures = (0 until featureCount).filter { gpos.u32(featureList + 2 + 6 * it) == KERN_TAG }
        if (kernFeatures.isEmpty()) return emptyList()
        val listed = HashSet<Int>()
        for (script in 0 until gpos.u16(scriptList)) {
            val scriptTable = scriptList + gpos.u16(scriptList + 2 + 6 * script + 4)
            val defaultLangSys = gpos.u16(scriptTable)
            if (defaultLangSys == 0) continue
            val langSys = scriptTable + defaultLangSys
            for (index in 0 until gpos.u16(langSys + 4)) listed += gpos.u16(langSys + 6 + 2 * index)
        }
        val features = kernFeatures.filter { it in listed }.ifEmpty { kernFeatures }
        val lookups = HashSet<Int>()
        for (feature in features) {
            val featureTable = featureList + gpos.u16(featureList + 2 + 6 * feature + 4)
            for (index in 0 until gpos.u16(featureTable + 2)) lookups += gpos.u16(featureTable + 4 + 2 * index)
        }
        return lookups.sorted()
    }

    /** The pair positioning subtables of the lookup at [at], through any extension subtables; none for another lookup type. */
    private fun pairSubtables(at: Int): List<PairSubtable> {
        val type = gpos.u16(at)
        if (type != PAIR_POSITIONING && type != EXTENSION) return emptyList()
        val count = gpos.u16(at + 4)
        return (0 until count).mapNotNull { index ->
            if (++subtablesRead > MAX_SUBTABLES) gpos.fail("lists more than $MAX_SUBTABLES kerning subtables")
            var subtable = at + gpos.u16(at + 6 + 2 * index)
            if (type == EXTENSION) {
                if (gpos.u16(subtable + 2) != PAIR_POSITIONING) return@mapNotNull null
                subtable += gpos.u32(subtable + 4).also { if (it < 0) gpos.fail("has an extension offset past 2 GB") }
            }
            when (gpos.u16(subtable)) {
                1 -> GlyphPairs(gpos, subtable, atlas)
                2 -> ClassPairs(gpos, subtable, atlas)
                else -> null
            }
        }
    }

    private companion object {
        const val SCRIPT_LIST = 4
        const val FEATURE_LIST = 6
        const val LOOKUP_LIST = 8
        const val PAIR_POSITIONING = 2
        const val EXTENSION = 9
        const val KERN_TAG = 0x6B65726E
    }
}

/** One `PairPos` subtable, read for the atlas glyphs. */
private abstract class PairSubtable(protected val table: FontTable, protected val at: Int, atlas: AtlasGlyphs) {
    private val valueFormat1 = table.u16(at + 4)
    private val valueFormat2 = table.u16(at + 6)

    /** Bytes in one pair's two value records. */
    protected val recordSize: Int = valueRecordSize(valueFormat1) + valueRecordSize(valueFormat2)

    /** Where the first value record's x advance sits in a pair's records, or -1 when it has none. */
    protected val xAdvanceOffset: Int =
        if (valueFormat1 and X_ADVANCE != 0) 2 * (valueFormat1 and (X_ADVANCE - 1)).countOneBits() else -1

    /** Each atlas glyph's index in this subtable's coverage, -1 when it is not covered. */
    private val coverage: IntArray = coverageIndices(table, at + table.u16(at + 2), atlas)

    fun covers(left: Int): Boolean = coverage[left] >= 0

    /**
     * Decides the pairs whose first glyph is atlas index [left] that no earlier subtable of the lookup has
     * decided, adding each one's x advance adjustment to [row].
     */
    fun apply(left: Int, row: IntArray, decided: BooleanArray) {
        val index = coverage[left]
        if (index >= 0) apply(left, index, row, decided)
    }

    protected abstract fun apply(left: Int, coverageIndex: Int, row: IntArray, decided: BooleanArray)

    /** The first value record's x advance in the pair records at [records], or 0 when it has none. */
    protected fun xAdvance(records: Int): Int = if (xAdvanceOffset < 0) 0 else table.s16(records + xAdvanceOffset)

    private companion object {
        const val X_ADVANCE = 0x0004
        const val VALUE_FIELDS = 0x00FF

        fun valueRecordSize(format: Int): Int = 2 * (format and VALUE_FIELDS).countOneBits()
    }
}

/** `PairPos` format 1: each covered first glyph lists the second glyphs it kerns with. */
private class GlyphPairs(table: FontTable, at: Int, private val atlas: AtlasGlyphs) : PairSubtable(table, at, atlas) {
    private val pairSetCount = table.u16(at + 8)

    override fun apply(left: Int, coverageIndex: Int, row: IntArray, decided: BooleanArray) {
        if (coverageIndex >= pairSetCount) return
        val pairSet = at + table.u16(at + 10 + 2 * coverageIndex)
        val stride = 2 + recordSize
        for (pair in 0 until table.u16(pairSet)) {
            val record = pairSet + 2 + stride * pair
            val value = xAdvance(record + 2)
            atlas.forEachIndexOf(table.u16(record)) { right ->
                if (!decided[right]) {
                    decided[right] = true
                    row[right] += value
                }
            }
        }
    }
}

/** `PairPos` format 2: a value for each pair of first-glyph and second-glyph classes. */
private class ClassPairs(table: FontTable, at: Int, atlas: AtlasGlyphs) : PairSubtable(table, at, atlas) {
    private val firstClasses = classIndices(table, at + table.u16(at + 8), atlas)
    private val secondClasses = classIndices(table, at + table.u16(at + 10), atlas)
    private val firstClassCount = table.u16(at + 12)
    private val secondClassCount = table.u16(at + 14)
    private val records = at + 16

    override fun apply(left: Int, coverageIndex: Int, row: IntArray, decided: BooleanArray) {
        val first = firstClasses[left]
        if (first >= firstClassCount) return
        val rowStart = records + first * secondClassCount * recordSize
        for (right in row.indices) {
            val second = secondClasses[right]
            if (decided[right] || second >= secondClassCount) continue
            decided[right] = true
            row[right] += xAdvance(rowStart + second * recordSize)
        }
    }
}

/** Each atlas glyph's index in the coverage table at [at], or -1; both formats are searched as sorted, as a shaper does. */
private fun coverageIndices(table: FontTable, at: Int, atlas: AtlasGlyphs): IntArray {
    val format = table.u16(at)
    val count = table.u16(at + 2)
    return IntArray(atlas.size) { index ->
        val glyph = atlas.glyphs[index]
        when {
            glyph == 0 -> -1
            format == 1 -> searchSorted(count) { table.u16(at + 4 + 2 * it) - glyph }
            format == 2 -> {
                val range = searchRanges(table, at + 4, count, RANGE_RECORD, glyph)
                if (range < 0) -1 else table.u16(at + 4 + RANGE_RECORD * range + 4) + glyph - table.u16(at + 4 + RANGE_RECORD * range)
            }
            else -> table.fail("has a coverage table in format $format")
        }
    }
}

/** Each atlas glyph's class in the class definition table at [at]; 0 for a glyph it does not list. */
private fun classIndices(table: FontTable, at: Int, atlas: AtlasGlyphs): IntArray {
    val format = table.u16(at)
    return IntArray(atlas.size) { index ->
        val glyph = atlas.glyphs[index]
        when (format) {
            1 -> {
                val offset = glyph - table.u16(at + 2)
                if (offset in 0 until table.u16(at + 4)) table.u16(at + 6 + 2 * offset) else 0
            }
            2 -> {
                val range = searchRanges(table, at + 4, table.u16(at + 2), RANGE_RECORD, glyph)
                if (range < 0) 0 else table.u16(at + 4 + RANGE_RECORD * range + 4)
            }
            else -> table.fail("has a class definition table in format $format")
        }
    }
}

/** The index of the range record, `start, end, value` from [at], holding [glyph], or -1. */
private fun searchRanges(table: FontTable, at: Int, count: Int, stride: Int, glyph: Int): Int =
    searchSorted(count) { index ->
        val record = at + stride * index
        when {
            glyph < table.u16(record) -> 1
            glyph > table.u16(record + 2) -> -1
            else -> 0
        }
    }

/** A binary search over `0 until count` by [compare] (negative: the item sorts before the target), or -1. */
private inline fun searchSorted(count: Int, compare: (Int) -> Int): Int {
    var low = 0
    var high = count - 1
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

private const val RANGE_RECORD = 6

/**
 * The `kern` table's horizontal format 0 subtables, in the OpenType layout (version 0) or Apple's (version 1):
 * pairs of glyphs, each with its adjustment in font units. Subtables add up, and one with its override bit set
 * replaces what came before. Minimum, cross-stream, vertical and variation subtables, and other formats, are not
 * kerning a run's advances, so they are skipped.
 */
private class KernTable(private val kern: FontTable, private val atlas: AtlasGlyphs, private val out: PairCollector) {
    fun collect() {
        val apple = kern.u16(0) == 1
        var at = if (apple) APPLE_HEADER else HEADER
        val count = if (apple) kern.u32(4) else kern.u16(2)
        if (count !in 0..MAX_SUBTABLES) kern.fail("lists $count subtables, more than $MAX_SUBTABLES")
        repeat(count) {
            val length = if (apple) kern.u32(at) else kern.u16(at + 2)
            val coverage = kern.u16(at + 4)
            val format = if (apple) coverage and BYTE else coverage ushr BYTE_BITS
            val kerns = if (apple) coverage and APPLE_SKIP == 0 else coverage and HORIZONTAL != 0 && coverage and OPENTYPE_SKIP == 0
            val override = !apple && coverage and OVERRIDE != 0
            val body = at + if (apple) APPLE_SUBTABLE_HEADER else SUBTABLE_HEADER
            if (format == 0 && kerns) readPairs(body, override)
            // An OpenType subtable's 16-bit length overflows in a large format 0 subtable; its pair count does not.
            val next = if (format == 0) maxOf(at + length, body + PAIRS_HEADER + PAIR_SIZE * kern.u16(body)) else at + length
            if (next <= at) kern.fail("has a subtable with no length")
            at = next
        }
    }

    private fun readPairs(at: Int, override: Boolean) {
        for (pair in 0 until kern.u16(at)) {
            val record = at + PAIRS_HEADER + PAIR_SIZE * pair
            val value = kern.s16(record + 4)
            atlas.forEachIndexOf(kern.u16(record)) { left ->
                atlas.forEachIndexOf(kern.u16(record + 2)) { right ->
                    if (override) out.replace(left, right, value) else out.add(left, right, value)
                }
            }
        }
    }

    private companion object {
        const val HEADER = 4
        const val APPLE_HEADER = 8
        const val SUBTABLE_HEADER = 6
        const val APPLE_SUBTABLE_HEADER = 8
        const val PAIRS_HEADER = 8
        const val PAIR_SIZE = 6
        const val BYTE = 0xFF
        const val BYTE_BITS = 8
        const val HORIZONTAL = 0x0001
        const val OVERRIDE = 0x0008

        /** Minimum values and cross-stream kerning. */
        const val OPENTYPE_SKIP = 0x0006

        /** Vertical, cross-stream and variation kerning. */
        const val APPLE_SKIP = 0xE000
    }
}
