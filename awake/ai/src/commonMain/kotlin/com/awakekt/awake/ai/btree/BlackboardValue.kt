/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * A value a [Blackboard] holds that a definition can name and a snapshot can store.
 *
 * In JSON a value is written as plainly as it reads: `true`, `3`, `2.5`, `"open"`,
 * `{"entity": 4294967297}` or `{"x": 1.0, "y": 0.0, "z": 2.0}`. A number with a fraction or an
 * exponent is a [FloatValue], one without is an [IntValue], so `2.0` and `2` differ. Other formats
 * write a tagged form.
 */
@Serializable(with = BlackboardValueSerializer::class)
sealed interface BlackboardValue {
    /** The plain Kotlin value a [Blackboard] stores for this: Boolean, Int, Float, String, [Entity] or [Vec3f]. */
    fun toRaw(): Any

    /** A boolean. @property value The boolean. */
    data class BoolValue(val value: Boolean) : BlackboardValue {
        override fun toRaw(): Any = value
    }

    /** A whole number. @property value The number. */
    data class IntValue(val value: Int) : BlackboardValue {
        override fun toRaw(): Any = value
    }

    /** A finite decimal number. @property value The number. */
    data class FloatValue(val value: Float) : BlackboardValue {
        init {
            require(value.isFinite()) { "A blackboard number must be finite, not $value." }
        }

        override fun toRaw(): Any = value
    }

    /** Text. @property value The text. */
    data class TextValue(val value: String) : BlackboardValue {
        override fun toRaw(): Any = value
    }

    /** An entity handle. @property entity The entity. */
    data class EntityValue(val entity: Entity) : BlackboardValue {
        override fun toRaw(): Any = entity
    }

    /** A position or direction. @property x X. @property y Y. @property z Z. */
    data class VectorValue(val x: Float, val y: Float, val z: Float) : BlackboardValue {
        init {
            require(x.isFinite() && y.isFinite() && z.isFinite()) { "A blackboard vector must be finite, not ($x, $y, $z)." }
        }

        override fun toRaw(): Any = Vec3f(x, y, z)
    }

    /** Conversions from the plain values a [Blackboard] stores. */
    companion object {
        /**
         * The value for [raw], or `null` when [raw] is of a type a blackboard value cannot hold.
         * A [Vec3f] is copied, so the value does not change with it.
         */
        fun ofOrNull(raw: Any): BlackboardValue? =
            when (raw) {
                is BlackboardValue -> raw
                is Boolean -> BoolValue(raw)
                is Int -> IntValue(raw)
                is Float -> if (raw.isFinite()) FloatValue(raw) else null
                is String -> TextValue(raw)
                is Entity -> EntityValue(raw)
                is Vec3f -> if (raw.x.isFinite() && raw.y.isFinite() && raw.z.isFinite()) VectorValue(raw.x, raw.y, raw.z) else null
                else -> null
            }

        /** The types [ofOrNull] accepts, for messages. */
        const val SUPPORTED_TYPES: String = "Boolean, Int, finite Float, String, Entity and finite Vec3f"
    }
}

/** The number in this value as a Float, or `null` when it is not a number. */
val BlackboardValue.numberOrNull: Float?
    get() = when (this) {
        is BlackboardValue.IntValue -> value.toFloat()
        is BlackboardValue.FloatValue -> value
        else -> null
    }

/** A one-word name for this value's type, for messages: `bool`, `int`, `float`, `text`, `entity` or `vector`. */
val BlackboardValue.typeName: String
    get() = when (this) {
        is BlackboardValue.BoolValue -> "bool"
        is BlackboardValue.IntValue -> "int"
        is BlackboardValue.FloatValue -> "float"
        is BlackboardValue.TextValue -> "text"
        is BlackboardValue.EntityValue -> "entity"
        is BlackboardValue.VectorValue -> "vector"
    }

/** The tagged form non-JSON formats write. */
@Serializable
internal sealed interface TaggedBlackboardValue {
    @Serializable
    @SerialName("bool")
    data class B(val value: Boolean) : TaggedBlackboardValue

    @Serializable
    @SerialName("int")
    data class I(val value: Int) : TaggedBlackboardValue

    @Serializable
    @SerialName("float")
    data class F(val value: Float) : TaggedBlackboardValue

    @Serializable
    @SerialName("text")
    data class T(val value: String) : TaggedBlackboardValue

    @Serializable
    @SerialName("entity")
    data class E(val entity: Long) : TaggedBlackboardValue

    @Serializable
    @SerialName("vector")
    data class V(val x: Float, val y: Float, val z: Float) : TaggedBlackboardValue
}

