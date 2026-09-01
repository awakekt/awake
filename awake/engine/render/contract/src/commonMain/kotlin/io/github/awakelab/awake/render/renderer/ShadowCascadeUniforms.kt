/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.renderer

import io.github.awakelab.awake.core.math.Mat4
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * One frame's cascade set, as the shader reads it.
 *
 * Padded to [MAX_SHADOW_CASCADES] here rather than in each backend, and padded by REPEATING the
 * last real cascade rather than by zeroing: a fragment beyond every real split then lands on the
 * last real cascade, which is the correct answer, instead of on an identity matrix that samples
 * a shadow map at whatever the origin happens to project to.
 *
 * @property viewProjections World space to light clip space, near cascade first. Never empty.
 * @property splitDistances Each cascade's far distance along the view direction, aligned with
 * [viewProjections]. Not uploaded: the shader picks a cascade by testing which one CONTAINS the
 * fragment, which is exact where a distance comparison has to agree with how the boxes were
 * fitted. Kept because the fit produced them and a debug overlay wants them.
 * @property depthScales Per cascade, NDC depth per world unit -- see
 * `UniformFields.CascadeDepthScales`. Defaulted so a caller that only has matrices (a test, a
 * one-box light) still builds: the shader then biases in NDC as it always did, which is right for
 * one cascade and only starts to matter once cascades differ in size.
 * @property worldExtents Per cascade, the world width its shadow map covers. With the map's own
 * resolution, which only the shader knows, this is what makes a texel a distance -- and a texel is
 * the unit acne is measured in, since acne is a surface sampling its own depth one texel away.
 */
class ShadowCascadeUniforms(
    val viewProjections: List<Mat4>,
    val splitDistances: FloatArray,
    val depthScales: FloatArray = FloatArray(viewProjections.size) { scaleAlong(viewProjections[it], DEPTH_AXIS) },
    val worldExtents: FloatArray = FloatArray(viewProjections.size) { 2f / scaleAlong(viewProjections[it], WIDTH_AXIS) },
) {
    init {
        require(viewProjections.isNotEmpty()) { "A shadow needs at least one cascade." }
        require(viewProjections.size == splitDistances.size) {
            "Every cascade needs its own split distance: ${viewProjections.size} matrices, " +
                "${splitDistances.size} splits."
        }
        require(viewProjections.size <= MAX_SHADOW_CASCADES) {
            "${viewProjections.size} cascades exceeds the $MAX_SHADOW_CASCADES the uniform block holds."
        }
    }

    /** How many cascades a depth pass actually renders -- the rest are padding. */
    val count: Int get() = viewProjections.size

    /** One `vec4` per cascade: depth scale in `x`, world extent in `y`, the rest padding. */
    fun depthScaleFloats(): FloatArray {
        val floats = FloatArray(MAX_SHADOW_CASCADES * VEC4)
        for (index in 0 until MAX_SHADOW_CASCADES) {
            val cascade = minOf(index, count - 1)
            floats[index * VEC4] = depthScales[cascade]
            floats[index * VEC4 + 1] = worldExtents[cascade]
        }
        return floats
    }

    /** [MAX_SHADOW_CASCADES] matrices, concatenated, for `UniformFields.CascadeViewProjections`. */
    fun matrixFloats(): FloatArray {
        val floats = FloatArray(MAX_SHADOW_CASCADES * MAT4_FLOATS)
        for (index in 0 until MAX_SHADOW_CASCADES) {
            val matrix = viewProjections[minOf(index, count - 1)]
            matrix.data.copyInto(floats, index * MAT4_FLOATS)
        }
        return floats
    }

    private companion object {
        const val MAT4_FLOATS = 16
        const val VEC4 = 4
        const val WIDTH_AXIS = 0
        const val DEPTH_AXIS = 2
    }
}

/**
 * How much clip space one world unit along [axis] covers, read off a world-to-clip matrix.
 *
 * The rotation in a view matrix moves that scale between components, so no single element answers
 * it; the length of the row does, because rotation preserves length. This is what lets a cascade
 * set built from matrices alone -- a single-box light, which has no fit to ask -- still report the
 * extent and depth range the shader sizes its bias and normal offset in. Defaulting those to
 * zero, as this once did, is a shader with no bias at all: every lit surface self-shadows.
 */
