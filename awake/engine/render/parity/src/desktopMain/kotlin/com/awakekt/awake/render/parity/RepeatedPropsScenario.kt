/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import kotlinx.coroutines.runBlocking

/**
 * Five textured posts over the ground, and how many opaque draws the frame took.
 *
 * Sharing one material, the posts differ only in placement, so the compiler folds them into one
 * instanced draw; each with its own material they stay five draws. Both must render alike.
 * [submit] renders through [Renderer.submitToTexture], which does not wait for the GPU.
 *
 * [masked] draws them as cut-out foliage is drawn: a texture whose right half is clear, alpha-masked,
 * so neither half-clear card draws nor casts its clear half. [cullMode] culls them as a single-sided
 * prop is culled; they face the camera, so a back-culled post still shows.
 */
fun Renderer.renderRepeatedPropsScene(
    shareMaterial: Boolean,
    submit: Boolean = false,
    masked: Boolean = false,
    cullMode: CullMode = CullMode.None,
): Pair<ByteArray, Int> {
    val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
    val ground = createMesh(plane(GROUND_HALF, y = 0f))
    // [texturedPlane] winds clockwise seen from above; culled, its posts must face the camera.
    val post = createMesh(texturedPlane(POST_HALF, y = CASTER_Y).let { if (cullMode == CullMode.Back) it.facingUp() else it })
    val groundMaterial = createMaterial(LitShadowUniformLayout)
    val texture = if (masked) HalfClear else SolidOrange
    val shared = texturedMaterial(texture)
    val materials = POST_XS.map { if (shareMaterial) shared else texturedMaterial(texture) }
    return try {
        val posts = POST_XS.zip(materials) { x, material ->
            RenderDrawCommand(
                post,
                material,
                model = Mat4().translate(x, 0f, 0f),
                extraUniformFloats = WHITE_FACTORS,
                alphaMode = if (masked) AlphaMode.Masked else AlphaMode.Opaque,
                cullMode = cullMode,
            )
        }
        val input = compileShadowScene(listOf(RenderDrawCommand(ground, groundMaterial)) + posts)
        if (submit) submitToTexture(target, input) else renderToTexture(target, input)
        runBlocking { readPixels(target) }.data to input.resolvedOpaqueDraws.size
    } finally {
        ground.destroy()
        post.destroy()
        groundMaterial.destroy()
        (materials + shared).distinct().forEach { it.destroy() }
        target.destroy()
    }
}

/** Copies of the props a glTF town repeats, folded into one instanced draw each. */
internal fun writeInstancedPropsEvidence(renderer: Renderer, write: (String, String, Int, ByteArray) -> Unit) {
    write(
        "scene-instanced-masked-cards",
        "Five half-clear masked cards folded into one instanced draw: each draws and casts only its opaque half",
        SCENE_SIZE,
        renderer.renderRepeatedPropsScene(shareMaterial = true, masked = true).first,
    )
    write(
        "scene-instanced-back-culled-posts",
        "Five single-sided posts folded into one back-culled instanced draw, facing the camera",
        SCENE_SIZE,
        renderer.renderRepeatedPropsScene(shareMaterial = true, cullMode = CullMode.Back).first,
    )
}

/** This quad wound counter-clockwise seen from above, as `generate { plane() }` winds its own. */
private fun MeshGeometry.facingUp() = MeshGeometry(vertices, intArrayOf(0, 2, 1, 0, 3, 2), format)

private const val POST_HALF = 0.5f
private val POST_XS = listOf(-3f, -1.5f, 0f, 1.5f, 3f)
