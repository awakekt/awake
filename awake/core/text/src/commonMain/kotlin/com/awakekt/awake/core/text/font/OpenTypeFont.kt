/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

/**
 * The tables of a TrueType or OpenType font that a UI atlas needs: units per em, the `hhea` line,
 * advances, the Unicode `cmap`, and outlines from `glyf` or `CFF `.
 *
 * Every read is bounds-checked against its table, so a truncated or corrupt file fails with an
 * [IllegalArgumentException] naming the table, never an index error.
 */
internal class OpenTypeFont(bytes: ByteArray, private val name: String) {
    private val tables: Map<String, FontTable> = readTableDirectory(bytes, name)

    val unitsPerEm: Int
    val ascender: Int
    val descender: Int
    val glyphCount: Int
    private val horizontalMetrics: FontTable
    private val horizontalMetricCount: Int
    private val glyphForCode: (Int) -> Int
    private val outlines: (Int, OutlineBuilder) -> Unit

    init {
        val head = table("head")
        unitsPerEm = head.u16(HEAD_UNITS_PER_EM)
        if (unitsPerEm !in MIN_UNITS_PER_EM..MAX_UNITS_PER_EM) head.fail("gives $unitsPerEm units per em")
        val hhea = table("hhea")
        ascender = hhea.s16(HHEA_ASCENDER)
        descender = hhea.s16(HHEA_DESCENDER)
        if (ascender - descender <= 0) hhea.fail("has no line height (ascender $ascender, descender $descender)")
        val maxp = table("maxp")
        glyphCount = maxp.u16(MAXP_GLYPH_COUNT)
        if (glyphCount == 0) maxp.fail("lists no glyphs")
        horizontalMetrics = table("hmtx")
        horizontalMetricCount = hhea.u16(HHEA_METRIC_COUNT)
        if (horizontalMetricCount !in 1..glyphCount) hhea.fail("gives $horizontalMetricCount horizontal metrics")
        glyphForCode = unicodeCharacterMap(table("cmap"))
        outlines = outlineReader(head)
    }

    /** The glyph [code] maps to, or 0 (`.notdef`) when the font has none. */
    fun glyphFor(code: Int): Int = glyphForCode(code).takeIf { it in 1 until glyphCount } ?: 0

    /** The advance of [glyph], in font units. */
    fun advance(glyph: Int): Int = horizontalMetrics.u16(4 * minOf(glyph, horizontalMetricCount - 1))

    /** The outline of [glyph], in font units, y up. */
    fun outline(glyph: Int): List<OutlineSegment> = OutlineBuilder().also { outlines(glyph, it) }.finish()

    private fun table(tag: String): FontTable =
        tables[tag] ?: throw IllegalArgumentException("$name has no '$tag' table")

    private fun outlineReader(head: FontTable): (Int, OutlineBuilder) -> Unit {
        val glyf = tables["glyf"]
        val cff = tables["CFF "]
        return when {
            glyf != null -> TrueTypeOutlines(glyf, table("loca"), head.s16(HEAD_LOCA_FORMAT) != 0, glyphCount)::outline
            cff != null -> CffOutlines(cff, glyphCount)::outline
            "CFF2" in tables -> table("CFF2").fail("holds variable outlines, which are not supported")
            else -> throw IllegalArgumentException("$name has no 'glyf' or 'CFF ' table")
        }
    }

    private companion object {
        const val HEAD_UNITS_PER_EM = 18
        const val HEAD_LOCA_FORMAT = 50
        const val HHEA_ASCENDER = 4
        const val HHEA_DESCENDER = 6
        const val HHEA_METRIC_COUNT = 34
        const val MAXP_GLYPH_COUNT = 4
        const val MIN_UNITS_PER_EM = 16
        const val MAX_UNITS_PER_EM = 16384
    }
}

