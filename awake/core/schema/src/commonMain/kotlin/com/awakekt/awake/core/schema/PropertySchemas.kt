/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalSerializationApi::class)

package com.awakekt.awake.core.schema

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Reads this descriptor, and the `@SerialInfo` annotations on its properties from
 * [PropertyAnnotations][PropertyRange], into a [PropertySchema].
 *
 * The result is deterministic: the same descriptor and options give an equal schema, with
 * properties in declaration order.
 *
 * @param options How to read it.
 * @return The schema of the root type, named by its serial name.
 * @throws IllegalArgumentException when an annotation is misapplied: a numeric annotation on a
 * property that is not a number, a range whose minimum exceeds its maximum, or [AssetReference]
 * on a property that is not text. The message names the property.
 */
fun SerialDescriptor.toPropertySchema(options: SchemaOptions = SchemaOptions()): PropertySchema {
    val root = Member(serialName.removeSuffix("?"), this, required = false, annotations = emptyList())
    return SchemaWalker(options).build(root, options.defaults, Position(root.name, 0))
}

/**
 * The default value of each property of [serializer]'s type, as JSON, found by decoding [seed]
 * (an empty object unless given one) and encoding the result with every default written.
 *
 * A descriptor says whether a property has a default but not what it is, so this is how a default
 * is learned. It returns null when [seed] is not enough to build the type, which is the case
 * when a property has no default: that property is then [PropertySchema.required].
 *
 * @param T The type to build.
 * @param serializer The type's own serializer, not a polymorphic one, so no class discriminator is needed.
 * @param json The configuration to decode and encode with, including any serializers module.
 * @param seed Values for the properties that have no default, to build a type that needs some.
 * @return The type with its defaults as a JSON object, or null when it cannot be built from [seed].
 */
fun <T> deriveDefaults(serializer: KSerializer<T>, json: Json, seed: JsonObject = JsonObject(emptyMap())): JsonObject? {
    val full = Json(from = json) {
        encodeDefaults = true
        explicitNulls = true
    }
    return try {
        full.encodeToJsonElement(serializer, full.decodeFromJsonElement(serializer, seed)) as? JsonObject
    } catch (@Suppress("SwallowedException") unbuildable: IllegalArgumentException) {
        // A missing required property, or a type whose own checks reject the seed: no defaults to learn.
        null
    }
}

/**
 * [serializer]'s type as a [PropertySchema] with its defaults filled in.
 *
 * A type with a required property cannot be built from nothing, so the other properties' defaults
 * are learned by seeding each required one with a neutral placeholder (zero, false, empty text, the
 * first enum entry, an empty list or map). A required property keeps no default, so a placeholder
 * never shows. If the type rejects the placeholders too, none of its defaults are known.
 *
 * @param T The type to describe.
 * @param serializer The type's own serializer.
 * @param json The configuration [deriveDefaults] decodes and encodes with.
 * @param semanticTypes Types to read as a richer kind; see [SchemaOptions.semanticTypes].
 * @return The schema, with [PropertySchema.default] set wherever a default could be learned.
 */
fun <T> propertySchemaOf(
    serializer: KSerializer<T>,
    json: Json,
    semanticTypes: Map<String, PropertyKind> = emptyMap(),
): PropertySchema {
    val defaults = deriveDefaults(serializer, json)
        ?: deriveDefaults(serializer, json, placeholderSeed(serializer.descriptor, depth = 0))
    return serializer.descriptor.toPropertySchema(SchemaOptions(semanticTypes, defaults))
}

/** A neutral value for every required property of [descriptor], enough to build the type if it has no other checks. */
private fun placeholderSeed(descriptor: SerialDescriptor, depth: Int): JsonObject = buildJsonObject {
    for (index in 0 until descriptor.elementsCount) {
        if (descriptor.isElementOptional(index)) continue
        placeholderFor(descriptor.getElementDescriptor(index), depth)?.let { put(descriptor.getElementName(index), it) }
    }
}

private fun placeholderFor(descriptor: SerialDescriptor, depth: Int): JsonElement? = when {
    descriptor.isNullable -> JsonNull
    else -> when (descriptor.kind) {
        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE, PrimitiveKind.INT, PrimitiveKind.LONG,
        PrimitiveKind.SHORT, PrimitiveKind.BYTE,
        -> JsonPrimitive(0)
        PrimitiveKind.BOOLEAN -> JsonPrimitive(false)
        PrimitiveKind.STRING -> JsonPrimitive("")
        PrimitiveKind.CHAR -> JsonPrimitive("a")
        SerialKind.ENUM -> JsonPrimitive(descriptor.getElementName(0))
        StructureKind.LIST -> JsonArray(emptyList())
        StructureKind.MAP -> JsonObject(emptyMap())
        StructureKind.CLASS -> if (depth < SchemaOptions.DEFAULT_MAX_DEPTH) placeholderSeed(descriptor, depth + 1) else null
        else -> null
    }
}

private class Member(
    val name: String,
    val descriptor: SerialDescriptor,
    val required: Boolean,
    val annotations: List<Annotation>,
)

private class Position(val path: String, val depth: Int) {
    fun into(name: String) = Position("$path.$name", depth + 1)
}

