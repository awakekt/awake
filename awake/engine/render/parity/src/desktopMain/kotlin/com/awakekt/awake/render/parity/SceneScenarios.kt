/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.renderer.CullMode
import com.awakekt.awake.render.renderer.DrawCall
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.SceneLight
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.renderer.directionalShadowBox
import com.awakekt.awake.render.renderer.shadowCascadeUniforms
import kotlinx.coroutines.runBlocking

/** The square every scene scenario renders into. */
const val SCENE_SIZE: Int = 128

/**
 * A ground plane with a smaller quad hovering above it, lit from overhead and to one side.
 *
 * The cheapest scene that has a shadow in it, and the one whose Vulkan rendering already has
 * coverage. What matters here is *where* the shadow lands: the mirrored WebGPU lookup this harness
 * was extended to catch put it on the opposite side of the ground from the caster, which is a
 * correct-looking picture unless something compares the two backends.
 */
fun Renderer.renderShadowScene(): ByteArray {
    val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
    val ground = createMesh(plane(GROUND_HALF, y = 0f))
    val caster = createMesh(plane(CASTER_HALF, y = CASTER_Y, r = 1f, g = 0f, b = 0f))
    val material = createMaterial(LitShadowUniformLayout)
    return try {
        renderToTexture(
            target,
            Lens(
                eye = Vec3f(0f, EYE_Y, EYE_Z),
                center = Vec3f(0f, 0f, 0f),
                fovYRadians = 1f,
                near = 0.1f,
                far = 50f,
            ),
            listOf(DrawCall(ground, material), DrawCall(caster, material)),
            // `viewProjection` is supplied, not derived: the renderer renders depth from whatever
            // matrix the light carries and never builds one. `RenderSystem` fills this in for a
            // real scene; a direct `renderToTexture` caller does it here -- and it must be built
            // for this backend's own clip space, which is the whole subject of this comparison.
            SceneLight(direction = Vec3f(LIGHT_X, 1f, 0f), color = Vec3f(1f, 1f, 1f)).let {
                it.copy(viewProjection = directionalShadowBox(it.direction, clipSpace).viewProjection)
            },
        )
        runBlocking { readPixels(target) }.data
    } finally {
        ground.destroy()
        caster.destroy()
        material.destroy()
        target.destroy()
    }
}

/** A horizontal quad at [y], facing up. `VertexFormat.PositionNormalColor`: 9 floats per vertex. */
private fun plane(half: Float, y: Float, r: Float = 1f, g: Float = 1f, b: Float = 1f) = MeshGeometry(
    floatArrayOf(
        -half, y, -half, 0f, 1f, 0f, r, g, b,
        half, y, -half, 0f, 1f, 0f, r, g, b,
        half, y, half, 0f, 1f, 0f, r, g, b,
        -half, y, half, 0f, 1f, 0f, r, g, b,
    ),
    intArrayOf(0, 1, 2, 2, 3, 0),
    // Explicit: MeshGeometry defaults to PositionColorUv (8 floats per vertex), which would read
    // these 9-float vertices at the wrong stride and put the geometry nowhere visible.
    VertexFormat.PositionNormalColor,
)

/** Brightness of the pixel at [x], [y] in a [SCENE_SIZE]-square RGBA readback. */
fun ByteArray.luminanceAt(x: Int, y: Int): Int {
    val offset = (y * SCENE_SIZE + x) * 4
    return (0..2).maxOf { this[offset + it].toInt() and 0xFF }
}

private const val GROUND_HALF = 6f
private const val CASTER_HALF = 1.5f
private const val CASTER_Y = 2f
private const val EYE_Y = 6f
private const val EYE_Z = 9f

/** Off-axis, so the caster's shadow lands beside it rather than underneath, where the caster
 * itself would hide which side it fell on -- which is exactly what this comparison reads. */
private const val LIGHT_X = 2f

/**
 * The studio's own `rotating-cube` scene at [yawRadians], read off its scene file.
 *
 * [renderShadowScene] cannot show self-shadowing, and that is not a gap in it -- it is the scene
 * that catches a mirrored lookup. But its caster is a single-sided plane, which never puts another
 * surface in the shadow map at its own depth, and it fits one `directionalShadowBox`. Measured both
 * ways, that scene scores zero self-shadowed pixels however wrong the bias is. Showing speckle
 * needs a **closed solid** and the **cascaded** fit, which is what this adds.
 *
 * Everything here comes from apps/studio's `rotating-cube.scene.json` and the meshes `StudioModule`
 * registers for it, down to the generators and the `far = 100` -- so what this rasterises is the
 * frame the stipple was reported on rather than a scene chosen to resemble it. The far plane is the
 * part that matters: the cascades are fitted to twice the depth [renderShadowScene] uses, and a
 * `size = 1` cube then covers very few texels of whichever slice contains it.
 */
