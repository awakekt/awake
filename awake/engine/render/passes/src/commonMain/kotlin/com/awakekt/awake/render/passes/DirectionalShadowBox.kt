/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.times
import kotlin.math.abs

/** The fixed shadow-box constants both the real shadow pass (Vulkan's `RendererDraw3D
 * .lightViewProjection`) and the debug visualizer ([directionalShadowBox]) share -- one copy,
 * not two. ponytail: fixed shadow-box centered at the origin, not scene/camera-fit; upgrade
 * path is a per-frame bounding-box (or camera-frustum) fit once a demo's content moves far
 * from origin. */
const val SHADOW_LIGHT_DISTANCE = 15f
const val SHADOW_ORTHO_HALF_SIZE = 12f
const val SHADOW_NEAR = 0.1f
const val SHADOW_FAR = 40f

/** [view]/[projection] kept separate (not just their product) so a caller that needs the
 * combined matrix ([viewProjection], the real shadow pass) and a caller that needs the raw
 * [view] to invert (the debug visualizer's box wireframe) both work from one function. */
data class DirectionalShadowBox(val eye: Vec3f, val view: Mat4, val projection: Mat4) {
    val viewProjection: Mat4 get() = view * projection
}

/** The directional light's own view-projection box, built the same "view * projection"
 * (Kotlin operator order) way [com.awakekt.awake.core.math.Lens
 * .viewProjectionMatrix] builds a real camera's -- an orthographic projection instead of a
 * perspective one (correct for a directional/parallel-rays light). */
fun directionalShadowBox(
    direction: Vec3f,
    clipSpace: ClipSpace,
    distance: Float = SHADOW_LIGHT_DISTANCE,
    halfSize: Float = SHADOW_ORTHO_HALF_SIZE,
    near: Float = SHADOW_NEAR,
    far: Float = SHADOW_FAR,
): DirectionalShadowBox {
    val normalizedDirection = direction.normalized()
    val eye = normalizedDirection * distance
    val up = if (abs(normalizedDirection.y) > 0.99f) Vec3f(0f, 0f, 1f) else Vec3f(0f, 1f, 0f)
    val view = Mat4.setLookAt(eye = eye, center = Vec3f.ZERO, up = up)
    val projection = Mat4.orthographic(
        left = -halfSize,
        right = halfSize,
        bottom = -halfSize,
        top = halfSize,
        near = near,
        far = far,
        clipSpace = clipSpace,
    )
    if (clipSpace.flipY) projection.m11 *= -1f
    return DirectionalShadowBox(eye, view, projection)
}

/**
 * Depth-buffer units spanned by one texel of a [targetSize]-square depth target rendered with
 * [directionalShadowBox]'s defaults -- the natural scale for a depth-comparison bias.
 *
 * Here rather than in a backend, and taking the size rather than reading it, so a backend can ask
 * for the number without importing the box's constants. Keeping a copy of them next to the
 * shader is how a bias silently comes to mean ~40x more world-space offset than it reads, which
 * detaches a shadow from its caster.
 */
fun directionalShadowTexelDepthScale(targetSize: Int): Float =
    (2f * SHADOW_ORTHO_HALF_SIZE / targetSize) / (SHADOW_FAR - SHADOW_NEAR)