/** Writes a [BlackboardValue] plainly in JSON and tagged elsewhere. */
internal object BlackboardValueSerializer : KSerializer<BlackboardValue> {
    private val tagged = TaggedBlackboardValue.serializer()

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        SerialDescriptor("com.awakekt.awake.ai.btree.BlackboardValue", tagged.descriptor)

    override fun serialize(encoder: Encoder, value: BlackboardValue) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(toJson(value))
        } else {
            encoder.encodeSerializableValue(tagged, toTagged(value))
        }
    }

    override fun deserialize(decoder: Decoder): BlackboardValue =
        if (decoder is JsonDecoder) {
            fromJson(decoder.decodeJsonElement())
        } else {
            fromTagged(decoder.decodeSerializableValue(tagged))
        }

    @OptIn(ExperimentalSerializationApi::class)
    private fun toJson(value: BlackboardValue): JsonElement =
        when (value) {
            is BlackboardValue.BoolValue -> JsonPrimitive(value.value)
            is BlackboardValue.IntValue -> JsonPrimitive(value.value)
            // Kotlin/JS prints a whole Float without a fraction, which would read back as an int.
            is BlackboardValue.FloatValue -> JsonUnquotedLiteral(floatLiteral(value.value))
            is BlackboardValue.TextValue -> JsonPrimitive(value.value)
            is BlackboardValue.EntityValue -> buildJsonObject { put("entity", value.entity.packed) }
            is BlackboardValue.VectorValue -> buildJsonObject {
                put("x", value.x)
                put("y", value.y)
                put("z", value.z)
            }
        }

    private fun floatLiteral(value: Float): String {
        val text = value.toString()
        return if (text.any { it == '.' || it == 'e' || it == 'E' }) text else "$text.0"
    }

    private fun fromJson(element: JsonElement): BlackboardValue =
        when (element) {
            is JsonPrimitive -> fromPrimitive(element)
            is JsonObject -> fromObject(element)
            else -> throw SerializationException(EXPECTED)
        }

    private fun fromPrimitive(element: JsonPrimitive): BlackboardValue {
        val text = element.content
        val bool = element.booleanOrNull
        return when {
            element.isString -> BlackboardValue.TextValue(text)
            bool != null -> BlackboardValue.BoolValue(bool)
            element is JsonNull -> throw SerializationException(EXPECTED)
            text.any { it == '.' || it == 'e' || it == 'E' } -> BlackboardValue.FloatValue(element.float)
            else -> BlackboardValue.IntValue(text.toIntOrNull() ?: throw SerializationException("$text is outside the Int range."))
        }
    }

    private fun fromObject(element: JsonObject): BlackboardValue =
        when (element.keys) {
            setOf("entity") -> BlackboardValue.EntityValue(Entity((element.getValue("entity") as JsonPrimitive).long))
            setOf("x", "y", "z") -> BlackboardValue.VectorValue(
                (element.getValue("x") as JsonPrimitive).float,
                (element.getValue("y") as JsonPrimitive).float,
                (element.getValue("z") as JsonPrimitive).float,
            )
            else -> throw SerializationException("$EXPECTED Got an object with ${element.keys}.")
        }

    private fun toTagged(value: BlackboardValue): TaggedBlackboardValue =
        when (value) {
            is BlackboardValue.BoolValue -> TaggedBlackboardValue.B(value.value)
            is BlackboardValue.IntValue -> TaggedBlackboardValue.I(value.value)
            is BlackboardValue.FloatValue -> TaggedBlackboardValue.F(value.value)
            is BlackboardValue.TextValue -> TaggedBlackboardValue.T(value.value)
            is BlackboardValue.EntityValue -> TaggedBlackboardValue.E(value.entity.packed)
            is BlackboardValue.VectorValue -> TaggedBlackboardValue.V(value.x, value.y, value.z)
        }

    private fun fromTagged(value: TaggedBlackboardValue): BlackboardValue =
        when (value) {
            is TaggedBlackboardValue.B -> BlackboardValue.BoolValue(value.value)
            is TaggedBlackboardValue.I -> BlackboardValue.IntValue(value.value)
            is TaggedBlackboardValue.F -> BlackboardValue.FloatValue(value.value)
            is TaggedBlackboardValue.T -> BlackboardValue.TextValue(value.value)
            is TaggedBlackboardValue.E -> BlackboardValue.EntityValue(Entity(value.entity))
            is TaggedBlackboardValue.V -> BlackboardValue.VectorValue(value.x, value.y, value.z)
        }

    private const val EXPECTED =
        "A blackboard value is a boolean, a number, text, {\"entity\": id} or {\"x\", \"y\", \"z\"}."
}
