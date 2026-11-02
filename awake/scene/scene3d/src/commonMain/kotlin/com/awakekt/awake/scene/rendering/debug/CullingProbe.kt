/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.debug

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.camera.Camera
import kotlin.math.min

/** What culling decided for one mesh with bounds. */
enum class CullResult {
    /** In the camera's view and not hidden behind an occluder: drawn. */
    Drawn,

    /** Rejected by the camera, but drawn into the shadow maps because it can shadow what the camera sees. */
    ShadowOnly,

    /** Outside the camera's view. */
    OutsideView,

    /** In the camera's view, but hidden behind an occluder. */
    Occluded,

    /**
     * Drawn without being culled at all: a copy of an instanced mesh (`InstancedMeshRenderer`,
     * `InstancedSkinnedMeshRenderer`), whose every copy is drawn every frame.
     */
    NotCulled,
}

/**
 * What [RenderSystem3D]'s culling decided for each mesh with bounds in the last frame it planned,
 * for the `culling` debug source ([CullingDebugSource]). A render system records into it only while
 * it is attached as [RenderSystem3D.cullingProbe]; otherwise culling costs what it did before.
 *
 * Turning [frozen] on keeps culling with the camera as it was at that moment, while the scene still
 * draws from the live camera. Flying the camera away then shows what the frozen view kept and what it
 * dropped.
 *
 * Counts cover every mesh. The boxes kept for drawing are the first [maxBoxes]; the rest are counted
 * in [boxesNotKept].
 */
