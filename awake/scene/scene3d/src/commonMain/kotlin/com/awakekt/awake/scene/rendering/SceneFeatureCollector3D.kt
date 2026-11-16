/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.DEFAULT_SHADOW_CASCADES
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.scene.rendering.debug.TransparentDrawSources

/** Collects built-in and authored feature output without knowing how a frame is submitted. */
internal class SceneFeatureCollector3D(
    clipSpace: ClipSpace,
    private val features: List<RenderFeature3D>,
    shadowCascades: () -> Int = { DEFAULT_SHADOW_CASCADES },
) {
    private val lightingFeature = SceneLightingFeature3D(SceneLightingCompiler(clipSpace, shadowCascades))
    private val particleFeature = SceneParticleFeature3D(ParticleDrawAdapter())

    /** Each feature's name for [TransparentDrawSources], built once: its class's, or its place in the list. */
    val featureNames: List<String> = features.mapIndexed { index, feature -> feature::class.simpleName ?: "feature $index" }

    fun collect(
        world: World,
        camera: Camera,
        elapsedTimeSeconds: Float,
        viewportAspect: Float,
        sources: TransparentDrawSources? = null,
    ): Contributions {
        val context = RenderFeatureContext3D(camera, elapsedTimeSeconds, viewportAspect)
        val particle = particleFeature.collect(world, context)
        val lighting = lightingFeature.collect(world, context)
        val authored = features.map { it.collect(world, context) }
        if (sources != null) {
            sources.addParticles(particle.draws)
            authored.forEachIndexed { index, contribution -> sources.addFeature(index, contribution.draws) }
        }
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