/** A big-endian view of one table; every read outside it fails with [label] in the message. */
internal class FontTable(
    private val bytes: ByteArray,
    private val start: Int,
    val length: Int,
    private val label: String,
) {
    fun u8(at: Int): Int {
        need(at, 1)
        return bytes[start + at].toInt() and BYTE
    }

    fun u16(at: Int): Int {
        need(at, 2)
        return (bytes[start + at].toInt() and BYTE shl 8) or (bytes[start + at + 1].toInt() and BYTE)
    }

    fun s16(at: Int): Int = u16(at).toShort().toInt()

    /** Reads an unsigned 32-bit value; one over [Int.MAX_VALUE] comes back negative and fails the next bounds check. */
    fun u32(at: Int): Int = (u16(at) shl 16) or u16(at + 2)

    /** [count] bytes from [at], as a table of their own that reports with the same label. */
    fun slice(at: Int, count: Int): FontTable {
        need(at, count)
        return FontTable(bytes, start + at, count, label)
    }

    fun fail(problem: String): Nothing = throw IllegalArgumentException("$label $problem")

    private fun need(at: Int, count: Int) {
        if (at < 0 || count < 0 || at > length - count) fail("is truncated")
    }

    private companion object {
        const val BYTE = 0xFF
    }
}

private fun readTableDirectory(bytes: ByteArray, name: String): Map<String, FontTable> {
    val header = FontTable(bytes, 0, bytes.size, "$name: the font header")
    val problem = when (header.u32(0)) {
        TRUETYPE, APPLE_TRUETYPE, OPENTYPE_CFF -> null
        COLLECTION -> "is a font collection; load one font from it"
        WOFF, WOFF2 -> "is a WOFF file; load the TrueType or OpenType font"
        else -> "is not a TrueType or OpenType font"
    }
    require(problem == null) { "$name $problem" }
    val count = header.u16(TABLE_COUNT)
    return buildMap {
        repeat(count) { index ->
            val record = DIRECTORY_SIZE + index * RECORD_SIZE
            val tag = header.u32(record).let { raw -> CharArray(4) { (raw ushr (24 - 8 * it) and 0xFF).toChar() }.concatToString() }
            val offset = header.u32(record + RECORD_OFFSET)
            val length = header.u32(record + RECORD_LENGTH)
            require(offset >= 0 && length >= 0 && offset <= bytes.size - length) { "$name: '$tag' table lies outside the file" }
            put(tag, FontTable(bytes, offset, length, "$name: '$tag' table"))
        }
    }
}

private const val TRUETYPE = 0x00010000
private const val APPLE_TRUETYPE = 0x74727565
private const val OPENTYPE_CFF = 0x4F54544F
private const val COLLECTION = 0x74746366
private const val WOFF = 0x774F4646
private const val WOFF2 = 0x774F4632
private const val TABLE_COUNT = 4
private const val DIRECTORY_SIZE = 12
private const val RECORD_SIZE = 16
private const val RECORD_OFFSET = 8
private const val RECORD_LENGTH = 12

/** The best Unicode subtable of [cmap]: format 12 over format 4, from platform 0 or Windows Unicode. */
private fun unicodeCharacterMap(cmap: FontTable): (Int) -> Int {
    var offset = 0
    var format = 0
    for (index in 0 until cmap.u16(2)) {
        val record = 4 + 8 * index
        val platform = cmap.u16(record)
        val encoding = cmap.u16(record + 2)
        val unicode = platform == 0 || (platform == 3 && (encoding == 1 || encoding == 10))
        val candidate = if (unicode) cmap.u32(record + 4) else -1
        val candidateFormat = if (candidate >= 0) cmap.u16(candidate) else 0
        val better = when (candidateFormat) {
            12 -> format != 12
            4 -> format == 0
            else -> false
        }
        if (better) {
            offset = candidate
            format = candidateFormat
        }
    }
    return when (format) {
        12 -> { code -> segmentedCoverage(cmap, offset, code) }
        4 -> { code -> segmentToDelta(cmap, offset, code) }
        else -> cmap.fail("has no Unicode subtable in format 4 or 12")
    }
}

