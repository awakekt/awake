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

    /** X and Y: mask layers 0 and 1's outline widths in pixels; a width under half a pixel draws none. */
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

/**
 * How one mask layer's outline is drawn: in [color], [widthPixels] wide around the silhouette of
 * what the layer masks. A mask sub-pass carries it as its pass uniforms, [packed], and the outline
 * overlay reads it back with [unpack].
 */
data class OutlineStyle(val color: Color, val widthPixels: Float) {
    /** This style as a mask sub-pass's pass uniforms. */
    fun packed(): FloatArray = floatArrayOf(color.r, color.g, color.b, color.a, widthPixels)

    /** Reading a style back from a mask sub-pass. */
    companion object {
        private const val PACKED_SIZE = 5

        /** The style [floats] hold, as [packed] wrote them, or null when they hold none. */
        fun unpack(floats: FloatArray): OutlineStyle? =
            if (floats.size < PACKED_SIZE) null else OutlineStyle(Color(floats[0], floats[1], floats[2], floats[3]), floats[4])
    }
}
