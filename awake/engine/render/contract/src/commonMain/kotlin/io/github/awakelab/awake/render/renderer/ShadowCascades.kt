/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.renderer

import io.github.awakelab.awake.core.math.ClipSpace
import io.github.awakelab.awake.core.math.Frustum
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.core.math.times
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

/** How many cascades a directional light splits into unless a caller says otherwise. */
const val DEFAULT_SHADOW_CASCADES = 3
/**
 * How far each cascade's far plane sits, blending a uniform split with a logarithmic one.
 *
 * Neither pure scheme is usable on its own. Uniform splits give the first cascade -- the one
 * covering everything near the camera, where shadow detail is actually looked at -- a slice far
 * too deep to resolve. Logarithmic splits resolve the near field beautifully and hand the last
 * cascade a slab so thin that distant geometry pops between levels as the camera moves. The
 * practical scheme mixes them, which is what every engine ships.
 *
 * @param near The camera's near plane -- the first slice starts here.
 * @param far The far plane the last cascade must reach.
 * @param count How many cascades to split into.
 * @param lambda 0 is uniform, 1 is logarithmic. Toward logarithmic by default, since the near
 * field is where the eye is.
 */
fun cascadeSplitDistances(
    near: Float,
    far: Float,
    count: Int = DEFAULT_SHADOW_CASCADES,
    lambda: Float = DEFAULT_CASCADE_LAMBDA,
): FloatArray {
    require(count > 0) { "A shadow needs at least one cascade, not $count." }
    require(far > near && near > 0f) { "Cascades need a positive near..far range, got $near..$far." }
    val ratio = far / near
    return FloatArray(count) { index ->
        val fraction = (index + 1).toFloat() / count
        val logarithmic = near * ratio.pow(fraction)
        val uniform = near + (far - near) * fraction
        logarithmic * lambda + uniform * (1f - lambda)
    }
}

/**
 * One shadow box per cascade, each fitted to its own slice of [camera]'s frustum.
 *
 * This is what [directionalShadowBox] cannot do: that one covers a fixed volume at the origin, so
 * a camera far from it looks at shadows rendered somewhere else, and a camera looking a long way
 * ahead gets one map stretched over the whole view. Fitting per slice is the entire point of
 * cascades -- the near slice gets a small box and therefore small texels.
 *
 * Fitted to each slice's bounding SPHERE rather than its corners. A tight box around the corners
 * is smaller, and its size changes as the camera turns, which makes every shadow edge in the
 * frame crawl while the camera pans. A sphere is rotation-invariant, so the box stays the same
 * size and only its centre moves -- and that centre is then snapped to whole texels, which is
 * what stops the remaining shimmer. Trading a little resolution for a still image is the trade
 * stable CSM is named for.
 *
 * @param camera The view being shadowed; each cascade is fitted to a slice of its frustum.
 * @param aspect That camera's aspect ratio, which decides how wide the slices are.
 * @param direction The light's direction, defining the box's orientation.
 * @param clipSpace The backend's clip-space convention, for the orthographic projection.
 * @param splits Each cascade's far distance -- see [cascadeSplitDistances].
 * @param texelsPerCascade The depth map's side in pixels, used only for that snapping. A wrong
 * value here costs stability, not correctness.
 * @return one box per entry in [splits], near slice first.
 */
fun cascadeShadowBoxes(
    camera: Lens,
    aspect: Float,
    direction: Vec3f,
    clipSpace: ClipSpace,
    splits: FloatArray = cascadeSplitDistances(camera.near, camera.far),
    texelsPerCascade: Int = DEFAULT_SHADOW_MAP_SIZE,
): List<DirectionalShadowBox> {
    val lightDirection = direction.normalized()
    val up = if (abs(lightDirection.y) > UP_PARALLEL_LIMIT) Vec3f(0f, 0f, 1f) else Vec3f(0f, 1f, 0f)
    var sliceNear = camera.near
    return splits.map { sliceFar ->
        val slice = Lens(
            eye = camera.eye,
            center = camera.center,
            up = camera.up,
            fovYRadians = camera.fovYRadians,
            near = sliceNear,
            far = sliceFar,
        ).also {
            // Carried, not defaulted: a slice that forgets its camera is orthographic is fitted
            // as a cone, and every cascade then covers a volume the camera does not render.
            it.projection = camera.projection
            it.orthoHalfHeight = camera.orthoHalfHeight
        }
        sliceNear = sliceFar
        boxAround(Frustum.corners(slice, aspect), lightDirection, up, clipSpace, texelsPerCascade)
    }
}

