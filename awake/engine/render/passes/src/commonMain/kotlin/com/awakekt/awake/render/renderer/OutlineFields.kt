/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.command.MASK_LAYER_COUNT

/**
 * The outline overlay's uniform block: the pixel size of the view it draws over, and each mask
 * layer's colour and width. One colour field per layer, so this holds as many as the engine's
 * mask has.
 */
object OutlineFields {
    /** XY: the width and height, in pixels, of the view the outline is drawn over. */
    val Viewport = UniformField("viewport", GpuDataShape.Vec4)

    /** Mask layer 0's outline colour; its alpha is the outline's opacity. */
    val Color0 = UniformField("color0", GpuDataShape.Vec4)

    /** Mask layer 1's outline colour. */
    val Color1 = UniformField("color1", GpuDataShape.Vec4)

    /**
     * X and Y: mask layers 0 and 1's outline widths in pixels; a width under half a pixel draws none.
     * Z and W: mask layers 0 and 1's edge softness (`0` for a crisp outline, `0..1` for a screen-space blurred glow).
     */
    val Widths = UniformField("widths", GpuDataShape.Vec4)

    init {
        check(MASK_LAYER_COUNT == 2) { "The outline block holds two mask layers; the mask has $MASK_LAYER_COUNT." }
    }
}

/** [OutlineFields] in their declared order. */
val OutlineUniformLayout = UniformLayout(
    OutlineFields.Viewport,
    OutlineFields.Color0,
    OutlineFields.Color1,
    OutlineFields.Widths,
)

/** The narrowest outline drawn, in pixels: a width under it draws nothing, in the overlay's shader too. */
const val OUTLINE_MIN_WIDTH_PIXELS: Float = 0.5f

/**
 * How one mask layer's outline is drawn: in [color], [widthPixels] wide around the silhouette of
 * what the layer masks, with [softness] controlling edge falloff (`0f` for a crisp hard border,
 * up to `1f` for a smooth screen-space Gaussian glow). A mask sub-pass carries it as its pass
 * uniforms, [packed], and the outline overlay reads it back with [unpack].
 */
data class OutlineStyle(
    val color: Color,
    val widthPixels: Float,
    val softness: Float = 0f,
) {
    /** The constructor as released before [softness], kept so compiled callers still link. */
    @Deprecated("Use the constructor with softness.", level = DeprecationLevel.HIDDEN)
    constructor(color: Color, widthPixels: Float) : this(color, widthPixels, softness = 0f)

    /** [copy] as released before [softness]. */
    @Deprecated("Use copy with softness.", level = DeprecationLevel.HIDDEN)
    fun copy(color: Color = this.color, widthPixels: Float = this.widthPixels): OutlineStyle =
        copy(color, widthPixels, softness)

    /** This style as a mask sub-pass's pass uniforms. */
    fun packed(): FloatArray = floatArrayOf(color.r, color.g, color.b, color.a, widthPixels, softness.coerceIn(0f, 1f))

    /**
     * Whether this style draws anything: at least [OUTLINE_MIN_WIDTH_PIXELS] wide, and not fully
     * transparent. The outline overlay draws nothing on a frame whose mask layers have no such style
     * (#1709).
     */
    val draws: Boolean get() = widthPixels >= OUTLINE_MIN_WIDTH_PIXELS && color.a > 0f

    /** Reading a style back from a mask sub-pass. */
    companion object {
        private const val MIN_PACKED_SIZE = 5
        private const val SOFTNESS_INDEX = 5

        /** The style [floats] hold, as [packed] wrote them, or null when they hold none. */
        fun unpack(floats: FloatArray): OutlineStyle? =
            if (floats.size < MIN_PACKED_SIZE) {
                null
            } else {
                val softness = if (floats.size > SOFTNESS_INDEX) floats[SOFTNESS_INDEX].coerceIn(0f, 1f) else 0f
                OutlineStyle(Color(floats[0], floats[1], floats[2], floats[3]), floats[4], softness)
            }
    }
}
