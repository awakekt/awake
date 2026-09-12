/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.spatial

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Plane
import com.awakekt.awake.core.math.ScreenBounds
import com.awakekt.awake.core.math.intersects
import com.awakekt.awake.core.math.isOccludedBy
import com.awakekt.awake.core.math.planes
import com.awakekt.awake.core.math.screenBounds
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.CONSERVATIVE_ASPECT
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.mesh.MeshBounds

/** Per-frame culling inputs shared by every 3D draw family. */
internal class FrameCulling(
    val camera: Camera,
    val visible: Set<Int>?,
    val planes: List<Plane>?,
    val occlusionViewProjection: Mat4?,
)

/**
 * Builds one culling snapshot per frame and applies it consistently to every scene draw family.
 *
 * The compiler owns spatial policy and diagnostics; [RenderSystem3D] only decides which ECS
 * components become draw packets. It never reaches into a backend or a game-authored component.
 */
internal class SceneCullingCompiler(
    private val clipSpace: ClipSpace,
) {
    private val occluderBounds = ArrayList<ScreenBounds>()
    private val visibleIds = HashSet<Int>()

    var lastOccludedCount: Int = 0
        private set
    var lastFrustumCulledCount: Int = 0
        private set

    fun prepare(world: World, camera: Camera): FrameCulling {
        occluderBounds.clear()
        lastOccludedCount = 0
        lastFrustumCulledCount = 0

        val occluderFamily = world.family<Transform, Occluder>()
        val occlusionViewProjection: Mat4? = if (occluderFamily.size > 0) {
            val viewProjection = camera.lens.viewProjectionMatrix(CONSERVATIVE_ASPECT, clipSpace)
            occluderFamily.forEach { _, transform, occluder ->
                val worldBounds = occluder.localBounds.transformed(transform.worldMatrix)
                camera.lens.screenBounds(worldBounds, viewProjection)?.let(occluderBounds::add)
            }
            viewProjection
        } else {
            null
        }

        val visible = visibleByIndex(world, camera)
        return FrameCulling(
            camera = camera,
            visible = visible,
            planes = if (visible == null) {
                Frustum.planes(camera.lens, CONSERVATIVE_ASPECT)
            } else {
                null
            },
            occlusionViewProjection = occlusionViewProjection,
        )
    }

    fun passesCulling(
        entityId: Int,
        transform: Transform,
        bounds: MeshBounds,
        culling: FrameCulling,
    ): Boolean {
        val inFrustum = if (culling.visible != null) {
            entityId in culling.visible
        } else {
            culling.planes?.intersects(bounds.worldBounds(transform.worldMatrix)) != false
        }
        if (!inFrustum) {
            lastFrustumCulledCount++
            return false
        }
        val occluded = culling.occlusionViewProjection != null &&
            isOccluded(
                culling.camera.lens,
                bounds.worldBounds(transform.worldMatrix),
                culling.occlusionViewProjection,
            )
        if (occluded) lastOccludedCount++
        return !occluded
    }

    private fun visibleByIndex(world: World, camera: Camera): MutableSet<Int>? {
        val grid = world.findSpatialIndex()?.grid ?: return null
        visibleIds.clear()
        grid.queryFrustum(camera.lens, CONSERVATIVE_ASPECT, visibleIds)
        return visibleIds
    }

    private fun isOccluded(
        camera: com.awakekt.awake.core.math.Lens,
        worldBounds: Aabb,
        occlusionViewProjection: Mat4?,
    ): Boolean {
        if (occlusionViewProjection == null || occluderBounds.isEmpty()) return false
        val candidateBounds = camera.screenBounds(worldBounds, occlusionViewProjection) ?: return false
        return occluderBounds.any { isOccludedBy(candidateBounds, it) }
    }
}
