/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import kotlinx.coroutines.runBlocking

/**
 * Five textured posts over the ground, and how many opaque draws the frame took.
 *
 * Sharing one material, the posts differ only in placement, so the compiler folds them into one
 * instanced draw; each with its own material they stay five draws. Both must render alike.
 */
fun Renderer.renderRepeatedPropsScene(shareMaterial: Boolean): Pair<ByteArray, Int> {
    val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
    val ground = createMesh(plane(GROUND_HALF, y = 0f))
    val post = createMesh(texturedPlane(POST_HALF, y = CASTER_Y))
    val groundMaterial = createMaterial(LitShadowUniformLayout)
    val shared = texturedMaterial(SolidOrange)
    val materials = POST_XS.map { if (shareMaterial) shared else texturedMaterial(SolidOrange) }
    return try {
        val posts = POST_XS.zip(materials) { x, material ->
            RenderDrawCommand(post, material, model = Mat4().translate(x, 0f, 0f), extraUniformFloats = WHITE_FACTORS)
        }
        val input = compileShadowScene(listOf(RenderDrawCommand(ground, groundMaterial)) + posts)
        renderToTexture(target, input)
        runBlocking { readPixels(target) }.data to input.resolvedOpaqueDraws.size
    } finally {
        ground.destroy()
        post.destroy()
        groundMaterial.destroy()
        (materials + shared).distinct().forEach { it.destroy() }
        target.destroy()
    }
}

private const val POST_HALF = 0.5f
private val POST_XS = listOf(-3f, -1.5f, 0f, 1.5f, 3f)
