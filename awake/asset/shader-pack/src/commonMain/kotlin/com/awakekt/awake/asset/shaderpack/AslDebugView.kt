/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslBlockBuilder
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.cos
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.eq
import com.awakekt.awake.asset.shaderdsl.fract
import com.awakekt.awake.asset.shaderdsl.ge
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.saturate
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.toF32
import com.awakekt.awake.asset.shaderdsl.toU32
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.render.passes.uniforms.NO_DATA_GREY
import com.awakekt.awake.render.passes.uniforms.RenderDebugView

/** How dark a fully shadowed point draws under [RenderDebugView.ShadowVisibility], so the cascade tint stays readable. */
private const val SHADOWED_FLOOR = 0.25f

/** Golden-ratio step between neighbouring layers' hues, so adjacent indices never look alike. */
private const val LAYER_HUE_STEP = 0.618034f
private const val TAU = 6.2831855f

/** The grey every surface takes under [RenderDebugView.Clay], in linear light: mid-grey, so both lit and shaded sides read. */
const val CLAY_ALBEDO: Float = 0.5f

/** The ambient share clay is lit with when the scene sets none: the lit shaders' own default. */
const val CLAY_DEFAULT_AMBIENT: Float = 0.08f

/**
 * What a surface can show under each [RenderDebugView]. A null field is data the surface does not
 * have; that view draws [NO_DATA_GREY].
 *
 * @property normal The normalized shading normal.
 * @property worldPosition The fragment's world position.
 * @property albedo Surface colour before lighting, in the space the shader writes its lit colour.
 * @property shadow What `sampleShadow` returned here.
 * @property shadowCascade `shadowCascade(worldPosition)`: which cascade answered, -1 for none.
 * @property layerWeights Each layer's [debugLayerColor], mixed by its weight.
 * @property dominantLayer The strongest layer's [debugLayerColor].
 * @property lightmap The baked lighting as stored.
 * @property clay [clayRadiance] through the shader's display transform: the colour clay writes.
 * @property jointWeights [jointWeightsColor] of a skinned vertex, interpolated.
 * @property selectedJointWeight [selectedJointWeight] of a skinned vertex, interpolated.
 */
class DebugSurface(
    val normal: AslExpr,
    val worldPosition: AslExpr,
    val albedo: AslExpr,
    val shadow: AslExpr? = null,
    val shadowCascade: AslExpr? = null,
    val layerWeights: AslExpr? = null,
    val dominantLayer: AslExpr? = null,
    val lightmap: AslExpr? = null,
    val clay: AslExpr? = null,
    val jointWeights: AslExpr? = null,
    val selectedJointWeight: AslExpr? = null,
)

/**
 * [lit] unless the block's debug view selects something else, then that view of [surface].
 *
 * Every scene shader routes its output through this, so a view means the same thing on every
 * surface. The branch is on a uniform, so the lit path pays one compare.
 *
 * @param debugView The block's `UniformFields.DebugView`.
 * @param cameraPosition The eye; only xyz is read.
 * @param surface What this fragment knows.
 * @param lit The shader's normal `vec4` output.
 * @return The colour to write.
 */
fun AslBlockBuilder.debugViewColor(debugView: AslExpr, cameraPosition: AslExpr, surface: DebugSurface, lit: AslExpr): AslExpr {
    val out = variable("debugViewOut", lit)
    val view = let("debugViewCode", toU32(debugView.w + 0.5f.lit))
    iff(view gt 0u.lit) {
        val rgb = variable("debugViewRgb", vec3(NO_DATA_GREY.lit))
        fun show(target: RenderDebugView, value: AslExpr?) {
            if (value != null) iff(view eq target.code.toUInt().lit) { assign(rgb, value) }
        }
        show(RenderDebugView.WorldNormals, surface.normal * 0.5f.lit + vec3(0.5f.lit))
        show(RenderDebugView.LinearDepth, vec3(saturate(dot(surface.worldPosition - cameraPosition.xyz, debugView.xyz))))
        show(RenderDebugView.ShadowVisibility, surface.shadowVisibility())
        show(RenderDebugView.Albedo, surface.albedo)
        show(RenderDebugView.LayerWeights, surface.layerWeights)
        show(RenderDebugView.DominantLayer, surface.dominantLayer)
        show(RenderDebugView.Lightmap, surface.lightmap)
        show(RenderDebugView.Clay, surface.clay)
        show(RenderDebugView.JointWeights, surface.jointWeights)
        show(RenderDebugView.SelectedJointWeight, surface.selectedJointWeight?.let(::weightHeat))
        assign(out, vec4(rgb, 1f.lit))
    }
    return out
}