private class SchemaWalker(private val options: SchemaOptions) {
    fun build(member: Member, default: JsonElement?, at: Position): PropertySchema {
        val descriptor = member.descriptor
        val typeName = descriptor.serialName.removeSuffix("?")
        val kind = kindOf(descriptor, typeName)
        val (constraints, hints) = readAnnotations(member.annotations, kind, at.path)
        return PropertySchema(
            name = member.name,
            kind = kind,
            typeName = typeName,
            nullable = descriptor.isNullable,
            required = member.required,
            default = default,
            constraints = constraints,
            hints = hints,
            enumValues = if (kind == PropertyKind.Enum) enumNames(descriptor) else emptyList(),
            element = elementOf(descriptor, kind, at),
            children = if (descriptor.kind == StructureKind.CLASS && at.depth < options.maxDepth) {
                childrenOf(descriptor, default, at)
            } else {
                emptyList()
            },
        )
    }

    private fun kindOf(descriptor: SerialDescriptor, typeName: String): PropertyKind =
        options.semanticTypes[typeName] ?: when (descriptor.kind) {
            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> PropertyKind.Float
            PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.SHORT, PrimitiveKind.BYTE -> PropertyKind.Int
            PrimitiveKind.BOOLEAN -> PropertyKind.Boolean
            PrimitiveKind.STRING, PrimitiveKind.CHAR -> PropertyKind.Text
            SerialKind.ENUM -> PropertyKind.Enum
            StructureKind.LIST -> PropertyKind.List
            StructureKind.MAP -> PropertyKind.Map
            StructureKind.CLASS, StructureKind.OBJECT -> PropertyKind.Object
            is PolymorphicKind -> PropertyKind.Polymorphic
            else -> PropertyKind.Unknown
        }

    private fun enumNames(descriptor: SerialDescriptor): List<String> =
        (0 until descriptor.elementsCount).map(descriptor::getElementName)

    /** A list's element or a map's value, which is element 0 or element 1 of the descriptor. */
    private fun elementOf(descriptor: SerialDescriptor, kind: PropertyKind, at: Position): PropertySchema? {
        val index = when (kind) {
            PropertyKind.List -> 0
            PropertyKind.Map -> 1
            else -> null
        }
        return if (index == null || at.depth >= options.maxDepth) {
            null
        } else {
            val member = Member("element", descriptor.getElementDescriptor(index), required = false, annotations = emptyList())
            build(member, default = null, at.into("[]"))
        }
    }

    private fun childrenOf(descriptor: SerialDescriptor, defaults: JsonElement?, at: Position): List<PropertySchema> =
        (0 until descriptor.elementsCount).map { index ->
            val name = descriptor.getElementName(index)
            val child = descriptor.getElementDescriptor(index)
            val optional = descriptor.isElementOptional(index)
            val member = Member(name, child, required = !optional, annotations = descriptor.getElementAnnotations(index))
            // A required property has no default; whatever the defaults hold for it is a placeholder.
            val default = if (optional) defaultOf(defaults, name, child.isNullable) else null
            build(member, default, at.into(name))
        }

    /** The JSON default of one property, or `JsonNull` for a nullable one the defaults left out. */
    private fun defaultOf(defaults: JsonElement?, name: String, nullableWithDefault: Boolean): JsonElement? =
        (defaults as? JsonObject)?.let { it[name] ?: JsonNull.takeIf { nullableWithDefault } }

    private fun readAnnotations(annotations: List<Annotation>, kind: PropertyKind, path: String): Pair<PropertyConstraints, PropertyHints> {
        val numeric = kind == PropertyKind.Float || kind == PropertyKind.Int
        fun requireNumeric(annotation: String) =
            require(numeric) { "$path: @$annotation needs a numeric property, but it is $kind" }

        val range = annotations.filterIsInstance<PropertyRange>().firstOrNull()?.let {
            requireNumeric("PropertyRange")
            require(!it.min.isNaN() && !it.max.isNaN() && it.min <= it.max) {
                "$path: @PropertyRange min ${it.min} must not exceed max ${it.max}"
            }
            NumberRange(
                min = it.min.takeIf { value -> value != Double.NEGATIVE_INFINITY },
                max = it.max.takeIf { value -> value != Double.POSITIVE_INFINITY },
                exclusiveMin = it.exclusiveMin,
                exclusiveMax = it.exclusiveMax,
            )
        }
        val slider = annotations.filterIsInstance<PropertySlider>().firstOrNull()?.let {
            requireNumeric("PropertySlider")
            require(it.softMin <= it.softMax && it.step >= 0.0) { "$path: @PropertySlider needs softMin <= softMax and a step of 0 or more" }
            SliderHint(it.softMin, it.softMax, it.step)
        }
        val step = annotations.filterIsInstance<PropertyStep>().firstOrNull()?.let {
            requireNumeric("PropertyStep")
            require(it.step > 0.0) { "$path: @PropertyStep must be greater than 0" }
            it.step
        }
        val assetKind = annotations.filterIsInstance<AssetReference>().firstOrNull()?.kind?.also {
            require(kind == PropertyKind.Text) { "$path: @AssetReference needs a text property, but it is $kind" }
        }
        return PropertyConstraints(range) to PropertyHints(
            slider = slider,
            step = step,
            unit = annotations.filterIsInstance<PropertyUnit>().firstOrNull()?.unit,
            tooltip = annotations.filterIsInstance<PropertyHint>().firstOrNull()?.text,
            hidden = annotations.any { it is PropertyHidden },
            readOnly = annotations.any { it is PropertyReadOnly },
            assetKind = assetKind,
        )
    }
}
