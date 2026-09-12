/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight

/** Collects built-in and authored feature output without knowing how a frame is submitted. */
internal class SceneFeatureCollector3D(
    clipSpace: ClipSpace,
    private val features: List<RenderFeature3D>,
) {
    private val lightingFeature = SceneLightingFeature3D(SceneLightingCompiler(clipSpace))
    private val particleFeature = SceneParticleFeature3D(SceneParticleCompiler())

    fun collect(world: World, camera: Camera, elapsedTimeSeconds: Float): Contributions {
        val context = RenderFeatureContext3D(camera, elapsedTimeSeconds)
        val particle = particleFeature.collect(world, context)
        val lighting = lightingFeature.collect(world, context)
        val authored = features.map { it.collect(world, context) }
        return Contributions(
            particleDraws = particle.draws,
            authoredDraws = authored.flatMap { it.draws },
            light = authored.firstNotNullOfOrNull { it.light } ?: lighting.light,
            environment = authored.firstNotNullOfOrNull { it.environment }
                ?: lighting.environment
                ?: EnvironmentUniforms.Default,
        )
    }

    data class Contributions(
        val particleDraws: List<RenderDrawCommand>,
        val authoredDraws: List<RenderDrawCommand>,
        val light: SceneLight?,
        val environment: EnvironmentUniforms,
    )
}
