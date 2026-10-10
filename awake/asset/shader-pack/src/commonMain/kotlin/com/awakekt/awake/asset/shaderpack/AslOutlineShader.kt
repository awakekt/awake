/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.fullScreenTriangleCorner
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.ndcToUv
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.samplerNonFiltering
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.step
import com.awakekt.awake.asset.shaderdsl.textureDepth2dArray
import com.awakekt.awake.asset.shaderdsl.textureSampleArrayLevelDepth
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xy
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.OutlineUniformLayout

/** Depth above this is the cleared mask: nothing of the layer was drawn there. */
private const val FAR_PLANE = 0.9999f

/** A width under half a pixel draws no outline. */
private const val MIN_WIDTH = 0.5f

/** Half the circle's diagonal, for the ring's four diagonal taps. */
private const val DIAGONAL = 0.70710677f

/** The ring's taps, as unit directions; each is read at the full width and at half of it. */
private val RING = listOf(
    1f to 0f,
    DIAGONAL to DIAGONAL,
    0f to 1f,
    -DIAGONAL to DIAGONAL,
    -1f to 0f,
    -DIAGONAL to -DIAGONAL,
    0f to -1f,
    DIAGONAL to -DIAGONAL,
)

/** The radii, as fractions of the width, the ring reads at, so a thin part of the silhouette isn't stepped over. */
private val RADII = listOf(1f, 0.5f)

/**
 * The outline overlay: a full-screen triangle that, for each mask layer, draws the layer's colour
 * where the pixel is outside the layer's silhouette but within the layer's width of it. Layer 0
 * draws over layer 1, so a selection outline stays whole where a hover outline meets it.
 *
 * The mask holds depth, and a texel below the cleared far plane is one the layer drew. The ring is
 * eight directions at the full width and at half of it, in screen pixels.
 */
fun outlineShader(clipSpace: ClipSpace): AslShaderDefinition = shader("outline") {
    val u = uniformBlock(
        "Uniforms",
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 0,
    )
    val handles = u.fieldsFrom(OutlineUniformLayout)
    val viewport = handles.value("viewport")
    val colors = listOf(handles.value("color0"), handles.value("color1"))
    val widths = handles.value("widths")

    val maskGroup = BindingLayout.Standard.slot(BindingSemantic.MaskDepth)
    val mask by textureDepth2dArray(group = maskGroup, binding = 0)
    val maskSampler by samplerNonFiltering(group = maskGroup, binding = 1)

    val out = varyings("VertexOutput")
    val ndc by out.varying(GpuDataShape.Vec2, location = 0)

    vertex {
        val corner = fullScreenTriangleCorner()
        // Overlay: depth test and write are off, so this z is only kept in range.
        out.position set vec4(corner, 1f.lit, 1f.lit)
        ndc set corner
    }

    val farPlane = const("FAR_PLANE", FAR_PLANE)

    fragment {
        val uv = let("uv", ndcToUv(ndc, clipSpace))
        val pixel = let("pixel", vec2(1f.lit, 1f.lit) / viewport.xy)

        // 1 where [layer] drew at [at], 0 on its cleared far plane.
        fun covered(layer: Int, at: AslExpr): AslExpr =
            step(textureSampleArrayLevelDepth(mask, maskSampler, at, layer.lit, 0.lit), farPlane)

        val edges = colors.indices.map { layer ->
            val width = let("width$layer", if (layer == 0) widths.x else widths.y)
            var ring: AslExpr = 0f.lit
            RADII.forEach { radius ->
                RING.forEach { (dx, dy) ->
                    ring = max(ring, covered(layer, uv + vec2(dx.lit, dy.lit) * pixel * width * radius.lit))
                }
            }
            let("edge$layer", ring * (1f.lit - covered(layer, uv)) * step(MIN_WIDTH.lit, width))
        }
        val front = let("front", edges[0] * colors[0].a)
        val back = let("back", edges[1] * colors[1].a * (1f.lit - edges[0]))
        colorOutput(vec4(mix(colors[1].rgb, colors[0].rgb, edges[0]), front + back))
    }
}