/**
 * A stable, distinct colour for palette index [layer] (an `f32`): layer 0 is red, 1 green, 2
 * violet, and neighbours never share a hue.
 */
fun debugLayerColor(layer: AslExpr): AslExpr {
    val hue = fract(layer * LAYER_HUE_STEP.lit)
    return vec3(0.5f.lit) + cos((vec3(hue) + vec3(0f.lit, (1f / 3f).lit, (2f / 3f).lit)) * TAU.lit) * 0.5f.lit
}

/**
 * [CLAY_ALBEDO] lit by a sun of colour [sun] at [nDotL], visible by [shadow], plus [ambient]: the
 * linear radiance [RenderDebugView.Clay] shows, with no texture, colour or material. Pass it
 * through the shader's own display transform. Every lit shader gives it the same terms, so clay is
 * the same on every surface.
 *
 * @param nDotL The sun's cosine on the geometric normal, 0 with no sun.
 * @param shadow The sun's visibility, 1 for a shader without shadows.
 * @param sun The sun's colour, `vec3`.
 * @param ambient The ambient share, `f32`.
 */
fun clayRadiance(nDotL: AslExpr, shadow: AslExpr, sun: AslExpr, ambient: AslExpr): AslExpr =
    vec3(CLAY_ALBEDO.lit) * (vec3(ambient) + sun * (nDotL * shadow))

/** The scene's ambient share, the `w` of its light colour, or [CLAY_DEFAULT_AMBIENT] when the scene sets none. */
fun clayAmbient(lightColor: AslExpr): AslExpr = select(CLAY_DEFAULT_AMBIENT.lit, lightColor.w, lightColor.w gt 0f.lit)

/**
 * The [RenderDebugView.JointWeights] colour of a skinned vertex: each of its four joints'
 * [debugLayerColor], times that joint's weight, summed.
 *
 * @param joints The vertex's joint indices, `vec4<u32>`.
 * @param weights The vertex's joint weights, `vec4`.
 */
fun jointWeightsColor(joints: AslExpr, weights: AslExpr): AslExpr =
    debugLayerColor(toF32(joints.x)) * weights.x + debugLayerColor(toF32(joints.y)) * weights.y +
        debugLayerColor(toF32(joints.z)) * weights.z + debugLayerColor(toF32(joints.w)) * weights.w

/**
 * How much joint [selected] weighs on a skinned vertex: the weights of the slots that name it,
 * summed, 0 when none does.
 *
 * @param joints The vertex's joint indices, `vec4<u32>`.
 * @param weights The vertex's joint weights, `vec4`.
 * @param selected The joint, an `f32` index into the skin's joints.
 */
fun selectedJointWeight(joints: AslExpr, weights: AslExpr, selected: AslExpr): AslExpr =
    select(0f.lit, weights.x, toF32(joints.x) eq selected) + select(0f.lit, weights.y, toF32(joints.y) eq selected) +
        select(0f.lit, weights.z, toF32(joints.z) eq selected) + select(0f.lit, weights.w, toF32(joints.w) eq selected)

/** A weight paint ramp: blue at 0, green at a half, red at 1. */
private fun weightHeat(weight: AslExpr): AslExpr {
    val clamped = saturate(weight)
    val blue = vec3(0f.lit, 0f.lit, 1f.lit)
    val green = vec3(0f.lit, 1f.lit, 0f.lit)
    val red = vec3(1f.lit, 0f.lit, 0f.lit)
    return select(mix(blue, green, clamped * 2f.lit), mix(green, red, clamped * 2f.lit - 1f.lit), clamped gt 0.5f.lit)
}

/** The cascade's tint, darkened where shadowed; grey where no cascade answered. */
private fun DebugSurface.shadowVisibility(): AslExpr? {
    if (shadow == null || shadowCascade == null) return null
    val tint = select(vec3(NO_DATA_GREY.lit), cascadeTint(shadowCascade), shadowCascade ge 0.lit)
    return tint * (SHADOWED_FLOOR.lit + (1f - SHADOWED_FLOOR).lit * shadow)
}

/** Red, green, blue, yellow for cascades 0-3. */
private fun cascadeTint(cascade: AslExpr): AslExpr = select(
    select(vec3(1f.lit, 0.3f.lit, 0.3f.lit), vec3(0.3f.lit, 1f.lit, 0.3f.lit), cascade eq 1.lit),
    select(vec3(0.3f.lit, 0.45f.lit, 1f.lit), vec3(1f.lit, 1f.lit, 0.3f.lit), cascade eq 3.lit),
    cascade ge 2.lit,
)
