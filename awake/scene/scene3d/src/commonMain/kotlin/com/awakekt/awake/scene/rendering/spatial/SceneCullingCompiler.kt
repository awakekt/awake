/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.spatial

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Plane
import com.awakekt.awake.core.math.ScreenBounds
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.intersects
import com.awakekt.awake.core.math.isOccludedBy
import com.awakekt.awake.core.math.planes
import com.awakekt.awake.core.math.screenBounds
import com.awakekt.awake.core.spatial.SpatialGrid
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.ShadowCasterVolume
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.CONSERVATIVE_ASPECT
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import kotlin.math.max

private const val HALF = 0.5f

/**
 * Per-frame culling inputs shared by every 3D draw family.
 *
 * [casters] is what can shadow this frame, null when nothing does; [casterIds] is the spatial
 * index's answer to the same question, null when there is no index.
 */
internal class FrameCulling(
    val camera: Camera,
    val visible: Set<Int>?,
    val planes: List<Plane>?,
    val occlusionViewProjection: Mat4?,
    val casters: ShadowCasterVolume? = null,
    val casterIds: Set<Int>? = null,
)

/** What one entity's culling decided: drawn, drawn into the shadow maps only, or not drawn. */
internal enum class CullVerdict {
    /** In the camera's view and not occluded: a scene draw, which also casts unless its component opts out. */
    Visible,

    /** Out of the camera's view or occluded, but able to shadow what it sees: a shadow-only draw. */
    ShadowOnly,

