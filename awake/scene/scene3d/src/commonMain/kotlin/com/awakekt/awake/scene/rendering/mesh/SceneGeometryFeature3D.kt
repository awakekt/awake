/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.spatial.FrameCulling
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler

/**
 * Coordinator for authored mesh families. It owns the shared culling snapshot and exposes the
 * two ordering points required by the scene pipeline: ordinary geometry before particles and LOD
 * geometry after particles. The class emits only backend-neutral [RenderDrawCommand]s.
 */
internal class SceneGeometryFeature3D(clipSpace: ClipSpace) {
    private val cullingCompiler = SceneCullingCompiler(clipSpace)
    private val drawCollector = SceneDrawCollector(cullingCompiler)

    val lastOccludedCount: Int get() = cullingCompiler.lastOccludedCount
    val lastFrustumCulledCount: Int get() = cullingCompiler.lastFrustumCulledCount

    fun begin(world: World, camera: Camera, elapsedTimeSeconds: Float): Frame {
        val culling = cullingCompiler.prepare(world, camera)
        return Frame(
            culling = culling,
            beforeParticles = drawCollector.collectBeforeParticles(world, culling, elapsedTimeSeconds),
        )
    }

    fun finish(world: World, frame: Frame, camera: Camera): List<RenderDrawCommand> =
        drawCollector.collectAfterParticles(world, frame.culling, camera)

    class Frame internal constructor(
        internal val culling: FrameCulling,
        val beforeParticles: List<RenderDrawCommand>,
    )
}