class CullingProbe(
    /** Most boxes kept in one frame. */
    val maxBoxes: Int = DEFAULT_MAX_BOXES,
) {
    init {
        require(maxBoxes >= 0) { "maxBoxes ($maxBoxes) must be 0 or more" }
    }

    /**
     * Whether culling keeps the camera it had when this turned on. The scene still draws from the
     * live camera; level of detail and billboards still follow it too.
     */
    var frozen: Boolean = false
        set(value) {
            if (!value) frozenCamera = null
            field = value
        }

    /** Meshes drawn last frame. */
    var drawn: Int = 0
        private set

    /** Meshes outside the camera's view last frame, not counting [shadowOnly] ones. */
    var outsideView: Int = 0
        private set

    /** Meshes hidden behind an occluder last frame, not counting [shadowOnly] ones. */
    var occluded: Int = 0
        private set

    /** Meshes the camera rejected last frame that were still drawn into the shadow maps. */
    var shadowOnly: Int = 0
        private set

    /** Copies of instanced meshes last frame, which are drawn without being culled. */
    var notCulled: Int = 0
        private set

    /** Meshes past [maxBoxes] last frame: counted, but their boxes were not kept. */
    var boxesNotKept: Int = 0
        private set

    /** Boxes kept last frame. */
    var boxCount: Int = 0
        private set

    private var bounds = FloatArray(0)
    private var results = arrayOfNulls<CullResult>(0)
    private var frozenCamera: Camera? = null
    private val frozenCorners = Array(FRUSTUM_CORNERS) { Vec3f(0f, 0f, 0f) }

    /** Whether [frozenCorner] holds the frozen view's frustum: true from the first frame planned while [frozen]. */
    val hasFrozenView: Boolean get() = frozen && frozenCamera != null

    /** What culling decided for box [index], below [boxCount]. */
    fun result(index: Int): CullResult = requireNotNull(results[checkBox(index)])

    /** Writes box [index]'s world-space minimum corner into [into], and returns it. */
    fun writeMin(index: Int, into: Vec3f): Vec3f {
        val at = checkBox(index) * FLOATS_PER_BOX
        return into.set(bounds[at], bounds[at + 1], bounds[at + 2])
    }

    /** Writes box [index]'s world-space maximum corner into [into], and returns it. */
    fun writeMax(index: Int, into: Vec3f): Vec3f {
        val at = checkBox(index) * FLOATS_PER_BOX + AXES
        return into.set(bounds[at], bounds[at + 1], bounds[at + 2])
    }

    /**
     * Writes frustum corner [index] (0 to 7, in [Frustum.corners]' order) of the frozen view into
     * [into], with its far plane brought in to at most [MAX_SHOWN_FAR], and returns it.
     */
    fun frozenCorner(index: Int, into: Vec3f): Vec3f {
        check(hasFrozenView) { "Culling is not frozen" }
        return into.set(frozenCorners[index])
    }

    /** Starts a frame: forgets the last one's counts and boxes. */
    internal fun begin() {
        drawn = 0
        outsideView = 0
        occluded = 0
        shadowOnly = 0
        notCulled = 0
        boxesNotKept = 0
        boxCount = 0
    }

    /**
     * The camera culling uses this frame: [camera] itself, or while [frozen], a copy of the camera it
     * had on the first frame after [frozen] turned on.
     */
    internal fun cullingCamera(camera: Camera, aspect: Float): Camera {
        if (!frozen) return camera
        return frozenCamera ?: camera.copy(lens = camera.lens.copied(camera.lens.far)).also { copy ->
            val shown = Frustum.corners(camera.lens.copied(min(camera.lens.far, MAX_SHOWN_FAR)), aspect)
            for (i in 0 until FRUSTUM_CORNERS) frozenCorners[i].set(shown[i])
            frozenCamera = copy
        }
    }

    /** Counts [result], and keeps [worldBounds] with it while there is room. */
    internal fun record(result: CullResult, worldBounds: Aabb) {
        when (result) {
            CullResult.Drawn -> drawn++
            CullResult.ShadowOnly -> shadowOnly++
            CullResult.OutsideView -> outsideView++
            CullResult.Occluded -> occluded++
            CullResult.NotCulled -> notCulled++
        }
        if (boxCount >= maxBoxes) {
            boxesNotKept++
            return
        }
        ensureRoom(boxCount + 1)
        val at = boxCount * FLOATS_PER_BOX
        bounds[at] = worldBounds.min.x
        bounds[at + 1] = worldBounds.min.y
        bounds[at + 2] = worldBounds.min.z
        bounds[at + AXES] = worldBounds.max.x
        bounds[at + AXES + 1] = worldBounds.max.y
        bounds[at + AXES + 2] = worldBounds.max.z
        results[boxCount] = result
        boxCount++
    }

    private fun ensureRoom(boxes: Int) {
        if (results.size >= boxes) return
        val size = min(maxBoxes, maxOf(boxes, results.size * 2, INITIAL_BOXES))
        bounds = bounds.copyOf(size * FLOATS_PER_BOX)
        results = results.copyOf(size)
    }

    private fun checkBox(index: Int): Int {
        if (index !in 0 until boxCount) throw IndexOutOfBoundsException("Box $index of $boxCount")
        return index
    }

    /** The probe's defaults. */
    companion object {
        /** Boxes kept by default: 12 lines each stays well inside a frame's debug-line limit. */
        const val DEFAULT_MAX_BOXES: Int = 4096

        /** Farthest the frozen view's frustum is drawn, so a far plane kilometres out still reads as a box. */
        const val MAX_SHOWN_FAR: Float = 60f

        private const val AXES = 3
        private const val FLOATS_PER_BOX = AXES * 2
        private const val FRUSTUM_CORNERS = 8
        private const val INITIAL_BOXES = 64
    }
}

/** A copy of this lens, with its own vectors, at [far]. */
private fun Lens.copied(far: Float): Lens =
    Lens(
        eye = Vec3f(eye.x, eye.y, eye.z),
        center = Vec3f(center.x, center.y, center.z),
        up = Vec3f(up.x, up.y, up.z),
        fovYRadians = fovYRadians,
        near = near,
        far = far,
    ).also {
        it.projection = projection
        it.orthoHalfHeight = orthoHalfHeight
    }
