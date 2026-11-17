/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Reads the primitive fields a canvas binding names out of a component's saved form (#1512), as the
 * scene format would write them to JSON and with the same text, without building the JSON.
 *
 * One [capture] pass walks the component once and fills every [FieldValue] whose path it meets. A path
 * matches as a JSON object's keys do: a class's field names, and a map's keys. A list is not walked,
 * as a JSON array is not looked into. A path ending at an object reads nothing.
 *
 * Anything this cannot read exactly as JSON would, a polymorphic or contextual value or an unsigned
 * number, ends the pass with [Unreadable], and the caller reads that component through JSON instead.
 */
@OptIn(ExperimentalSerializationApi::class)
@Suppress("TooManyFunctions") // One override per Encoder primitive, as the interface asks.
internal class FieldEncoder : AbstractEncoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    private var fields: List<FieldValue> = emptyList()

    /** Per structure depth: what is entered, and how many path segments the branch has matched. */
    private var kinds = arrayOfNulls<SerialKind>(INITIAL_DEPTH)
    private var depth = 0
    private var matched = 0
    private var target: FieldValue? = null
    private var keyNext = false

    /** Fills each of [fields] that [value], saved by [serializer], holds; resets the rest to absent. */
    fun <T> capture(serializer: SerializationStrategy<T>, value: T, fields: List<FieldValue>) {
        this.fields = fields
        for (index in fields.indices) fields[index].clear()
        depth = 0
        matched = 0
        target = null
        keyNext = false
        mapKey = null
        encodeSerializableValue(serializer, value)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        val kind = descriptor.kind
        // A structured map key is JSON's own error to report; so is a polymorphic or contextual value to read.
        if (keyNext || kind is PolymorphicKind || kind == SerialKind.CONTEXTUAL) throw Unreadable
        target = null // A path that ends at an object reads nothing, as in JSON.
        if (depth == kinds.size) kinds = kinds.copyOf(depth * 2)
        kinds[depth++] = kind
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        depth--
        matched = minOf(matched, depth)
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        val segment = depth - 1
        matched = minOf(matched, segment)
        target = null
        when (kinds[segment]) {
            StructureKind.LIST -> Unit
            StructureKind.MAP -> if (index % 2 == 0) keyNext = matched == segment else mapKey?.let { enter(segment, it) }
            else -> if (matched == segment) enter(segment, descriptor.getElementName(index))
        }
        if (index % 2 == 1 || kinds[segment] != StructureKind.MAP) mapKey = null
        return true
    }

    /** The key of the map entry being written, when a path names it here. */
    private var mapKey: String? = null

    /** The branch now matches segment [segment] when [name] is a path's segment there. */
    private fun enter(segment: Int, name: String) {
        var hit = false
        for (index in fields.indices) {
            val field = fields[index]
            if (segment < field.segments.size && field.segments[segment] == name) {
                hit = true
                if (segment == field.segments.size - 1) target = field
            }
        }
        if (hit) matched = segment + 1
    }

    /** A map's key: the next value is matched when the key is a path's segment here. */
    private fun key(text: String): Boolean {
        if (!keyNext) return false
        keyNext = false
        val segment = depth - 1
        mapKey = text.takeIf { fields.any { field -> segment < field.segments.size && field.segments[segment] == text } }
        return true
    }

    override fun encodeInline(descriptor: SerialDescriptor): Encoder {
        if (descriptor.serialName in UNSIGNED) throw Unreadable
        return this
    }

    override fun encodeBoolean(value: Boolean) {
        if (!(keyNext && key(value.toString()))) target?.setBoolean(value)
    }

    override fun encodeByte(value: Byte) = encodeLong(value.toLong())

    override fun encodeShort(value: Short) = encodeLong(value.toLong())

    override fun encodeInt(value: Int) = encodeLong(value.toLong())

    override fun encodeLong(value: Long) {
        if (!(keyNext && key(value.toString()))) target?.setLong(value)
    }

    override fun encodeFloat(value: Float) {
        if (!(keyNext && key(value.toString()))) target?.setFloat(value)
    }

    override fun encodeDouble(value: Double) {
        if (!(keyNext && key(value.toString()))) target?.setDouble(value)
    }

    override fun encodeChar(value: Char) {
        if (!(keyNext && key(value.toString()))) target?.setText(value.toString())
    }

    override fun encodeString(value: String) {
        if (!key(value)) target?.setText(value)
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        encodeString(enumDescriptor.getElementName(index))
    }

    override fun encodeNull() {
        if (!keyNext) target?.setNull()
    }

    /** A value this cannot read exactly as JSON would: read the component through JSON. */
    object Unreadable : RuntimeException("not readable without JSON") {
        private fun readResolve(): Any = Unreadable
    }

    private companion object {
        const val INITIAL_DEPTH = 8

        // JSON writes these as unsigned digits; the encoder would see their signed bits.
        val UNSIGNED = setOf("kotlin.UByte", "kotlin.UShort", "kotlin.UInt", "kotlin.ULong")
    }
}
