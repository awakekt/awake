/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlin.math.abs

/**
 * Outlines from a `CFF ` table: Type 2 charstrings, with local and global subroutines.
 *
 * Hints are read only to step over them. CID-keyed fonts, `seac` accents and the arithmetic
 * operators are rejected by name rather than drawn wrong.
 */
internal class CffOutlines(private val cff: FontTable, glyphCount: Int) {
    private val charStrings: CffIndex
    private val globalSubrs: CffIndex
    private val localSubrs: CffIndex?

    init {
        val names = CffIndex(cff, cff.u8(2))
        val topDicts = CffIndex(cff, names.end)
        val strings = CffIndex(cff, topDicts.end)
        globalSubrs = CffIndex(cff, strings.end)
        if (topDicts.count == 0) cff.fail("has no Top DICT")
        val top = readDict(cff, topDicts.start(0), topDicts.end(0))
        if (REGISTRY_ORDERING_SUPPLEMENT in top) cff.fail("is CID-keyed, which is not supported")
        val type = top[CHARSTRING_TYPE]?.firstOrNull() ?: 2.0
        if (type != 2.0) cff.fail("uses Type ${type.toInt()} charstrings, which are not supported")
        charStrings = CffIndex(cff, top[CHAR_STRINGS]?.lastOrNull()?.toInt() ?: cff.fail("has no CharStrings"))
        if (charStrings.count < glyphCount) cff.fail("has ${charStrings.count} charstrings for $glyphCount glyphs")
        localSubrs = top[PRIVATE]?.takeIf { it.size == 2 }?.let { (size, offset) ->
            val at = offset.toInt()
            readDict(cff, at, at + size.toInt())[SUBRS]?.lastOrNull()?.let { CffIndex(cff, at + it.toInt()) }
        }
    }

    fun outline(glyph: Int, out: OutlineBuilder) {
        Charstring(out).execute(charStrings.start(glyph), charStrings.end(glyph), depth = 0)
        out.close()
    }

