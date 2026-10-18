/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.schema

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.color.toHex
import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.core.math2d.Sp
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Typed sizes and colours that save as plain data: a theme or a layout keeps `Dp`, `Sp` and `Color`
// fields in its API and writes the numbers and hex text a scene file and an editor already use. The
// descriptors are primitives, so a property schema reads a `Dp` field as a float and a `Color` field
// as text.

/**
 * Writes a [Dp] as its number of dp, such as `16.0`, and reads one back.
 *
 * Use it on a property, `@Serializable(with = DpSerializer::class) val gap: Dp`, or for a whole file
 * with `@file:UseSerializers(DpSerializer::class)`. [Dp.Unspecified] is NaN, which JSON writes only
 * when its `allowSpecialFloatingPointValues` is on; give an optional size a null instead.
 */
object DpSerializer : KSerializer<Dp> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.awakekt.awake.core.math2d.Dp", PrimitiveKind.FLOAT)

    override fun serialize(encoder: Encoder, value: Dp) = encoder.encodeFloat(value.value)

    override fun deserialize(decoder: Decoder): Dp = Dp(decoder.decodeFloat())
}

/**
 * Writes an [Sp] as its number of sp, such as `14.0`, and reads one back. Used as [DpSerializer] is.
 */
object SpSerializer : KSerializer<Sp> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.awakekt.awake.core.math2d.Sp", PrimitiveKind.FLOAT)

    override fun serialize(encoder: Encoder, value: Sp) = encoder.encodeFloat(value.value)

    override fun deserialize(decoder: Decoder): Sp = Sp(decoder.decodeFloat())
}

/**
 * Writes a [Color] as [Color.toHex] does, `#RRGGBB` when opaque and `#RRGGBBAA` otherwise, the form
 * a canvas element saves, and reads either form back, in any case.
 *
 * Each channel is saved to 8 bits, so a colour that is not already on those steps, such as
 * `Color(0.3f, 0.3f, 0.3f)`, reads back as the nearest one. Anything else, such as `#FFF`, `red` or
 * hex with no `#`, fails to read with a [SerializationException] naming the text.
 */
object ColorHexSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.awakekt.awake.core.color.Color", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Color) = encoder.encodeString(value.toHex())

    override fun deserialize(decoder: Decoder): Color {
        val text = decoder.decodeString()
        if (!HEX_COLOR.matches(text)) throw SerializationException("\"$text\" is not a colour: write #RRGGBB or #RRGGBBAA")
        return Color.fromHex(text)
    }
}

private val HEX_COLOR = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
