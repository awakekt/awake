/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.schema

import kotlinx.serialization.json.JsonElement

/** What an editor or exporter treats a property as. */
enum class PropertyKind {
    /** A floating-point number. */
    Float,

    /** A whole number. */
    Int,

    /** A true or false value. */
    Boolean,

    /** Text, including an asset path. */
    Text,

    /** One of a fixed set of names; see [PropertySchema.enumValues]. */
    Enum,

    /** An ordered list; see [PropertySchema.element]. */
    List,

    /** A map from text keys to values; see [PropertySchema.element]. */
    Map,

    /** A group of named properties; see [PropertySchema.children]. */
    Object,

    /** One of several types chosen at run time, so it has no fixed children. */
    Polymorphic,

    /** Three numbers: a position, a direction or a scale. Only for a type named in [SchemaOptions.semanticTypes]. */
    Vector3,

    /** A colour with an alpha channel. Only for a type named in [SchemaOptions.semanticTypes]. */
    Color,

    /** A kind this walker does not model, such as a contextual serializer. */
    Unknown,
}

/**
 * A numeric limit a validator enforces.
 *
 * @property min Smallest allowed value, or null for none.
 * @property max Largest allowed value, or null for none.
 * @property exclusiveMin True when a value equal to [min] is not allowed.
 * @property exclusiveMax True when a value equal to [max] is not allowed.
 */
data class NumberRange(
    val min: Double?,
    val max: Double?,
    val exclusiveMin: Boolean = false,
    val exclusiveMax: Boolean = false,
)

/**
 * What a property must satisfy.
 *
 * @property range The numeric limit, or null when there is none.
 */
data class PropertyConstraints(val range: NumberRange? = null)

/**
 * A slider range an editor may offer.
 *
 * @property softMin Value at the low end.
 * @property softMax Value at the high end.
 * @property step Snap interval, or 0 for none.
 */
data class SliderHint(val softMin: Double, val softMax: Double, val step: Double = 0.0)

/**
 * What an editor is told about a property, none of it enforced.
 *
 * @property slider A slider range, or null.
 * @property step The increment to step by, or null.
 * @property unit A unit label, or null.
 * @property tooltip A one-sentence description, or null.
 * @property hidden True when the editor must not show it.
 * @property readOnly True when the editor must not let the user change it.
 * @property assetKind What kind of asset a text property names, or null when it is not an asset path.
 */
data class PropertyHints(
    val slider: SliderHint? = null,
    val step: Double? = null,
    val unit: String? = null,
    val tooltip: String? = null,
    val hidden: Boolean = false,
    val readOnly: Boolean = false,
    val assetKind: String? = null,
)

/**
 * One property of a serializable type, as an editor or a schema exporter reads it.
 *
 * @property name The property name as written in the document. For the root it is the type's serial name.
 * @property kind What to treat the value as.
 * @property typeName The serial name of the value's type, without any nullable marker.
 * @property nullable True when the value may be null.
 * @property required True when a document must give a value because the property has no default.
 * @property default The default value as JSON, or null when it is unknown or the property is required.
 * `JsonNull` means the default is null.
 * @property constraints What the value must satisfy.
 * @property hints What an editor is told about it.
 * @property enumValues The names of an [PropertyKind.Enum]'s entries, in declaration order; empty otherwise.
 * @property element The element schema of a [PropertyKind.List] or the value schema of a [PropertyKind.Map]; null otherwise.
 * @property children The properties of an [PropertyKind.Object] in declaration order; empty otherwise.
 */
data class PropertySchema(
    val name: String,
    val kind: PropertyKind,
    val typeName: String,
    val nullable: Boolean,
    val required: Boolean,
    val default: JsonElement?,
    val constraints: PropertyConstraints = PropertyConstraints(),
    val hints: PropertyHints = PropertyHints(),
    val enumValues: List<String> = emptyList(),
    val element: PropertySchema? = null,
    val children: List<PropertySchema> = emptyList(),
)