/** A format 4 lookup: segments sorted by their end code, each a delta or a glyph array. */
private fun segmentToDelta(cmap: FontTable, at: Int, code: Int): Int {
    val doubledSegments = cmap.u16(at + 6)
    val ends = at + 14
    var segment = 0
    while (segment < doubledSegments && cmap.u16(ends + segment) < code) segment += 2
    if (segment >= doubledSegments || code > 0xFFFF) return 0
    val starts = ends + doubledSegments + 2
    val deltas = starts + doubledSegments
    val ranges = deltas + doubledSegments
    val start = cmap.u16(starts + segment)
    val delta = cmap.u16(deltas + segment)
    val rangeOffset = cmap.u16(ranges + segment)
    return when {
        code < start -> 0
        rangeOffset == 0 -> (code + delta) and 0xFFFF
        else -> cmap.u16(ranges + segment + rangeOffset + 2 * (code - start)).let { if (it == 0) 0 else (it + delta) and 0xFFFF }
    }
}

/** A format 12 lookup: groups of consecutive codes mapping to consecutive glyphs. */
private fun segmentedCoverage(cmap: FontTable, at: Int, code: Int): Int {
    var glyph = 0
    for (group in 0 until cmap.u32(at + 12)) {
        val record = at + 16 + 12 * group
        val first = cmap.u32(record)
        if (code >= first && code <= cmap.u32(record + 4)) {
            glyph = cmap.u32(record + 8) + (code - first)
            break
        }
    }
    return glyph
}

