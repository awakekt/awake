/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.skin

import com.awakekt.awake.compose.foundation.sheet.SheetAxis
import com.awakekt.awake.compose.foundation.sheet.SheetState
import com.awakekt.awake.compose.foundation.sheet.SliceInsets
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.ImageFill

/**
 * An immutable set of named image parts cut from one sheet: build it with [skin], provide it with
 * [LocalSkin], and draw a part by its key with [skinPart][com.awakekt.awake.compose.foundation.skin.skinPart]
 * or [skinBar][com.awakekt.awake.compose.foundation.skin.skinBar].
 *
 * **Before the sheet has decoded** [resolve] answers `null`, and the modifiers draw nothing: no
 * crash, no placeholder. The sheet is read through a function so a web host can hand it over once
 * its decode finishes; parts then resolve on first use and are reused until the function answers a
 * different image, so a draw does one map read and allocates nothing.
 *
 * Not thread-safe: resolve on the thread that draws.
 */
class Skin internal constructor(
    private val sheet: () -> ImageBitmap?,
    /** The art's scale: source pixels per dp. */
    val pixelsPerDp: Float,
    private val specs: Map<PartKey<*>, PartSpec<*>>,
) {
    private var resolvedFor: ImageBitmap? = null
    private val resolved = HashMap<PartKey<*>, Any>()

    /** Every part this skin declares. */
    val keys: Set<PartKey<*>> get() = specs.keys

    /** Whether this skin declares [key]. */
    operator fun contains(key: PartKey<*>): Boolean = key in specs

    /** The part [key] names, or `null` while the sheet has not decoded or this skin lacks the part. */
    fun <R : Any> resolve(key: PartKey<R>): R? {
        val image = sheet() ?: return null
        if (image !== resolvedFor) {
            resolved.clear()
            resolvedFor = image
        }
        @Suppress("UNCHECKED_CAST")
        return (resolved[key] as R?) ?: (specs[key] as PartSpec<R>?)?.resolve(image, pixelsPerDp)?.also { resolved[key] = it }
    }

    /**
     * The nine-slice insets of the frame or bar [key] names, in source pixels, or `null` if this
     * skin lacks it. Known before the sheet has decoded, so a layout can pad by it from the first frame.
     */
    fun sliceInsets(key: PartKey<ImageFill>): SliceInsets? = (specs[key] as? FrameSpec)?.slice

    /** The keys of [required] this skin does not declare, in order. */
    fun missing(required: Collection<PartKey<*>>): List<PartKey<*>> = required.filter { it !in specs }

    /** Fails naming every part of [required] this skin lacks; for a test or the start of an app. */
    fun requireParts(required: Collection<PartKey<*>>) {
        val lacking = missing(required)
        require(lacking.isEmpty()) { "The skin lacks ${lacking.size} part(s): ${lacking.joinToString { it.name }}." }
    }
}

/** Declares the parts of a [Skin]; see [skin]. */
class SkinBuilder internal constructor() {
    internal val specs = LinkedHashMap<PartKey<*>, PartSpec<*>>()

    private fun add(key: PartKey<*>, spec: PartSpec<*>) {
        require(specs.put(key, spec) == null) { "The part ${key.name} is declared twice." }
    }

    /** Declares [key] as the [rect] of the sheet, cut by [slice]; edges and centre repeat per the flags. */
    fun frame(
        key: FrameKey,
        rect: SheetRect,
        slice: SliceInsets = SliceInsets.None,
        repeatEdges: Boolean = false,
        repeatCenter: Boolean = false,
        hollow: Boolean = false,
    ) = add(key, FrameSpec(rect, slice, repeatEdges, repeatCenter, hollow))

    /** Declares [key] as a bar fill, the [rect] of the sheet cut by [slice] so its end caps stay whole. */
    fun bar(key: BarKey, rect: SheetRect, slice: SliceInsets = SliceInsets.None, repeatEdges: Boolean = false) =
        add(key, FrameSpec(rect, slice, repeatEdges, repeatCenter = false, hollow = false))

    /**
     * Declares [key] as [states] laid out in equal frames along [axis] inside [rect], so [rect] is
     * the whole strip, not one frame. Each frame is cut by [slice]; [tiled] repeats edges and centre.
     */
    fun states(
        key: StatesKey,
        rect: SheetRect,
        states: List<SheetState> = listOf(SheetState.Normal, SheetState.Hovered, SheetState.Pressed, SheetState.Disabled),
        axis: SheetAxis = SheetAxis.Row,
        slice: SliceInsets = SliceInsets.None,
        tiled: Boolean = false,
    ) = add(key, StatesSpec(rect, states, axis, slice, tiled))
}

/**
 * Builds a [Skin] from the sheet [image] (a function, for a decode that finishes later) and the
 * parts [declare] adds. [pixelsPerDp] is the art's scale, as [com.awakekt.awake.compose.ui.graphics.ImageFill] takes it.
 */
fun skin(image: () -> ImageBitmap?, pixelsPerDp: Float = 1f, declare: SkinBuilder.() -> Unit): Skin {
    val builder = SkinBuilder().apply(declare)
    return Skin(image, pixelsPerDp, builder.specs.toMap())
}

/** Builds a [Skin] from an already decoded [image]; see the function form. */
fun skin(image: ImageBitmap, pixelsPerDp: Float = 1f, declare: SkinBuilder.() -> Unit): Skin =
    skin({ image }, pixelsPerDp, declare)