    /** One glyph's run through the Type 2 interpreter: one function per kind of work, as the operators group. */
    @Suppress("TooManyFunctions")
    private inner class Charstring(private val out: OutlineBuilder) {
        private val stack = DoubleArray(MAX_STACK)
        private var size = 0
        private var stems = 0
        private var widthRead = false
        private var ended = false
        private var operations = 0
        private var x = 0.0
        private var y = 0.0

        fun execute(from: Int, to: Int, depth: Int) {
            if (depth > MAX_SUBR_DEPTH) cff.fail("nests subroutines more than $MAX_SUBR_DEPTH deep")
            var at = from
            while (at < to && !ended) {
                if (++operations > MAX_OPERATIONS) cff.fail("has a charstring that runs too long")
                val b0 = cff.u8(at)
                at = when {
                    b0 == RETURN -> return
                    b0 == SHORT_INT || b0 == FIXED || b0 >= SMALL_INT_FIRST -> at + number(at, b0)
                    b0 == HINT_MASK || b0 == COUNTER_MASK -> at + 1 + hintMask()
                    b0 == ESCAPE -> {
                        escape(cff.u8(at + 1))
                        at + 2
                    }
                    else -> {
                        operator(b0, depth)
                        at + 1
                    }
                }
            }
        }

        /** Pushes the number at [at] and returns how many bytes it took. */
        private fun number(at: Int, b0: Int): Int {
            val (value, length) = when {
                b0 == SHORT_INT -> cff.s16(at + 1).toDouble() to 3
                b0 == FIXED -> cff.u32(at + 1) / FIXED_ONE to 5
                b0 <= SMALL_INT_LAST -> (b0 - SMALL_INT_BIAS).toDouble() to 1
                b0 <= POSITIVE_INT_LAST -> ((b0 - POSITIVE_INT_FIRST) * 256 + cff.u8(at + 1) + INT_OFFSET).toDouble() to 2
                else -> (-(b0 - NEGATIVE_INT_FIRST) * 256 - cff.u8(at + 1) - INT_OFFSET).toDouble() to 2
            }
            push(value)
            return length
        }

        private fun call(subrs: CffIndex, depth: Int) {
            val index = pop().toInt() + subrBias(subrs.count)
            if (index !in 0 until subrs.count) cff.fail("calls subroutine $index of ${subrs.count}")
            execute(subrs.start(index), subrs.end(index), depth + 1)
        }

        /** Counts the stems a mask covers, so its bytes can be skipped. */
        private fun hintMask(): Int {
            stems += (size - firstArgument(size % 2 == 1)) / 2
            size = 0
            return (stems + 7) / 8
        }

        /** Where the operator's own arguments start: a charstring's first stack-clearing operator may carry the width before them. */
        private fun firstArgument(hasWidth: Boolean): Int {
            val first = if (!widthRead && hasWidth) 1 else 0
            widthRead = true
            return first
        }

        @Suppress("CyclomaticComplexMethod", "LongMethod")
        private fun operator(op: Int, depth: Int) {
            when (op) {
                CALL_SUBR -> return call(localSubrs ?: cff.fail("calls a local subroutine but has none"), depth)
                CALL_GLOBAL_SUBR -> return call(globalSubrs, depth)
                HSTEM, VSTEM, HSTEM_HM, VSTEM_HM -> stems += (size - firstArgument(size % 2 == 1)) / 2
                RMOVETO -> firstArgument(size > 2).let { moveBy(arg(it), arg(it + 1)) }
                HMOVETO -> firstArgument(size > 1).let { moveBy(arg(it), 0.0) }
                VMOVETO -> firstArgument(size > 1).let { moveBy(0.0, arg(it)) }
                RLINETO -> for (i in 0 until size - 1 step 2) lineBy(arg(i), arg(i + 1))
                HLINETO, VLINETO -> for (i in 0 until size) {
                    if ((i % 2 == 0) == (op == HLINETO)) lineBy(arg(i), 0.0) else lineBy(0.0, arg(i))
                }
                RRCURVETO -> for (i in 0 until size - 5 step 6) curveAt(i)
                RCURVELINE -> {
                    var i = 0
                    while (i + 6 <= size - 2) {
                        curveAt(i)
                        i += 6
                    }
                    lineBy(arg(i), arg(i + 1))
                }
                RLINECURVE -> {
                    var i = 0
                    while (i + 2 <= size - 6) {
                        lineBy(arg(i), arg(i + 1))
                        i += 2
                    }
                    curveAt(i)
                }
                VVCURVETO, HHCURVETO -> {
                    var i = size % 2
                    var across = if (i == 1) arg(0) else 0.0
                    while (i + 4 <= size) {
                        if (op == VVCURVETO) {
                            curveBy(across, arg(i), arg(i + 1), arg(i + 2), 0.0, arg(i + 3))
                        } else {
                            curveBy(arg(i), across, arg(i + 1), arg(i + 2), arg(i + 3), 0.0)
                        }
                        across = 0.0
                        i += 4
                    }
                }
                HVCURVETO, VHCURVETO -> {
                    var i = 0
                    var horizontal = op == HVCURVETO
                    while (i + 4 <= size) {
                        val last = if (size - i == 5) arg(i + 4) else 0.0
                        if (horizontal) {
                            curveBy(arg(i), 0.0, arg(i + 1), arg(i + 2), last, arg(i + 3))
                        } else {
                            curveBy(0.0, arg(i), arg(i + 1), arg(i + 2), arg(i + 3), last)
                        }
                        horizontal = !horizontal
                        i += 4
                    }
                }
                ENDCHAR -> {
                    val first = firstArgument(size == 1 || size == 5)
                    if (size - first >= 4) cff.fail("builds an accented glyph with endchar (seac), which is not supported")
                    ended = true
                }
                else -> cff.fail("uses reserved charstring operator $op")
            }
            size = 0
        }

        private fun escape(op: Int) {
            when (op) {
                FLEX -> {
                    curveAt(0)
                    curveAt(6)
                }
                HFLEX -> {
                    curveBy(arg(0), 0.0, arg(1), arg(2), arg(3), 0.0)
                    curveBy(arg(4), 0.0, arg(5), -arg(2), arg(6), 0.0)
                }
                HFLEX1 -> {
                    curveBy(arg(0), arg(1), arg(2), arg(3), arg(4), 0.0)
                    curveBy(arg(5), 0.0, arg(6), arg(7), arg(8), -(arg(1) + arg(3) + arg(7)))
                }
                FLEX1 -> {
                    val dx = arg(0) + arg(2) + arg(4) + arg(6) + arg(8)
                    val dy = arg(1) + arg(3) + arg(5) + arg(7) + arg(9)
                    curveAt(0)
                    if (abs(dx) > abs(dy)) {
                        curveBy(arg(6), arg(7), arg(8), arg(9), arg(10), -dy)
                    } else {
                        curveBy(arg(6), arg(7), arg(8), arg(9), -dx, arg(10))
                    }
                }
                else -> cff.fail("uses charstring operator 12 $op, which is not supported")
            }
            size = 0
        }

        private fun moveBy(dx: Double, dy: Double) {
            x += dx
            y += dy
            out.moveTo(x, y)
        }

        private fun lineBy(dx: Double, dy: Double) {
            x += dx
            y += dy
            out.lineTo(x, y)
        }

        private fun curveAt(i: Int) = curveBy(arg(i), arg(i + 1), arg(i + 2), arg(i + 3), arg(i + 4), arg(i + 5))

        @Suppress("LongParameterList")
        private fun curveBy(dx1: Double, dy1: Double, dx2: Double, dy2: Double, dx3: Double, dy3: Double) {
            val x1 = x + dx1
            val y1 = y + dy1
            val x2 = x1 + dx2
            val y2 = y1 + dy2
            x = x2 + dx3
            y = y2 + dy3
            out.cubicTo(x1, y1, x2, y2, x, y)
        }

        private fun arg(index: Int): Double = if (index < size) stack[index] else cff.fail("has a charstring that underflows its stack")

        private fun push(value: Double) {
            if (size == MAX_STACK) cff.fail("has a charstring that overflows its stack")
            stack[size++] = value
        }

        private fun pop(): Double = if (size > 0) stack[--size] else cff.fail("has a charstring that underflows its stack")
    }

