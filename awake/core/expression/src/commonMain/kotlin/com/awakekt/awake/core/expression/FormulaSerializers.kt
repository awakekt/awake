/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Writes a [CompiledFormula] as its source string and compiles one when reading, so a formula in data is
 * validated as it loads: a formula that names an undeclared variable or does not parse fails the load,
 * with the problems and their positions in the message.
 *
 * A formula's variables come from the plugin that evaluates it, not from the data, so the serializer is
 * built with them and named on the property:
 *
 * ```kotlin
 * object DamageFormula : KSerializer<CompiledFormula> by FormulaSerializer(combat)
 *
 * @Serializable
 * data class Skill(val name: String, @Serializable(with = DamageFormula::class) val damage: CompiledFormula)
 * ```
 *
 * @property variables What a loaded formula may read.
 * @property options The limits and policy a loaded formula is compiled with.
 */
class FormulaSerializer(
    val variables: FormulaVariables,
    val options: FormulaOptions = FormulaOptions.Default,
) : KSerializer<CompiledFormula> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.awakekt.awake.core.expression.Formula", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CompiledFormula) {
        encoder.encodeString(value.source)
    }

    /** @throws SerializationException when the string is not a valid formula for [variables]. */
    override fun deserialize(decoder: Decoder): CompiledFormula {
        val source = decoder.decodeString()
        return try {
            Formula.compile(source, variables, options)
        } catch (e: FormulaException) {
            throw SerializationException(e.message, e)
        }
    }
}

/**
 * Writes a [CompiledCondition] as its source string and compiles one when reading; see [FormulaSerializer].
 *
 * @property variables What a loaded condition may read.
 * @property options The limits and policy a loaded condition is compiled with.
 */
class FormulaConditionSerializer(
    val variables: FormulaVariables,
    val options: FormulaOptions = FormulaOptions.Default,
) : KSerializer<CompiledCondition> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.awakekt.awake.core.expression.FormulaCondition", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CompiledCondition) {
        encoder.encodeString(value.source)
    }

    /** @throws SerializationException when the string is not a valid condition for [variables]. */
    override fun deserialize(decoder: Decoder): CompiledCondition {
        val source = decoder.decodeString()
        return try {
            FormulaCondition.compile(source, variables, options)
        } catch (e: FormulaException) {
            throw SerializationException(e.message, e)
        }
    }
}
