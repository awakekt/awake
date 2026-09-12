/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.command.GpuDrawCommand
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.renderer.DrawCall
import com.awakekt.awake.render.renderer.EnvironmentUniforms
import com.awakekt.awake.render.renderer.SceneLight
import com.awakekt.awake.render.renderer.shadowCascades

/**
 * High-level compiled scene frame state managed by the render graph.
 *
 * Compiles into a hardware-ready [GpuPassInput] before calling [com.awakekt.awake.render.renderer.Renderer.draw].
 * Holds scene camera, draw calls, lighting, and environmental parameters.
 */
data class GpuSceneFrame(
    val lens: Lens,
    val drawCalls: List<DrawCall>,
    val light: SceneLight? = null,
    val environment: EnvironmentUniforms = EnvironmentUniforms.Default,
) {
    /**
     * Translates high-level scene state into a hardware-ready [GpuPassInput].
     */
    fun toPassInput(clipSpace: ClipSpace, aspect: Float): GpuPassInput {
        val vp = lens.viewProjectionMatrix(aspect, clipSpace)
        val opaque = mutableListOf<GpuDrawCommand>()
        val transparent = mutableListOf<GpuDrawCommand>()

        for (dc in drawCalls) {
            val cmd = GpuDrawCommand(
                mesh = dc.mesh,
                material = dc.material,
                transform = dc.model,
                instances = dc.instanceModels?.size ?: 1,
            )
            if (dc.transparent) {
                transparent.add(cmd)
            } else {
                opaque.add(cmd)
            }
        }

        val prePasses = mutableListOf<GpuSubPass>()
        if (environment.shadowsEnabled && light != null) {
            val cascades = light.shadowCascades()
            if (cascades != null) {
                for ((layer, cascadeVp) in cascades.viewProjections.withIndex()) {
                    prePasses.add(
                        GpuSubPass(
                            target = null,
                            targetLayer = layer,
                            viewProjection = cascadeVp,
                            draws = opaque,
                        )
                    )
                }
            }
        }

        return GpuPassInput(
            prePasses = prePasses,
            viewProjection = vp,
            cameraEye = lens.eye,
            opaqueDraws = opaque,
            transparentDraws = transparent,
            passUniforms = FloatArray(0),
        )
    }
}
