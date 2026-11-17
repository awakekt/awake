/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

/**
 * One field a canvas binding reads, `component.field.inner` as [segments] after the component, and
 * what [FieldEncoder] last found there (#1512). A number is held as its bits, so reading it and asking
 * whether it [changed] allocate nothing; its [content] is the text JSON would give it.
 */
internal class FieldValue(val segments: List<String>) {
    private var kind = ABSENT
    private var bits = 0L
    private var ref: String? = null

    private var lastKind = ABSENT
    private var lastBits = 0L
    private var lastRef: String? = null

    /** Whether the last pass found the field. */
    val present: Boolean get() = kind != ABSENT

    fun clear() {
        kind = ABSENT
        ref = null
    }

    fun setBoolean(value: Boolean) = set(BOOLEAN, if (value) 1L else 0L, null)

    fun setLong(value: Long) = set(LONG, value, null)

    fun setFloat(value: Float) = set(FLOAT, value.toRawBits().toLong(), null)

    fun setDouble(value: Double) = set(DOUBLE, value.toRawBits(), null)

    fun setText(value: String) = set(TEXT, 0L, value)

    fun setNull() = set(NULL, 0L, null)

    private fun set(kind: Int, bits: Long, ref: String?) {
        this.kind = kind
        this.bits = bits
        this.ref = ref
    }

    /**
     * The field's value as JSON's text for it: `JsonPrimitive.content`, `"null"` for a null field, and
     * null when the last pass did not find it.
     */
    fun content(): String? = when (kind) {
        BOOLEAN -> (bits != 0L).toString()
        LONG -> bits.toString()
        FLOAT -> Float.fromBits(bits.toInt()).toString()
        DOUBLE -> Double.fromBits(bits).toString()
        TEXT -> ref
        NULL -> "null"
        else -> null
    }

    /** Whether the value differs from what the previous call saw; it records the current one either way. */
    fun changed(): Boolean {
        // By kind and bits, so a NaN that stays NaN is no change, and by text equality for words.
        val changed = kind != lastKind || bits != lastBits || ref != lastRef
        lastKind = kind
        lastBits = bits
        lastRef = ref
        return changed
    }

    private companion object {
        const val ABSENT = 0
        const val BOOLEAN = 1
        const val LONG = 2
        const val FLOAT = 3
        const val DOUBLE = 4
        const val TEXT = 5
        const val NULL = 6
    }
}