    /** Nothing to draw. */
    Culled,
}

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
    private val casterIds = HashSet<Int>()
    private val scratchBounds = Aabb(Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, 0f))

    /**
     * Off-screen shadow casters smaller than this, as their bounds' radius over their distance
     * from the camera, are not drawn into the shadow maps. 0 keeps every one.
     */
    var minShadowCasterRatio: Float = 0f

    var lastOccludedCount: Int = 0
        private set
    var lastFrustumCulledCount: Int = 0
        private set

    /** Entities the camera culled that were still drawn into the shadow maps, last frame. */
    var lastShadowOnlyCount: Int = 0
        private set

    /**
     * The frame's culling snapshot. [shadows] is the volume the frame's shadow maps cover, from
     * `ShadowCasterVolume.set`; null or empty, nothing is drawn for shadows alone, and culling
     * decides exactly what it did before shadow casters had their own test.
     */
    fun prepare(
        world: World,
        camera: Camera,
        aspect: Float = CONSERVATIVE_ASPECT,
        shadows: ShadowCasterVolume? = null,
    ): FrameCulling {
        occluderBounds.clear()
        lastOccludedCount = 0
        lastFrustumCulledCount = 0
        lastShadowOnlyCount = 0
        val cullingAspect = if (camera.lens.projection == Lens.Projection.Orthographic) aspect else max(aspect, CONSERVATIVE_ASPECT)

        val occluderFamily = world.family<Transform, Occluder>()
        val occlusionViewProjection: Mat4? = if (occluderFamily.size > 0) {
            val viewProjection = camera.lens.viewProjectionMatrix(cullingAspect, clipSpace)
            occluderFamily.forEach { _, transform, occluder ->
                val worldBounds = occluder.localBounds.transformed(transform.worldMatrix)
                camera.lens.screenBounds(worldBounds, viewProjection, 1f, 1f, clipSpace)
                    ?.let(occluderBounds::add)
            }
            viewProjection
        } else {
            null
        }

        val grid = world.findSpatialIndex()?.grid?.core
        val visible = grid?.let { visibleByIndex(it, camera, cullingAspect) }
        val casters = shadows?.takeUnless { it.isEmpty }
        return FrameCulling(
            camera = camera,
            visible = visible,
            planes = if (visible == null) {
                Frustum.planes(camera.lens, cullingAspect)
            } else {
                null
            },
            occlusionViewProjection = occlusionViewProjection,
            casters = casters,
            casterIds = if (casters != null && grid != null) casterIdsByIndex(grid, casters) else null,
        )
    }

    /**
     * Whether an entity is drawn, drawn for shadows only, or culled.
     *
     * The camera decides the scene draw exactly as [passesCulling] does. An entity the camera
     * rejects -- outside its view, or occluded -- is still drawn into the shadow maps when
     * [castsShadows] and the frame's caster volume admits it: something off screen or behind a
     * wall can still shadow what the camera sees, so camera occlusion never drops a caster.
     */
    fun classify(
        entityId: Int,
        transform: Transform,
        bounds: MeshBounds,
        culling: FrameCulling,
        castsShadows: Boolean = true,
    ): CullVerdict {
        val verdict = when {
            passesCulling(entityId, transform, bounds, culling) -> CullVerdict.Visible
            castsShadows && reachesShadowMaps(entityId, transform, bounds, culling) -> CullVerdict.ShadowOnly
            else -> CullVerdict.Culled
        }
        if (verdict == CullVerdict.ShadowOnly) lastShadowOnlyCount++
        return verdict
    }

    /** Whether the frame's caster volume admits the entity, and it is not below the size cutoff. */
    private fun reachesShadowMaps(entityId: Int, transform: Transform, bounds: MeshBounds, culling: FrameCulling): Boolean {
        val casters = culling.casters ?: return false
        val admitted = culling.casterIds?.contains(entityId)
            ?: casters.admits(bounds.writeWorldBounds(transform, scratchBounds))
        return admitted && !isTooSmallToCast(transform, bounds, culling.camera)
    }

    /** Whether [bounds] is below [minShadowCasterRatio] seen from [camera]'s eye. */
    private fun isTooSmallToCast(transform: Transform, bounds: MeshBounds, camera: Camera): Boolean {
        if (minShadowCasterRatio <= 0f) return false
        val box = bounds.writeWorldBounds(transform, scratchBounds)
        val hx = (box.max.x - box.min.x) * HALF
        val hy = (box.max.y - box.min.y) * HALF
        val hz = (box.max.z - box.min.z) * HALF
        val eye = camera.lens.eye
        val dx = box.min.x + hx - eye.x
        val dy = box.min.y + hy - eye.y
        val dz = box.min.z + hz - eye.z
        val radiusSquared = hx * hx + hy * hy + hz * hz
        val distanceSquared = dx * dx + dy * dy + dz * dz
        return radiusSquared < minShadowCasterRatio * minShadowCasterRatio * distanceSquared
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
            culling.planes?.let { planes -> bounds.intersects(transform, planes) } != false
        }
        if (!inFrustum) {
            lastFrustumCulledCount++
            return false
        }
        val occluded = culling.occlusionViewProjection != null &&
            isOccluded(
                culling.camera.lens,
                bounds.worldBounds(transform),
                culling.occlusionViewProjection,
                clipSpace,
            )
        if (occluded) lastOccludedCount++
        return !occluded
    }

    private fun visibleByIndex(grid: SpatialGrid, camera: Camera, aspect: Float): MutableSet<Int> {
        visibleIds.clear()
        grid.queryFrustum(camera.lens, aspect, visibleIds)
        return visibleIds
    }

    private fun casterIdsByIndex(grid: SpatialGrid, casters: ShadowCasterVolume): MutableSet<Int> {
        casterIds.clear()
        grid.queryVolume(casters, casterIds)
        return casterIds
    }

    @Suppress("ReturnCount") // Null inputs are independent early exits for this optional culling pass.
    private fun isOccluded(
        camera: com.awakekt.awake.core.math.Lens,
        worldBounds: Aabb,
        occlusionViewProjection: Mat4?,
        clipSpace: ClipSpace,
    ): Boolean {
        if (occlusionViewProjection == null || occluderBounds.isEmpty()) return false
        val candidateBounds = camera.screenBounds(
            worldBounds,
            occlusionViewProjection,
            1f,
            1f,
            clipSpace,
        ) ?: return false
        return occluderBounds.any { isOccludedBy(candidateBounds, it) }
    }
}
