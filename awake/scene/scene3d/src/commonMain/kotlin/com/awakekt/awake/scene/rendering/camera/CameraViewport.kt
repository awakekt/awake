/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.camera

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Ray
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.ViewportLayout
import com.awakekt.awake.core.math.projectToViewport
import com.awakekt.awake.core.math.rayThroughViewport
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.render.renderer.RenderViewport

/**
 * Resolves a camera into a target rectangle for both rendering and picking. Reuse one per view.
 * The resolved lens is a scratch copy: resizing never changes the camera's authored lens or export.
 */
class CameraViewport {
    /** Camera-relative world extents and top-left framebuffer bounds of the current view. */
    val layout: ViewportLayout = ViewportLayout()

    /** Lens for the current resolved view. Do not mutate or retain across [update] calls. */
    val lens: Lens = Lens.perspective()
    internal val camera = Camera(lens)

    /** GPU viewport and scissor bounds. Replaced only when the resolved pixel bounds change. */
    var viewport: RenderViewport? = null
        private set

    /** Aspect used to build the resolved projection. */
    val aspect: Float get() = layout.aspect

    /** Invalidates a view when its camera or drawable target disappears. */
    fun clear() {
        layout.set(0f, 0f, 0f, 0f)
        viewport = null
    }

    /**
     * Resolves [source] inside physical-pixel target bounds. Returns false for an undrawable target.
     * A configured virtual viewport requires an orthographic source camera.
     */
    @Suppress("LongParameterList") // Camera plus a target rectangle.
    fun update(source: Camera, width: Float, height: Float, x: Float = 0f, y: Float = 0f): Boolean {
        val authored = source.lens
        val policy = source.viewport
        require(policy == null || authored.projection == Lens.Projection.Orthographic) {
            "Virtual viewport scaling requires an orthographic camera."
        }
        if (policy != null) {
            policy.resolve(width, height, layout, x, y)
        } else {
            layout.set(width, height, authored.orthoHalfHeight * 2f * width / height, authored.orthoHalfHeight * 2f, x, y)
        }
        if (!layout.isValid) {
            viewport = null
            return false
        }
        lens.eye.set(authored.eye)
        lens.center.set(authored.center)
        lens.up.set(authored.up)
        lens.fovYRadians = authored.fovYRadians
        lens.near = authored.near
        lens.far = authored.far
        lens.projection = authored.projection
        lens.orthoHalfHeight = if (policy == null) authored.orthoHalfHeight else layout.worldHeight / 2f
        camera.isPrimary = source.isPrimary
        val previous = viewport
        if (previous == null || boundsChanged(previous)) {
            viewport = RenderViewport(layout.x, layout.y, layout.width, layout.height)
        }
        return true
    }

    private fun boundsChanged(previous: RenderViewport): Boolean =
        previous.x != layout.x || previous.y != layout.y || previous.width != layout.width || previous.height != layout.height

    /** World-space ray through a framebuffer pixel; bars and pixels outside the view yield null. */
    fun rayThroughSurface(pixelX: Float, pixelY: Float, clipSpace: ClipSpace): Ray? {
        if (!layout.contains(pixelX, pixelY)) return null
        return lens.rayThroughViewport(
            pixelX - layout.x,
            pixelY - layout.y,
            lens.viewProjectionMatrix(aspect, clipSpace),
            layout.width,
            layout.height,
            clipSpace,
        )
    }

    /** Projects a world point into framebuffer pixels, using the same view as [rayThroughSurface]. */
    fun projectToSurface(world: Vec3f, clipSpace: ClipSpace): Vec2? {
        if (!layout.isValid) return null
        return lens.projectToViewport(
            world,
            lens.viewProjectionMatrix(aspect, clipSpace),
            layout.width,
            layout.height,
            clipSpace,
        )?.also { point ->
            point.x += layout.x
            point.y += layout.y
        }
    }
}
