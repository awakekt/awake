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
import com.awakekt.awake.core.spatial.IntIdSet
import com.awakekt.awake.core.spatial.SpatialGrid
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.ShadowCasterVolume
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.CONSERVATIVE_ASPECT
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.debug.CullResult
import com.awakekt.awake.scene.rendering.debug.CullingProbe
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private const val HALF = 0.5f

/**
 * Per-frame culling inputs shared by every 3D draw family.
 *
 * [casters] is what can shadow this frame, null when nothing does; [casterIds] is the spatial
 * index's answer to the same question, null when there is no index. [cullingCamera] is the camera
 * the culling tests use: [camera] itself, unless a [CullingProbe] froze culling.
 */
internal class FrameCulling(
    val camera: Camera,
    val visible: IntIdSet?,
    val planes: List<Plane>?,
    val occlusionViewProjection: Mat4?,
    val casters: ShadowCasterVolume? = null,
    val casterIds: IntIdSet? = null,
    val cullingCamera: Camera = camera,
    val aspect: Float = CONSERVATIVE_ASPECT,
) {
    /** [planes], or with a spatial index, the view's planes built the first time a billboard asks. */
    internal var billboardPlanes: List<Plane>? = planes
}

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
    private val visibleIds = IntIdSet()
    private val casterIds = IntIdSet()
    private val scratchBounds = Aabb(Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, 0f))

    /**
     * Off-screen shadow casters smaller than this, as their bounds' radius over their distance
     * from the camera, are not drawn into the shadow maps. 0 keeps every one.
     */
    var minShadowCasterRatio: Float = 0f

    /** Records each bounded mesh's result while set, for the `culling` debug source; it can also freeze culling. */
    var probe: CullingProbe? = null

    /** Whether the last [passesCulling] that failed did so because the mesh was outside the view. */
    private var lastOutsideView = false

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
        val probe = probe
        probe?.begin()
        // The camera culling tests against: this one, or the one a probe froze.
        val cullCamera = probe?.cullingCamera(camera, cullingAspect) ?: camera

        val occluderFamily = world.family<Transform, Occluder>()
        val occlusionViewProjection: Mat4? = if (occluderFamily.size > 0) {
            val viewProjection = cullCamera.lens.viewProjectionMatrix(cullingAspect, clipSpace)
            occluderFamily.forEach { _, transform, occluder ->
                val worldBounds = occluder.localBounds.transformed(transform.worldMatrix)
                cullCamera.lens.screenBounds(worldBounds, viewProjection, 1f, 1f, clipSpace)
                    ?.let(occluderBounds::add)
            }
            viewProjection
        } else {
            null
        }

        val grid = world.findSpatialIndex()?.grid?.core
        val visible = grid?.let { visibleByIndex(it, cullCamera, cullingAspect) }
        val casters = shadows?.takeUnless { it.isEmpty }
        return FrameCulling(
            camera = camera,
            visible = visible,
            planes = if (visible == null) {
                Frustum.planes(cullCamera.lens, cullingAspect)
            } else {
                null
            },
            occlusionViewProjection = occlusionViewProjection,
            casters = casters,
            casterIds = if (casters != null && grid != null) casterIdsByIndex(grid, casters) else null,
            cullingCamera = cullCamera,
            aspect = cullingAspect,
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
        probe?.let { record(it, verdict, transform, bounds) }
        return verdict
    }

    /**
     * Whether a billboard at [transform], whose mesh fits [bounds] however it turns, can be in
     * [culling]'s view. A billboard faces each view's eye, so its authored box does not describe it;
     * the sphere about its position through the box's farthest corner, scaled as the billboard is
     * drawn, does, in every view. Before this a billboard was never culled, so a portrait camera far
     * from the town drew every glow in it (#1621). Occlusion is not tested.
     */
    fun billboardInView(transform: Transform, bounds: MeshBounds, culling: FrameCulling): Boolean {
        val planes = culling.billboardPlanes
            ?: Frustum.planes(culling.cullingCamera.lens, culling.aspect).also { culling.billboardPlanes = it }
        val local = bounds.localBounds
        val reachX = max(abs(local.min.x), abs(local.max.x))
        val reachY = max(abs(local.min.y), abs(local.max.y))
        val reachZ = max(abs(local.min.z), abs(local.max.z))
        val world = transform.worldMatrix
        // The largest axis scale, as billboardMatrix keeps each axis's.
        val m = world.data
        val scaleSquared = max(m[0] * m[0] + m[1] * m[1] + m[2] * m[2], max(m[4] * m[4] + m[5] * m[5] + m[6] * m[6], m[8] * m[8] + m[9] * m[9] + m[10] * m[10]))
        val radius = sqrt(reachX * reachX + reachY * reachY + reachZ * reachZ) * sqrt(scaleSquared)
        val x = world.m03
        val y = world.m13
        val z = world.m23
        val inView = planes.intersects(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius)
        if (!inView) lastFrustumCulledCount++
        return inView
    }

    private fun record(probe: CullingProbe, verdict: CullVerdict, transform: Transform, bounds: MeshBounds) {
        val result = when {
            verdict == CullVerdict.Visible -> CullResult.Drawn
            verdict == CullVerdict.ShadowOnly -> CullResult.ShadowOnly
            lastOutsideView -> CullResult.OutsideView
            else -> CullResult.Occluded
        }
        probe.record(result, bounds.writeWorldBounds(transform, scratchBounds))
    }

    /**
     * Records, for the [probe], each copy of an instanced mesh: they are drawn every frame without
     * being culled, so each is [CullResult.NotCulled]. Does nothing without a probe or bounds.
     */
    fun recordNotCulled(localBounds: Aabb?, instances: List<Mat4>) {
        val probe = probe ?: return
        if (localBounds == null) return
        for (i in instances.indices) {
            writeWorldBounds(localBounds, instances[i], scratchBounds)
            probe.record(CullResult.NotCulled, scratchBounds)
        }
    }

    /** Whether the frame's caster volume admits the entity, and it is not below the size cutoff. */
    private fun reachesShadowMaps(entityId: Int, transform: Transform, bounds: MeshBounds, culling: FrameCulling): Boolean {
        val casters = culling.casters ?: return false
        val admitted = culling.casterIds?.contains(entityId)
            ?: casters.admits(bounds.writeWorldBounds(transform, scratchBounds))
        return admitted && !isTooSmallToCast(transform, bounds, culling.cullingCamera)
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
        lastOutsideView = !inFrustum
        if (!inFrustum) {
            lastFrustumCulledCount++
            return false
        }
        val occluded = culling.occlusionViewProjection != null &&
            isOccluded(
                culling.cullingCamera.lens,
                bounds.worldBounds(transform),
                culling.occlusionViewProjection,
                clipSpace,
            )
        if (occluded) lastOccludedCount++
        return !occluded
    }

    private fun visibleByIndex(grid: SpatialGrid, camera: Camera, aspect: Float): IntIdSet {
        visibleIds.clear()
        grid.queryFrustum(camera.lens, aspect, visibleIds)
        return visibleIds
    }

    private fun casterIdsByIndex(grid: SpatialGrid, casters: ShadowCasterVolume): IntIdSet {
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

/** [local] moved by [matrix], written into [into]'s corners: `Aabb.transformed` without the new box. */
private fun writeWorldBounds(local: Aabb, matrix: Mat4, into: Aabb) {
    val cx = (local.min.x + local.max.x) * HALF
    val cy = (local.min.y + local.max.y) * HALF
    val cz = (local.min.z + local.max.z) * HALF
    val ex = (local.max.x - local.min.x) * HALF
    val ey = (local.max.y - local.min.y) * HALF
    val ez = (local.max.z - local.min.z) * HALF
    val centerX = matrix.m00 * cx + matrix.m01 * cy + matrix.m02 * cz + matrix.m03
    val centerY = matrix.m10 * cx + matrix.m11 * cy + matrix.m12 * cz + matrix.m13
    val centerZ = matrix.m20 * cx + matrix.m21 * cy + matrix.m22 * cz + matrix.m23
    val extentX = abs(matrix.m00) * ex + abs(matrix.m01) * ey + abs(matrix.m02) * ez
    val extentY = abs(matrix.m10) * ex + abs(matrix.m11) * ey + abs(matrix.m12) * ez
    val extentZ = abs(matrix.m20) * ex + abs(matrix.m21) * ey + abs(matrix.m22) * ez
    into.min.set(centerX - extentX, centerY - extentY, centerZ - extentZ)
    into.max.set(centerX + extentX, centerY + extentY, centerZ + extentZ)
}
