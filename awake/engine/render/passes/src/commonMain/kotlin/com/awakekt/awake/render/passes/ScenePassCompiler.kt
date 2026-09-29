/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.sortForRecording
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.DEFAULT_SCENE_LIGHT
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.sceneLightUniforms
import com.awakekt.awake.render.passes.uniforms.shadowCascades
import com.awakekt.awake.render.renderer.RenderViewport

/**
 * Shared scene-to-HAL compiler. Scene vocabulary ends at this boundary; backends receive only
 * [GpuPassInput] and must not repeat camera, light, sorting, or pass-planning policy.
 */
object ScenePassCompiler {
    fun compile(
        lens: Lens,
        drawCalls: List<RenderDrawCommand>,
        light: SceneLight? = null,
        environment: EnvironmentUniforms = EnvironmentUniforms.Default,
        clipSpace: ClipSpace,
        aspect: Float,
        viewport: RenderViewport? = null,
        drawPreparer: GpuDrawPreparer? = null,
    ): GpuPassInput {
        val viewProjection = lens.viewProjectionMatrix(aspect, clipSpace)
        val cameraForward = lens.forwardDirection()
        val shadowCascadeData = shadowCascadeData(light, environment)
        val shadowViewProjections = shadowCascadeData?.viewProjections.orEmpty()
        val packedPassUniforms = sceneLightUniforms(light ?: DEFAULT_SCENE_LIGHT, lens.eye).packed
        val gpuEnvironment = environment.toGpuState(viewDepthRange = lens.far)

        // One preparation pass in source order, so every draw keeps its own source index; the
        // shadow-only ones are then kept out of the scene and handed to the shadow passes.
        val shadowOnly = ArrayList<Caster>()
        val resolved = drawPreparer?.let { preparer ->
            val context = GpuDrawPreparationContext(
                viewProjection = viewProjection,
                cameraEye = lens.eye,
                cameraForward = cameraForward,
                passUniforms = packedPassUniforms,
                environment = gpuEnvironment,
                viewport = viewport,
                shadowViewProjections = shadowViewProjections,
                shadowCascadeData = shadowCascadeData,
            )
            val visible = ArrayList<Caster>(drawCalls.size)
            drawCalls.forEachIndexed { index, request ->
                val draw = preparer.prepare(request, index, context) ?: return@forEachIndexed
                val caster = Caster(draw, request.worldBounds)
                if (!request.shadowsOnly) visible += caster else if (!draw.transparent) shadowOnly += caster
            }
            sortForRecording(visible)
        }
        val opaqueCasters = resolved?.opaqueByPipeline?.values?.flatten().orEmpty()
        val casters = if (shadowOnly.isEmpty()) opaqueCasters else opaqueCasters + shadowOnly

        val prePasses = ArrayList<GpuSubPass>()
        // The scene compiler has already applied the authoritative shadow toggle when it
        // constructs [light]. Do not gate again on the environment payload: doing so would drop
        // a valid fallback matrix supplied by the scene path.
        shadowViewProjections.forEachIndexed { layer, cascadeVp ->
            prePasses += GpuSubPass(
                target = null,
                targetLayer = layer,
                viewProjection = cascadeVp,
                resolvedDraws = if (resolved == null) emptyList() else casters.seenBy(cascadeVp),
            )
        }
        if (light != null && environment.shadowsEnabled && resolved != null) {
            prePasses += light.pointShadowPrePasses(casters)
        }
        return GpuPassInput(
            prePasses = prePasses,
            viewProjection = viewProjection,
            cameraEye = lens.eye,
            viewport = viewport,
            passUniforms = packedPassUniforms,
            environment = gpuEnvironment,
            resolvedOpaqueDraws = opaqueCasters.map { it.draw },
            resolvedTransparentDraws = resolved?.transparent?.map { it.draw }.orEmpty(),
            resolvedPath = resolved != null,
            cameraForward = cameraForward,
            shadowCascadeData = shadowCascadeData,
        )
    }
}

/** One depth sub-pass per point-shadow cube face, each into its own layer. */
private fun SceneLight.pointShadowPrePasses(draws: List<Caster>): List<GpuSubPass> =
    pointShadows.flatMap { pointShadow ->
        pointShadow.viewProjections.mapIndexed { face, faceVp ->
            GpuSubPass(
                target = null,
                targetLayer = pointShadow.baseLayer + face,
                viewProjection = faceVp,
                resolvedDraws = draws.seenBy(faceVp),
            )
        }
    }

/** A prepared draw and the world bounds its request carried, kept together through sorting. */
private class Caster(val draw: GpuResolvedDraw, val bounds: Aabb?) : PreparedDraw by draw

/**
 * The casters that can write a texel of the depth map [viewProjection] renders.
 *
 * A caster wholly beside the map, or wholly farther from the light than its far plane, cannot.
 * Nothing is culled on the near side: what sits between the light and the map is left to the
 * rasterizer, as it was before this test existed. Without it every shadow pass drew every visible
 * draw, most of which lie outside all but one cascade.
 */
private fun List<Caster>.seenBy(viewProjection: Mat4): List<GpuResolvedDraw> {
    val planes = castingPlanes(viewProjection)
    return mapNotNull { caster -> caster.draw.takeIf { caster.bounds?.let(planes::admits) != false } }
}

/** Left, right, bottom, top and far clip planes of [viewProjection], as `(x, y, z, w)` quads. */
private fun castingPlanes(viewProjection: Mat4): FloatArray {
    val m = viewProjection.data
    val planes = FloatArray(CASTING_PLANES.size * PLANE_FLOATS)
    CASTING_PLANES.forEachIndexed { plane, (row, sign) ->
        // Column-major: row r of the matrix is m[r], m[4 + r], m[8 + r], m[12 + r].
        for (column in 0 until PLANE_FLOATS) {
            planes[plane * PLANE_FLOATS + column] = m[column * 4 + W_ROW] + sign * m[column * 4 + row]
        }
    }
    return planes
}

/** Whether [box] is at least partly inside every plane. A zero matrix admits everything. */
private fun FloatArray.admits(box: Aabb): Boolean {
    for (p in indices step PLANE_FLOATS) {
        val x = if (this[p] > 0f) box.max.x else box.min.x
        val y = if (this[p + 1] > 0f) box.max.y else box.min.y
        val z = if (this[p + 2] > 0f) box.max.z else box.min.z
        if (this[p] * x + this[p + 1] * y + this[p + 2] * z + this[p + 3] < 0f) return false
    }
    return true
}

private const val W_ROW = 3
private const val PLANE_FLOATS = 4
private val CASTING_PLANES = listOf(0 to 1f, 0 to -1f, 1 to 1f, 1 to -1f, 2 to -1f)

private fun Lens.forwardDirection(): Vec3f = (center - eye).let { direction ->
    val length = direction.length3()
    if (length > 0.0001f) direction.scale(1f / length) else Vec3f(0f, 0f, -1f)
}

private fun shadowCascadeData(
    light: SceneLight?,
    environment: EnvironmentUniforms,
): GpuShadowCascadeData? =
    if (light != null && environment.shadowsEnabled) light.shadowCascades() else null