private fun scaleAlong(matrix: Mat4, axis: Int): Float {
    val x = matrix.data[axis]
    val y = matrix.data[axis + ROW_STRIDE]
    val z = matrix.data[axis + 2 * ROW_STRIDE]
    return sqrt(x * x + y * y + z * z)
}

private const val ROW_STRIDE = 4

/**
 * The cascade set for [light] seen through [camera], or null when the light casts no shadow.
 *
 * Null rather than a single fixed box, so a caller can tell "this light has no shadow pass" from
 * "this light has one cascade": the first skips the depth pass entirely.
 */
fun shadowCascadeUniforms(
    light: SceneLight,
    camera: io.github.awakelab.awake.core.math.Lens,
    aspect: Float,
    clipSpace: io.github.awakelab.awake.core.math.ClipSpace,
    count: Int = DEFAULT_SHADOW_CASCADES,
    shadowDistance: Float = DEFAULT_SHADOW_DISTANCE,
): ShadowCascadeUniforms {
    // Cascades stop at the shadow distance, not at the camera's far plane. Fitting to the far
    // plane spends the whole map on ground nobody can resolve: a 1000m view gave a 5m-wide scene
    // cascades 329m, 676m and 2338m across -- texels of 16cm to 114cm, on which a 2m box is six
    // texels wide and every artefact downstream of that is unfixable by bias.
    val splits = cascadeSplitDistances(camera.near, minOf(camera.far, shadowDistance), count)
    val boxes = cascadeShadowBoxes(camera, aspect, light.direction, clipSpace, splits)
    return ShadowCascadeUniforms(
        boxes.map { it.viewProjection },
        splits,
        // |m22| IS ndc-depth-per-world-unit for an orthographic box (see Mat4.orthographic),
        // so the shader gets the conversion without being told the near and far it came from.
        FloatArray(boxes.size) { abs(boxes[it].projection.m22) },
        // 2/m00 is the box's world width, for the same reason: an ortho projection maps that
        // width onto -1..1.
        FloatArray(boxes.size) { 2f / abs(boxes[it].projection.m00) },
    )
}

/**
 * The depth pass's own tiny block: which cascade it is rendering right now.
 *
 * Its own layout, not a slice of the material's, because it changes BETWEEN draws of the same
 * material -- the pass draws every mesh once per cascade.
 */
val CascadePassUniformLayout = UniformLayout(UniformFields.CascadeViewProjection)

/**
 * Which bind group the depth pass's cascade block sits at.
 *
 * In the contract rather than beside the shader that declares it, because the backend that binds
 * the set and the shader that reads it are in modules that cannot see each other -- and a
 * mismatch between them is a validation error naming a set index, not a shader.
 */
val SHADOW_CASCADE_PASS_GROUP: Int =
    io.github.awakelab.awake.render.pipeline.BindingLayout.Standard.slot(
        io.github.awakelab.awake.render.pipeline.ShadowCascadePassBinding,
    )

/**
 * A cascade set that shadows nothing, for a shader that declares the block while shadows are off.
 *
 * A pipeline's uniform block is decided by its SHADER, not by whether shadows happen to be
 * enabled: `lit_shadow` reads material, camera position, fog and cascades whatever the renderer's
 * `shadowsEnabled` says, so handing it a shorter block leaves those fields reading whatever was
 * in the buffer. That renders as a scene which gets DARKER when shadows are turned off.
 *
 * The matrices are zero, so every fragment's `projected.w` is zero and the shader's own
 * containment test rejects every cascade -- the same path a fragment beyond the last cascade
 * takes. The scales are ones rather than derived, because a zero matrix has no scale to derive
 * and an infinite texel would reach the arithmetic before the rejection does.
 */
val UNSHADOWED_CASCADES: ShadowCascadeUniforms = ShadowCascadeUniforms(
    viewProjections = listOf(Mat4().apply { data.fill(0f) }),
    splitDistances = floatArrayOf(Float.MAX_VALUE),
    depthScales = floatArrayOf(1f),
    worldExtents = floatArrayOf(1f),
)