fun Renderer.renderStudioCubeScene(yawRadians: Float): ByteArray {
    val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
    val ground = createMesh(generate { plane(size = GROUND_SIZE, colored = false) })
    // Uncoloured, unlike the studio's own cube: [selfShadowedPixels] reads luminance, and a
    // per-face vertex colour moves that on its own -- a hue step across a face boundary then
    // counts as speckle. Geometry, camera, light and fit are what this scene is copying; the
    // vertex colours would only add a second signal on top of the one being measured.
    val cube = createMesh(generate { cube(size = CUBE_SIZE, colored = false) })
    val material = createMaterial(LitShadowUniformLayout)
    return try {
        val lens = Lens(
            eye = Vec3f(0f, STUDIO_EYE_Y, STUDIO_EYE_Z),
            center = Vec3f(0f, CUBE_Y, 0f),
            fovYRadians = STUDIO_FOV_Y,
            near = STUDIO_NEAR,
            far = STUDIO_FAR,
        )
        val base = SceneLight(direction = Vec3f(SUN_X, SUN_Y, SUN_Z), color = Vec3f(1f, 1f, 1f))
        // The cascaded fit, not `directionalShadowBox`: the box fit scores zero on this same cube.
        val cascades = shadowCascadeUniforms(base, lens, STUDIO_ASPECT, clipSpace)
        renderToTexture(
            target,
            lens,
            listOf(
                DrawCall(ground, material, cullMode = CullMode.Back),
                // Composed the way `TransformSystem` composes a spun entity's matrix.
                DrawCall(cube, material, model = Mat4().apply { identity() }.translate(0f, CUBE_Y, 0f).rotateY(yawRadians)),
            ),
            base.copy(viewProjection = cascades.viewProjections.first(), cascades = cascades),
        )
        runBlocking { readPixels(target) }.data
    } finally {
        ground.destroy()
        cube.destroy()
        material.destroy()
        target.destroy()
    }
}

/**
 * How many pixels alternate against a locally flat surround -- the signature of shadow acne.
 *
 * A strict local extremum along a row whose two flanks agree with each other. The agreement clause
 * is what makes this usable on a solid: a silhouette or a face boundary is a one-pixel transition
 * that reads as an extremum on the naive test, and without the clause a clean frame of this cube
 * scores dozens of "spikes" that are all its own outline.
 *
 * Smooth shading scores zero however steep the gradient. Zeroing the shader's three bias constants
 * scores in the low thousands, so a zero here means the bias is working rather than that the probe
 * is blind.
 */
fun ByteArray.selfShadowedPixels(): Int {
    var spikes = 0
    for (y in 1 until SCENE_SIZE - 1) {
        for (x in 1 until SCENE_SIZE - 1) {
            val prev = luminanceAt(x - 1, y)
            val here = luminanceAt(x, y)
            val next = luminanceAt(x + 1, y)
            val flanksAgree = kotlin.math.abs(prev - next) <= SPIKE_DELTA
            val risesAbove = here - prev >= SPIKE_DELTA && here - next >= SPIKE_DELTA
            val fallsBelow = prev - here >= SPIKE_DELTA && next - here >= SPIKE_DELTA
            if (flanksAgree && (risesAbove || fallsBelow)) spikes++
        }
    }
    return spikes
}

/** A full turn in twelve steps. The cube spins, and the shortfall this catches is angle-dependent:
 * it appears at some yaws and not others, which is what "it flickers as it rotates" describes. */
val STUDIO_YAWS: List<Float> = List(12) { it * (2f * kotlin.math.PI.toFloat() / 12f) }

/** Above 8-bit dither on a shaded gradient, far below a lit/shadowed flip. */
private const val SPIKE_DELTA = 3

// apps/studio/src/commonMain/resources/assets/examples/rotating-cube.scene.json, and the meshes
// StudioModule registers for it.
private const val CUBE_SIZE = 1f
private const val CUBE_Y = 0.5f
private const val GROUND_SIZE = 10f
private const val STUDIO_EYE_Y = 5f
private const val STUDIO_EYE_Z = 10f
private const val STUDIO_FOV_Y = 0.7853982f
private const val STUDIO_NEAR = 0.1f
private const val STUDIO_FAR = 100f

/** The engine's default sun -- the scene names a directional light and no direction. */
private const val SUN_X = 0.4f
private const val SUN_Y = 0.8f
private const val SUN_Z = 0.4f

/** The studio viewport is wider than tall, and the cascade fit is sensitive to this. */
private const val STUDIO_ASPECT = 1.5f