    private companion object {
        const val MAX_STACK = 48
        const val MAX_SUBR_DEPTH = 10
        const val MAX_OPERATIONS = 100_000
        const val FIXED_ONE = 65536.0

        const val HSTEM = 1
        const val VSTEM = 3
        const val VMOVETO = 4
        const val RLINETO = 5
        const val HLINETO = 6
        const val VLINETO = 7
        const val RRCURVETO = 8
        const val CALL_SUBR = 10
        const val RETURN = 11
        const val ESCAPE = 12
        const val ENDCHAR = 14
        const val HSTEM_HM = 18
        const val HINT_MASK = 19
        const val COUNTER_MASK = 20
        const val RMOVETO = 21
        const val HMOVETO = 22
        const val VSTEM_HM = 23
        const val RCURVELINE = 24
        const val RLINECURVE = 25
        const val VVCURVETO = 26
        const val HHCURVETO = 27
        const val SHORT_INT = 28
        const val CALL_GLOBAL_SUBR = 29
        const val VHCURVETO = 30
        const val HVCURVETO = 31
        const val SMALL_INT_FIRST = 32
        const val SMALL_INT_LAST = 246
        const val SMALL_INT_BIAS = 139
        const val POSITIVE_INT_FIRST = 247
        const val POSITIVE_INT_LAST = 250
        const val NEGATIVE_INT_FIRST = 251
        const val INT_OFFSET = 108
        const val FIXED = 255
        const val HFLEX = 34
        const val FLEX = 35
        const val HFLEX1 = 36
        const val FLEX1 = 37

        const val CHAR_STRINGS = 17
        const val PRIVATE = 18
        const val SUBRS = 19
        const val CHARSTRING_TYPE = 1206
        const val REGISTRY_ORDERING_SUPPLEMENT = 1230

        /** Type 2 subroutine numbers are stored minus this bias, so small indexes fit in one byte. */
        fun subrBias(count: Int): Int = when {
            count < 1240 -> 107
            count < 33900 -> 1131
            else -> 32768
        }
    }
}

