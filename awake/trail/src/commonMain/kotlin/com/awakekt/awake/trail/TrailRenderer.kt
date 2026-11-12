/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.trail

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.texture.TextureAsset

/** How a trail's colour meets what is behind it. */
enum class TrailBlend {
    /** Adds to what is behind it, so it only brightens: a glowing swing, a beam, a spark's streak. */
    Additive,

    /** Covers what is behind it by its alpha: smoke, a tyre mark, a ribbon. */
    Alpha,
}

/**
 * A trail: a [TrailStrip] of point pairs and how it is drawn. A system fills its pairs, raises
 * [visible] as the trail is revealed and rewrites colours as it fades; [TrailBatch] draws it with one
 * GPU buffer, sized once at [capacity] and updated in place over only the pairs that changed.
 *
 * It is drawn unlit and depth tested in the transparent pass, through its placement's matrix (an
 * entity's world matrix in a scene), and culled by the box around
 * its visible pairs.
 *
 * @param capacity The most pairs it holds, at least 2; its GPU buffer is sized for this once.
 * @property blend Whether it adds to what is behind it or covers it.
 * @property texture Sampled with `u` along the strip and `v` across it, or null for none: the
 * vertices' colours alone.
 * @property tint Straight-alpha colour multiplied into every vertex's: one trail's colour per use,
 * without rewriting its vertices.
 * @property depthWrite Whether fragments that pass the depth test update the depth buffer.
 * @property doubleSided Whether the ribbon is visible from both sides.
 */
class TrailRenderer(
    capacity: Int,
    var blend: TrailBlend = TrailBlend.Additive,
    var texture: TextureAsset? = null,
    var tint: Color = Color.White,
    var depthWrite: Boolean = false,
    var doubleSided: Boolean = true,
) {
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(capacity: Int, blend: TrailBlend = TrailBlend.Additive, texture: TextureAsset? = null, tint: Color = Color.White) :
        this(capacity, blend, texture, tint, depthWrite = false)

    /** The pairs, their colours and which of them changed. */
    val strip: TrailStrip = TrailStrip(capacity)

    /** The most pairs it holds. */
    val capacity: Int get() = strip.capacity

    /** How many pairs, from the first, are drawn this frame; see [TrailStrip.visible]. */
    var visible: Int
        get() = strip.visible
        set(value) {
            strip.visible = value
        }

    /** Sets pair [index]; see [TrailStrip.set]. */
    fun set(index: Int, a: Vec3f, b: Vec3f, colorA: Color, colorB: Color) = strip.set(index, a, b, colorA, colorB)

    /** Sets pair [index]'s colours, keeping its points; see [TrailStrip.setColors]. */
    fun setColors(index: Int, colorA: Color, colorB: Color) = strip.setColors(index, colorA, colorB)
}