/** The stable box around one slice: sphere fit, texel-snapped centre, ortho projection. */
private fun boxAround(
    corners: List<Vec3f>,
    lightDirection: Vec3f,
    up: Vec3f,
    clipSpace: ClipSpace,
    texelsPerCascade: Int,
): DirectionalShadowBox {
    var centerX = 0f
    var centerY = 0f
    var centerZ = 0f
    for (corner in corners) {
        centerX += corner.x
        centerY += corner.y
        centerZ += corner.z
    }
    val count = corners.size.toFloat()
    val center = Vec3f(centerX / count, centerY / count, centerZ / count)
    var radius = 0f
    for (corner in corners) {
        radius = max(radius, (corner - center).length3())
    }
    // Rounded up to a whole texel so the box's size is stable against float wobble in the
    // corners, which would otherwise resize it slightly every frame and undo the snapping below.
    val texelWorldSize = radius * 2f / texelsPerCascade
    val snapped = Vec3f(
        floor(center.x / texelWorldSize) * texelWorldSize,
        floor(center.y / texelWorldSize) * texelWorldSize,
        floor(center.z / texelWorldSize) * texelWorldSize,
    )
    // Behind the slice by its own radius plus depth margin, so nothing between the light and the
    // slice is clipped away and left unable to cast into it.
    val eye = snapped + lightDirection * (radius + radius * DEPTH_MARGIN)
    val view = Mat4.setLookAt(eye = eye, center = snapped, up = up)
    val projection = Mat4.orthographic(
        left = -radius,
        right = radius,
        bottom = -radius,
        top = radius,
        near = 0f,
        far = radius * (2f + DEPTH_MARGIN * 2f),
        clipSpace = clipSpace,
    )
    if (clipSpace.flipY) projection.m11 *= -1f
    return DirectionalShadowBox(eye, view, projection)
}

/**
 * How far from the camera shadows are drawn, in metres.
 *
 * Beyond it a fragment is lit: cascades are fitted to THIS rather than to the camera's far plane,
 * because a far plane is how far you can SEE and this is how far a shadow is worth resolving. The
 * two are not the same order of magnitude -- a 1000m view fitted to its far plane gets metre-wide
 * texels, and the shadows inside it are blocks regardless of any bias.
 */
const val DEFAULT_SHADOW_DISTANCE = 100f

/** Toward logarithmic: the near field is where shadow detail is looked at. */
const val DEFAULT_CASCADE_LAMBDA = 0.6f

/** The side of one cascade's depth map, matching `DepthTarget`'s own default. */
const val DEFAULT_SHADOW_MAP_SIZE = 2048

/**
 * Rasterizer depth bias for the shadow depth pass, in the units both APIs share: constant in
 * minimum-resolvable-depth steps, slope as a multiple of each polygon's own depth gradient.
 *
 * Applied at the SOURCE, where the receiver-side bias in `lit_shadow` cannot reach: the shader
 * estimates a surface's slope from `nDotL` at the fragment being LIT, but the depth error lives
 * in the map's own texels, whose slope the rasterizer knows exactly per polygon. Without this,
 * a face at a moderate angle to the light shows a per-texel waffle that no receiver constant
 * removes -- swept twice, radius/slope/offset all immune.
 *
 * In the contract because the value is part of the shadow comparison's meaning: both backends
 * must apply the same bias or the same scene renders differently lit per backend.
 */
const val SHADOW_DEPTH_BIAS_CONSTANT = 4f
const val SHADOW_DEPTH_BIAS_SLOPE = 2f

/** Extra depth in front of and behind a slice, as a fraction of its radius, so a caster outside
 * the slice still reaches into it. */
private const val DEPTH_MARGIN = 0.5f

/** Above this the light is near-vertical and the usual up vector is parallel to it. */
private const val UP_PARALLEL_LIMIT = 0.99f
