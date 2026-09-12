/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight

/** Backend-neutral scene feature extension point owned by [RenderSystem3D]. */
interface RenderFeature3D {
    fun collect(world: World, context: RenderFeatureContext3D): RenderContribution
}

/** Read-only frame inputs exposed to a scene feature. */
data class RenderFeatureContext3D(
    val camera: Camera,
    val elapsedTimeSeconds: Float,
)

/** Data a feature contributes; GPU submission remains owned by [RenderSystem3D]. */
data class RenderContribution(
    val draws: List<RenderDrawCommand> = emptyList(),
    val light: SceneLight? = null,
    val environment: EnvironmentUniforms? = null,
)

/** Built-in lighting feature; it reads scene state and returns only generic render inputs. */
internal class SceneLightingFeature3D(
    private val compiler: SceneLightingCompiler,
) : RenderFeature3D {
    override fun collect(world: World, context: RenderFeatureContext3D): RenderContribution =
        RenderContribution(
            light = compiler.sceneLight(world, context.camera),
            environment = compiler.environmentUniforms(world),
        )
}

/** Built-in particle feature; its contribution is inserted between opaque and LOD families. */
internal class SceneParticleFeature3D(
    private val compiler: SceneParticleCompiler,
) : RenderFeature3D {
    override fun collect(world: World, context: RenderFeatureContext3D): RenderContribution {
        val draws = ArrayList<RenderDrawCommand>()
        compiler.appendWorldDrawCalls(draws, world, context.camera)
        return RenderContribution(draws = draws)
    }
}