/** TrueType `glyf` outlines: quadratic contours, and composites of other glyphs placed by a 2x2 transform. */
private class TrueTypeOutlines(
    private val glyf: FontTable,
    private val loca: FontTable,
    private val longOffsets: Boolean,
    private val glyphCount: Int,
) {
    private var parts = 0

    fun outline(glyph: Int, out: OutlineBuilder) {
        parts = 0
        val points = GlyphPoints()
        collect(glyph, points, depth = 0)
        points.emitTo(out)
    }

    private fun glyphData(glyph: Int): FontTable? {
        val start = offset(glyph)
        val end = offset(glyph + 1)
        if (start > end || end > glyf.length) glyf.fail("has a bad 'loca' entry for glyph $glyph")
        return if (start == end) null else glyf.slice(start, end - start)
    }

    private fun offset(glyph: Int): Int = if (longOffsets) loca.u32(4 * glyph) else 2 * loca.u16(2 * glyph)

    private fun collect(glyph: Int, into: GlyphPoints, depth: Int) {
        if (depth > MAX_COMPOSITE_DEPTH) glyf.fail("nests composite glyphs more than $MAX_COMPOSITE_DEPTH deep")
        // Composites may reuse a glyph many times over; without a count a few bytes could ask for billions of parts.
        if (++parts > MAX_PARTS) glyf.fail("builds a glyph from more than $MAX_PARTS parts")
        val data = glyphData(glyph) ?: return
        val contours = data.s16(0)
        if (contours >= 0) readSimple(data, contours, into) else readComposite(data, into, depth)
    }

    private fun readSimple(data: FontTable, contours: Int, into: GlyphPoints) {
        val base = into.size
        var previousEnd = -1
        val ends = IntArray(contours) { index ->
            data.u16(SIMPLE_HEADER + 2 * index).also {
                if (it <= previousEnd) data.fail("has contour ends out of order")
                previousEnd = it
            }
        }
        val count = previousEnd + 1
        var at = SIMPLE_HEADER + 2 * contours
        at += 2 + data.u16(at)
        val flags = IntArray(count)
        var index = 0
        while (index < count) {
            val flag = data.u8(at++)
            val repeats = if (flag and REPEAT != 0) data.u8(at++) else 0
            if (index + repeats >= count) data.fail("has glyph flags that run past the last point")
            repeat(repeats + 1) { flags[index++] = flag }
        }
        val xs = IntArray(count)
        val ys = IntArray(count)
        at = readCoordinates(data, at, flags, xs, short = X_SHORT)
        readCoordinates(data, at, flags, ys, short = Y_SHORT)
        for (point in 0 until count) into.add(xs[point].toDouble(), ys[point].toDouble(), flags[point] and ON_CURVE != 0)
        ends.forEach { into.contourEnds += base + it }
    }

    /** Reads one axis of a simple glyph's points. [short] is the axis's one-byte flag; its same-or-positive flag sits three bits higher. */
    private fun readCoordinates(data: FontTable, from: Int, flags: IntArray, out: IntArray, short: Int): Int {
        val sameOrPositive = short shl 3
        var at = from
        var value = 0
        flags.forEachIndexed { index, flag ->
            value += when {
                flag and short != 0 -> data.u8(at++).let { if (flag and sameOrPositive != 0) it else -it }
                flag and sameOrPositive != 0 -> 0
                else -> data.s16(at).also { at += 2 }
            }
            out[index] = value
        }
        return at
    }

    private fun readComposite(data: FontTable, into: GlyphPoints, depth: Int) {
        var at = SIMPLE_HEADER
        do {
            val flags = data.u16(at)
            val child = data.u16(at + 2)
            if (child >= glyphCount) data.fail("names component glyph $child, past the last glyph")
            at += 4
            val words = flags and ARGS_ARE_WORDS != 0
            val offsets = flags and ARGS_ARE_XY != 0
            val arg1 = if (words) data.u16(at) else data.u8(at)
            val arg2 = if (words) data.u16(at + 2) else data.u8(at + 1)
            at += if (words) 4 else 2
            val m = doubleArrayOf(1.0, 0.0, 0.0, 1.0)
            at = readTransform(data, at, flags, m)
            val component = GlyphPoints()
            collect(child, component, depth + 1)
            component.transform(m)
            if (offsets) {
                val dx = signed(arg1, words).toDouble()
                val dy = signed(arg2, words).toDouble()
                val scaled = flags and SCALED_OFFSET != 0 && flags and UNSCALED_OFFSET == 0
                if (scaled) component.translate(m[0] * dx + m[2] * dy, m[1] * dx + m[3] * dy) else component.translate(dx, dy)
            } else {
                if (arg1 >= into.size || arg2 >= component.size) data.fail("matches component points that do not exist")
                component.translate(into.xs[arg1] - component.xs[arg2], into.ys[arg1] - component.ys[arg2])
            }
            into.append(component)
            if (into.size > MAX_POINTS) data.fail("builds a glyph of more than $MAX_POINTS points")
        } while (flags and MORE_COMPONENTS != 0)
    }

    /** Reads a component's scale or 2x2 into [m] (xx, yx, xy, yy) and returns where the next field starts. */
    private fun readTransform(data: FontTable, at: Int, flags: Int, m: DoubleArray): Int = when {
        flags and HAS_SCALE != 0 -> {
            m[0] = f2Dot14(data, at)
            m[3] = m[0]
            at + 2
        }
        flags and HAS_XY_SCALE != 0 -> {
            m[0] = f2Dot14(data, at)
            m[3] = f2Dot14(data, at + 2)
            at + 4
        }
        flags and HAS_TWO_BY_TWO != 0 -> {
            for (index in 0..3) m[index] = f2Dot14(data, at + 2 * index)
            at + 8
        }
        else -> at
    }

    private fun signed(value: Int, words: Boolean): Int = if (words) value.toShort().toInt() else value.toByte().toInt()

    private fun f2Dot14(data: FontTable, at: Int): Double = data.s16(at) / F2DOT14_ONE

    private companion object {
        const val MAX_COMPOSITE_DEPTH = 16
        const val MAX_PARTS = 1024
        const val MAX_POINTS = 65535
        const val SIMPLE_HEADER = 10
        const val ON_CURVE = 0x01
        const val X_SHORT = 0x02
        const val Y_SHORT = 0x04
        const val REPEAT = 0x08
        const val ARGS_ARE_WORDS = 0x0001
        const val ARGS_ARE_XY = 0x0002
        const val HAS_SCALE = 0x0008
        const val MORE_COMPONENTS = 0x0020
        const val HAS_XY_SCALE = 0x0040
        const val HAS_TWO_BY_TWO = 0x0080
        const val SCALED_OFFSET = 0x0800
        const val UNSCALED_OFFSET = 0x1000
        const val F2DOT14_ONE = 16384.0
    }
}

