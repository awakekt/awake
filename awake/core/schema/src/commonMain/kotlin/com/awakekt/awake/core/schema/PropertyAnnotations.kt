/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.schema

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

/**
 * A constraint on a numeric property: the value must lie between [min] and [max].
 *
 * This is a **constraint**, not a hint: a validator enforces it and the exported JSON Schema carries
 * it as `minimum` and `maximum`. Annotate only a limit that the component's own validation already
 * enforces, so adding the annotation never makes a valid document invalid. Use [PropertySlider] for
 * a range that only guides an editor.
 *
 * @property min Smallest allowed value, or negative infinity for none.
 * @property max Largest allowed value, or positive infinity for none.
 * @property exclusiveMin True when a value equal to [min] is not allowed.
 * @property exclusiveMax True when a value equal to [max] is not allowed.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class PropertyRange(
    val min: Double = Double.NEGATIVE_INFINITY,
    val max: Double = Double.POSITIVE_INFINITY,
    val exclusiveMin: Boolean = false,
    val exclusiveMax: Boolean = false,
)

/**
 * A slider range for a numeric property. A **hint** for editors only: nothing enforces it, and a
 * value outside it is still valid unless a [PropertyRange] says otherwise.
 *
 * @property softMin Value at the slider's low end.
 * @property softMax Value at the slider's high end.
 * @property step Snap interval, or 0 for none.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class PropertySlider(
    val softMin: Double,
    val softMax: Double,
    val step: Double = 0.0,
)

/**
 * The increment an editor steps a numeric property by, for a field that has no slider.
 *
 * @property step Increment, greater than 0.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class PropertyStep(val step: Double)

/**
 * The unit of a numeric property, for an editor to show beside the value.
 *
 * @property unit Short unit label, such as `s`, `m` or `deg`.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class PropertyUnit(val unit: String)

/**
 * Tooltip text for a property.
 *
 * @property text One short sentence saying what the property does.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class PropertyHint(val text: String)

/** Marks a property an editor must not show, such as state a system writes at run time. */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class PropertyHidden

/** Marks a property an editor shows but must not let the user change. */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class PropertyReadOnly

/**
 * Marks a text property as the path of a project asset, so an editor offers a picker.
 *
 * @property kind What the asset is, such as `texture`, `model`, `audio` or `shader`.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class AssetReference(val kind: String)
