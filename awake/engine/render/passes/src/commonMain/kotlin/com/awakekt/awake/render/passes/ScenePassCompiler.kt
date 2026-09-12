/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.prepareAll
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
        val shadowViewProjections = if (light != null && environment.shadowsEnabled) {
            light.shadowCascades()?.viewProjections.orEmpty()
        } else {
            emptyList()
        }
        val packedPassUniforms = sceneLightUniforms(light ?: DEFAULT_SCENE_LIGHT, lens.eye).packed

        val resolved = drawPreparer?.let {
            sortForRecording(
                it.prepareAll(
                    drawCalls,
                    GpuDrawPreparationContext(
                        viewProjection = viewProjection,
                        cameraEye = lens.eye,
                        passUniforms = packedPassUniforms,
                        environment = environment.toGpuState(),
                        viewport = viewport,
                        shadowViewProjections = shadowViewProjections,
                    ),
                ),
            )
        }
        val resolvedOpaque = resolved?.opaqueByPipeline?.values?.flatten().orEmpty()

        val prePasses = ArrayList<GpuSubPass>()
        // The scene compiler has already applied the authoritative shadow toggle when it
        // constructs [light]. Do not gate again on the environment payload: doing so would drop
        // a valid fallback matrix supplied by the scene path.
        shadowViewProjections.forEachIndexed { layer, cascadeVp ->
            prePasses += GpuSubPass(
                target = null,
                targetLayer = layer,
                viewProjection = cascadeVp,
                resolvedDraws = if (resolved == null) emptyList() else resolvedOpaque,
            )
        }
        if (light != null && environment.shadowsEnabled && resolved != null) {
            light.pointShadows.flatMap { pointShadow ->
                pointShadow.viewProjections.mapIndexed { face, faceVp ->
                    GpuSubPass(
                        target = null,
                        targetLayer = pointShadow.baseLayer + face,
                        viewProjection = faceVp,
                        resolvedDraws = resolvedOpaque,
                    )
                }
            }.also { prePasses += it }
        }
        return GpuPassInput(
            prePasses = prePasses,
            viewProjection = viewProjection,
            cameraEye = lens.eye,
            viewport = viewport,
            passUniforms = packedPassUniforms,
            environment = environment.toGpuState(),
            resolvedOpaqueDraws = resolvedOpaque,
            resolvedTransparentDraws = resolved?.transparent.orEmpty(),
            resolvedPath = resolved != null,
        )
    }
}