/** TrueType points, on or off the curve, and where each contour ends. */
private class GlyphPoints {
    val xs = ArrayList<Double>()
    val ys = ArrayList<Double>()
    val onCurve = ArrayList<Boolean>()
    val contourEnds = ArrayList<Int>()
    val size: Int get() = xs.size

    fun add(x: Double, y: Double, on: Boolean) {
        xs += x
        ys += y
        onCurve += on
    }

    fun transform(m: DoubleArray) {
        for (index in xs.indices) {
            val x = xs[index]
            val y = ys[index]
            xs[index] = m[0] * x + m[2] * y
            ys[index] = m[1] * x + m[3] * y
        }
    }

    fun translate(dx: Double, dy: Double) {
        for (index in xs.indices) {
            xs[index] += dx
            ys[index] += dy
        }
    }

    fun append(other: GlyphPoints) {
        val base = size
        xs += other.xs
        ys += other.ys
        onCurve += other.onCurve
        other.contourEnds.forEach { contourEnds += base + it }
    }

    /** Walks each contour from an on-curve point; two off-curve points in a row imply the on-curve point between them. */
    fun emitTo(out: OutlineBuilder) {
        var first = 0
        for (last in contourEnds) {
            val count = last - first + 1
            val start = (0 until count).firstOrNull { onCurve[first + it] }
            if (start != null) {
                out.moveTo(xs[first + start], ys[first + start])
            } else {
                out.moveTo((xs[first] + xs[last]) / 2, (ys[first] + ys[last]) / 2)
            }
            var control = false
            var cx = 0.0
            var cy = 0.0
            val steps = if (start != null) count else count + 1
            for (step in 1..steps) {
                val index = if (start != null) first + (start + step) % count else first + step - 1
                val closing = start == null && step == steps
                val x = if (closing) out.startX else xs[index]
                val y = if (closing) out.startY else ys[index]
                val on = closing || onCurve[index]
                when {
                    on && control -> out.quadTo(cx, cy, x, y)
                    on -> out.lineTo(x, y)
                    control -> out.quadTo(cx, cy, (cx + x) / 2, (cy + y) / 2)
                }
                control = !on
                cx = x
                cy = y
            }
            out.close()
            first = last + 1
        }
    }
}

/** One outline segment, y up in font units until a caller moves it: a line from point 0 to point 3, or a cubic. */
internal class OutlineSegment(val points: DoubleArray, val isLine: Boolean)

/** Collects contours as lines and cubics; a TrueType quadratic is raised to the cubic that draws the same curve. */
internal class OutlineBuilder {
    private val segments = ArrayList<OutlineSegment>()
    private var x = 0.0
    private var y = 0.0
    var startX = 0.0
        private set
    var startY = 0.0
        private set
    private var open = false

    fun moveTo(toX: Double, toY: Double) {
        close()
        x = toX
        y = toY
        startX = toX
        startY = toY
        open = true
    }

    fun lineTo(toX: Double, toY: Double) {
        if (toX != x || toY != y) segments += OutlineSegment(doubleArrayOf(x, y, x, y, toX, toY, toX, toY), isLine = true)
        x = toX
        y = toY
    }

    fun quadTo(cx: Double, cy: Double, toX: Double, toY: Double) =
        cubicTo(x + TWO_THIRDS * (cx - x), y + TWO_THIRDS * (cy - y), toX + TWO_THIRDS * (cx - toX), toY + TWO_THIRDS * (cy - toY), toX, toY)

    @Suppress("LongParameterList")
    fun cubicTo(c1x: Double, c1y: Double, c2x: Double, c2y: Double, toX: Double, toY: Double) {
        val moves = toX != x || toY != y || c1x != x || c1y != y || c2x != x || c2y != y
        if (moves) segments += OutlineSegment(doubleArrayOf(x, y, c1x, c1y, c2x, c2y, toX, toY), isLine = false)
        x = toX
        y = toY
    }

    fun close() {
        if (open) lineTo(startX, startY)
        open = false
    }

    fun finish(): List<OutlineSegment> {
        close()
        return segments
    }

    private companion object {
        const val TWO_THIRDS = 2.0 / 3.0
    }
}
