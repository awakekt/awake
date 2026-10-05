/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import com.awakekt.awake.core.color.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Serializable RGBA color primitive for scene documents.
 *
 * Supports polymorphic JSON decoding from:
 * - Hex string: `"#RGB"`, `"#RGBA"`, `"#RRGGBB"`, or `"#RRGGBBAA"`.
 * - Object: `{"r": 1.0, "g": 0.5, "b": 0.2, "a": 1.0}` (`a` defaults to `1.0`).
 * - Array: `[1.0, 0.5, 0.2]` or `[1.0, 0.5, 0.2, 1.0]`.
 *
 * @property r The red component normalized in `[0f, 1f]`.
 * @property g The green component normalized in `[0f, 1f]`.
 * @property b The blue component normalized in `[0f, 1f]`.
 * @property a The alpha (opacity) component normalized in `[0f, 1f]`.
 */
@Serializable(with = SceneColorSerializer::class)
data class SceneColor(
    val r: Float = 1f,
    val g: Float = 1f,
    val b: Float = 1f,
    val a: Float = 1f,
) {
    /**
     * Converts this color to an 8-character hex string `"#RRGGBBAA"`.
     *
     * @param includeAlpha Whether to include the alpha channel in the resulting hex string.
     * @return Uppercase hex formatted string representing this color.
     */
    fun toHex(includeAlpha: Boolean = a < 1f): String {
        val ri = (r.coerceIn(0f, 1f) * 255f).toInt().toString(16).padStart(2, '0')
        val gi = (g.coerceIn(0f, 1f) * 255f).toInt().toString(16).padStart(2, '0')
        val bi = (b.coerceIn(0f, 1f) * 255f).toInt().toString(16).padStart(2, '0')
        if (!includeAlpha) return "#$ri$gi$bi".uppercase()
        val ai = (a.coerceIn(0f, 1f) * 255f).toInt().toString(16).padStart(2, '0')
        return "#$ri$gi$bi$ai".uppercase()
    }

    /** Predefined constant colors and parsing helpers for [SceneColor]. */
    companion object {
        /** Opaque white color (`#FFFFFFFF`). */
        val White = SceneColor(1f, 1f, 1f, 1f)

        /** Opaque black color (`#000000FF`). */
        val Black = SceneColor(0f, 0f, 0f, 1f)

        /** Completely transparent color (`#00000000`). */
        val Transparent = SceneColor(0f, 0f, 0f, 0f)

        /**
         * Parses a hex string into a [SceneColor].
         *
         * @param hex Hex color string in `#RGB`, `#RGBA`, `#RRGGBB`, or `#RRGGBBAA` format.
         * @return Parsed [SceneColor], or [White] if format is unrecognized.
         */
        fun fromHex(hex: String): SceneColor {
            val clean = hex.removePrefix("#").trim()
            return when (clean.length) {
                3 -> {
                    val r = clean.substring(0, 1).repeat(2).toInt(16) / 255f
                    val g = clean.substring(1, 2).repeat(2).toInt(16) / 255f
                    val b = clean.substring(2, 3).repeat(2).toInt(16) / 255f
                    SceneColor(r, g, b, 1f)
                }
                4 -> {
                    val r = clean.substring(0, 1).repeat(2).toInt(16) / 255f
                    val g = clean.substring(1, 2).repeat(2).toInt(16) / 255f
                    val b = clean.substring(2, 3).repeat(2).toInt(16) / 255f
                    val a = clean.substring(3, 4).repeat(2).toInt(16) / 255f
                    SceneColor(r, g, b, a)
                }
                6 -> {
                    val r = clean.substring(0, 2).toInt(16) / 255f
                    val g = clean.substring(2, 4).toInt(16) / 255f
                    val b = clean.substring(4, 6).toInt(16) / 255f
                    SceneColor(r, g, b, 1f)
                }
                8 -> {
                    val r = clean.substring(0, 2).toInt(16) / 255f
                    val g = clean.substring(2, 4).toInt(16) / 255f
                    val b = clean.substring(4, 6).toInt(16) / 255f
                    val a = clean.substring(6, 8).toInt(16) / 255f
                    SceneColor(r, g, b, a)
                }
                else -> White
            }
        }
    }
}