/** A CFF INDEX: a count, then offsets into the data that follows them. */
private class CffIndex(private val table: FontTable, at: Int) {
    val count: Int = table.u16(at)
    private val offsetSize = if (count == 0) 0 else table.u8(at + 2)
    private val offsets = at + 3
    private val data = offsets + (count + 1) * offsetSize - 1

    /** The first byte after this INDEX. */
    val end: Int

    init {
        if (count > 0 && offsetSize !in 1..4) table.fail("has an INDEX with offset size $offsetSize")
        end = if (count == 0) at + 2 else end(count - 1)
    }

    fun start(index: Int): Int = data + offset(index)

    fun end(index: Int): Int {
        val end = data + offset(index + 1)
        if (end < start(index) || end > table.length) table.fail("has an INDEX entry outside the table")
        return end
    }

    private fun offset(index: Int): Int {
        var value = 0
        repeat(offsetSize) { value = (value shl 8) or table.u8(offsets + index * offsetSize + it) }
        return value
    }
}

/** A CFF DICT's operators and their operands; a two-byte operator `12 x` is keyed as `1200 + x`. */
private fun readDict(cff: FontTable, from: Int, to: Int): Map<Int, DoubleArray> {
    val entries = HashMap<Int, DoubleArray>()
    val operands = ArrayList<Double>()
    var at = from
    while (at < to) {
        val b0 = cff.u8(at)
        if (b0 <= DICT_LAST_OPERATOR) {
            val operator = if (b0 == DICT_ESCAPE) DICT_ESCAPED + cff.u8(at + 1) else b0
            entries[operator] = operands.toDoubleArray()
            operands.clear()
            at += if (b0 == DICT_ESCAPE) 2 else 1
        } else {
            at = readDictOperand(cff, at, b0, operands)
        }
    }
    return entries
}

/** Adds the operand at [at] to [operands] and returns where the next item starts. */
private fun readDictOperand(cff: FontTable, at: Int, b0: Int, operands: MutableList<Double>): Int {
    // Only integer operands are used, so a real is stepped over and stands as zero.
    if (b0 == DICT_REAL) {
        operands += 0.0
        return skipReal(cff, at + 1)
    }
    val (value, length) = when (b0) {
        DICT_SHORT -> cff.s16(at + 1) to 3
        DICT_LONG -> cff.u32(at + 1) to 5
        in 32..246 -> b0 - 139 to 1
        in 247..250 -> (b0 - 247) * 256 + cff.u8(at + 1) + 108 to 2
        in 251..254 -> -(b0 - 251) * 256 - cff.u8(at + 1) - 108 to 2
        else -> cff.fail("has a DICT with the reserved byte $b0")
    }
    operands += value.toDouble()
    return at + length
}

/** Steps over a real: packed decimal nibbles, ended by the nibble 0xF. */
private fun skipReal(cff: FontTable, from: Int): Int {
    var at = from
    var byte: Int
    do {
        byte = cff.u8(at++)
    } while (byte and 0x0F != 0x0F && byte ushr 4 != 0x0F)
    return at
}

private const val DICT_LAST_OPERATOR = 21
private const val DICT_ESCAPE = 12
private const val DICT_ESCAPED = 1200
private const val DICT_SHORT = 28
private const val DICT_LONG = 29
private const val DICT_REAL = 30
