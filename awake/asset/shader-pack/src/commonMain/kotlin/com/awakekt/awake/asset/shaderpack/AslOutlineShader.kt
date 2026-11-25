/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslBlockBuilder
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.fullScreenTriangleCorner
import com.awakekt.awake.asset.shaderdsl.ge
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.le
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.lt
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
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xy
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.OUTLINE_MIN_WIDTH_PIXELS
import com.awakekt.awake.render.renderer.OutlineUniformLayout
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Depth above this is the cleared mask: nothing of the layer was drawn there. */
private const val FAR_PLANE = 0.9999f

/** A width under half a pixel draws no outline, as [OUTLINE_MIN_WIDTH_PIXELS] says on the CPU side. */
private const val MIN_WIDTH = OUTLINE_MIN_WIDTH_PIXELS

/** Softness at or below this keeps the crisp 16-tap outline; above it runs the screen-space blur. */
private const val SOFTNESS_EPSILON = 0.001f

/** Half the circle's diagonal, for the crisp ring's four diagonal taps. */
private const val DIAGONAL = 0.70710677f

/** The crisp ring's taps, as unit directions; each is read at the full width and at half of it. */
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

/** The radii, as fractions of the width, the crisp ring reads at, so a thin part of the silhouette isn't stepped over. */
private val RADII = listOf(1f, 0.5f)

/** Normalized Gaussian sigma for the soft silhouette blur (as a fraction of `widthPixels`). */
private const val SOFT_GAUSSIAN_SIGMA = 0.38f

/** Blur knee at `softness -> 0+` (firm feathered band) and `softness = 1` (full silky Gaussian glow). */
private const val SOFT_KNEE_MIN = 0.10f
private const val SOFT_KNEE_MAX = 0.42f

/** Floor on output alpha when un-premultiplying the two composited outline layers. */
private const val ALPHA_EPSILON = 0.0001f

private data class SoftBlurRing(
    val offsets: List<Pair<Float, Float>>,
    val tapWeight: Float,
)

/**
 * Five concentric staggered rings whose tap counts grow with circumference (6 + 10 + 14 + 16 + 18 = 64 taps),
 * weighted by annulus area * 2D Gaussian * smooth boundary taper so the outer glow reaches zero smoothly inside
 * [OutlineScissor] with no hard cutoff and rounds off sharp polygon silhouettes on both thick and thin meshes.
 */
private val SOFT_BLUR_RINGS: List<SoftBlurRing> = run {
    val specs = listOf(
        Triple(0.14f, 6, 0f),
        Triple(0.30f, 10, (PI / 10.0).toFloat()),
        Triple(0.48f, 14, 0f),
        Triple(0.68f, 16, (PI / 16.0).toFloat()),
        Triple(0.88f, 18, 0f),
    )
    val edges = listOf(0f, 0.22f, 0.39f, 0.58f, 0.78f, 1.0f)
    val rawWeights = specs.mapIndexed { index, (radius, taps, _) ->
        val areaPerTap = (edges[index + 1] * edges[index + 1] - edges[index] * edges[index]) / taps
        val gauss = exp(-0.5 * (radius / SOFT_GAUSSIAN_SIGMA) * (radius / SOFT_GAUSSIAN_SIGMA)).toFloat()
        val taper = (1f - radius * radius).coerceAtLeast(0f)
        areaPerTap * gauss * taper
    }
    val totalWeight = specs.indices.sumOf { i -> (specs[i].second * rawWeights[i]).toDouble() }.toFloat()
    specs.mapIndexed { index, (radius, taps, phase) ->
        val offsets = List(taps) { j ->
            val angle = phase + (2.0 * PI * j / taps)
            (cos(angle).toFloat() * radius) to (sin(angle).toFloat() * radius)
        }
        SoftBlurRing(offsets = offsets, tapWeight = rawWeights[index] / totalWeight)
    }
}

/**
 * The outline overlay: a full-screen triangle that, for each mask layer, draws the layer's colour
 * where the pixel is outside the layer's silhouette but within the layer's width of it. Layer 0
 * draws over layer 1, so a selection outline stays whole where a hover outline meets it.
 *
 * The mask holds depth, and a texel below the cleared far plane is one the layer drew. When a
 * layer's softness is zero, it draws a crisp border from eight directions at full and half width;
 * when softness is positive, it evaluates a single-pass scissored 2D Gaussian blur of the mask so
 * silhouettes glow with smooth, rounded falloff and zero extra render passes or targets.
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

        val sampling = OutlineLayerSampling(uv, pixel, ::covered)
        val edges = colors.indices.map { layer ->
            layerEdge(
                layer = layer,
                width = let("width$layer", if (layer == 0) widths.x else widths.y),
                softness = let("softness$layer", if (layer == 0) widths.z else widths.w),
                sampling = sampling,
            )
        }
        // Straight-alpha Porter-Duff compositing of layer 0 over layer 1 so fractional soft glow keeps pure colour.
        val front = let("front", edges[0] * colors[0].a)
        val back = let("back", edges[1] * colors[1].a * (1f.lit - front))
        val alpha = let("alpha", front + back)
        val rgb = (colors[0].rgb * front + colors[1].rgb * back) / max(alpha, ALPHA_EPSILON.lit)
        colorOutput(vec4(rgb, alpha))
    }
}

private class OutlineLayerSampling(
    val uv: AslExpr,
    val pixel: AslExpr,
    val covered: (Int, AslExpr) -> AslExpr,
)

private fun AslBlockBuilder.layerEdge(
    layer: Int,
    width: AslExpr,
    softness: AslExpr,
    sampling: OutlineLayerSampling,
): AslExpr {
    val edge = variable("edge$layer", 0f.lit)
    // A layer with no outline this frame reads none of its mask samples (#1709). The branch is
    // on a uniform, so every fragment takes the same side of it.
    iff(width ge MIN_WIDTH.lit) {
        val inside = let("inside$layer", sampling.covered(layer, sampling.uv))
        // Pixels inside the silhouette draw no outline; explicit-LOD reads allow skipping all ring taps.
        iff(inside lt 0.5f.lit) {
            val stepUv = let("step$layer", sampling.pixel * width)
            iff(softness le SOFTNESS_EPSILON.lit) {
                var ring: AslExpr = 0f.lit
                RADII.forEach { radius ->
                    RING.forEach { (dx, dy) ->
                        ring = max(ring, sampling.covered(layer, sampling.uv + vec2((dx * radius).lit, (dy * radius).lit) * stepUv))
                    }
                }
                assign(edge, ring)
            }
            iff(softness gt SOFTNESS_EPSILON.lit) {
                val blur = variable("blur$layer", 0f.lit)
                SOFT_BLUR_RINGS.forEach { ring ->
                    var ringSum: AslExpr = 0f.lit
                    ring.offsets.forEach { (ox, oy) ->
                        ringSum = ringSum + sampling.covered(layer, sampling.uv + vec2(ox.lit, oy.lit) * stepUv)
                    }
                    assign(blur, blur + ringSum * ring.tapWeight.lit)
                }
                val knee = mix(SOFT_KNEE_MIN.lit, SOFT_KNEE_MAX.lit, clamp(softness, 0f.lit, 1f.lit))
                val shaped = let("shaped$layer", clamp(blur / knee, 0f.lit, 1f.lit))
                assign(edge, shaped * (2f.lit - shaped))
            }
        }
    }
    return edge
}