/**
 * Converts a core [Color] to a document [SceneColor].
 *
 * @return The converted [SceneColor] preserving color channel values.
 */
fun Color.toSceneColor(): SceneColor = SceneColor(r, g, b, a)

/**
 * Converts a document [SceneColor] to a core [Color].
 *
 * @return The converted [Color] preserving color channel values.
 */
fun SceneColor.toColor(): Color = Color(r, g, b, a)

/**
 * Polymorphic serializer for [SceneColor] supporting hex string, object, and array JSON formats.
 */
object SceneColorSerializer : KSerializer<SceneColor> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SceneColor") {
        element<Float>("r", isOptional = true)
        element<Float>("g", isOptional = true)
        element<Float>("b", isOptional = true)
        element<Float>("a", isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: SceneColor) {
        if (encoder is JsonEncoder) {
            val jsonObject = buildJsonObject {
                put("r", value.r)
                put("g", value.g)
                put("b", value.b)
                if (value.a != 1f) {
                    put("a", value.a)
                }
            }
            encoder.encodeJsonElement(jsonObject)
        } else {
            val composite = encoder.beginStructure(descriptor)
            composite.encodeFloatElement(descriptor, 0, value.r)
            composite.encodeFloatElement(descriptor, 1, value.g)
            composite.encodeFloatElement(descriptor, 2, value.b)
            composite.encodeFloatElement(descriptor, 3, value.a)
            composite.endStructure(descriptor)
        }
    }

    override fun deserialize(decoder: Decoder): SceneColor {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            return when (element) {
                is JsonPrimitive -> {
                    if (element.isString) {
                        SceneColor.fromHex(element.content)
                    } else {
                        val v = element.floatOrNull ?: 0f
                        SceneColor(v, v, v, 1f)
                    }
                }
                is JsonObject -> {
                    val r = element["r"]?.jsonPrimitive?.floatOrNull
                        ?: element["x"]?.jsonPrimitive?.floatOrNull
                        ?: 0f
                    val g = element["g"]?.jsonPrimitive?.floatOrNull
                        ?: element["y"]?.jsonPrimitive?.floatOrNull
                        ?: 0f
                    val b = element["b"]?.jsonPrimitive?.floatOrNull
                        ?: element["z"]?.jsonPrimitive?.floatOrNull
                        ?: 0f
                    val a = element["a"]?.jsonPrimitive?.floatOrNull
                        ?: element["w"]?.jsonPrimitive?.floatOrNull
                        ?: 1f
                    SceneColor(r, g, b, a)
                }
                is JsonArray -> {
                    val r = element.getOrNull(0)?.jsonPrimitive?.floatOrNull ?: 0f
                    val g = element.getOrNull(1)?.jsonPrimitive?.floatOrNull ?: 0f
                    val b = element.getOrNull(2)?.jsonPrimitive?.floatOrNull ?: 0f
                    val a = element.getOrNull(3)?.jsonPrimitive?.floatOrNull ?: 1f
                    SceneColor(r, g, b, a)
                }
                kotlinx.serialization.json.JsonNull -> SceneColor()
            }
        }

        val composite = decoder.beginStructure(descriptor)
        var r = 1f
        var g = 1f
        var b = 1f
        var a = 1f
        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> r = composite.decodeFloatElement(descriptor, 0)
                1 -> g = composite.decodeFloatElement(descriptor, 1)
                2 -> b = composite.decodeFloatElement(descriptor, 2)
                3 -> a = composite.decodeFloatElement(descriptor, 3)
            }
        }
        composite.endStructure(descriptor)
        return SceneColor(r, g, b, a)
    }
}
