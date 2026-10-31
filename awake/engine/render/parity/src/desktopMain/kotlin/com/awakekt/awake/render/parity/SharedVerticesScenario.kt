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
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import kotlinx.coroutines.runBlocking

/**
 * The ground and a textured post cut to its first triangle, as a level of detail is a mesh's own
 * vertices with fewer triangles. With [shareVertices] the triangle is made with
 * [Renderer.createMeshSharingVertices] from the whole post, which is destroyed before the frame:
 * the shared vertex buffer must outlive the mesh that made it. Otherwise it is uploaded on its own.
 */
fun Renderer.renderSharedVerticesScene(shareVertices: Boolean): ByteArray {
    val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
    val ground = createMesh(plane(GROUND_HALF, y = 0f))
    val post = texturedPlane(POST_HALF, y = CASTER_Y)
    val triangle = MeshGeometry(post.vertices, post.indices.copyOf(TRIANGLE), post.format)
    val level = if (shareVertices) {
        val full = createMesh(post)
        createMeshSharingVertices(full, triangle).also { full.destroy() }
    } else {
        createMesh(triangle)
    }
    val groundMaterial = createMaterial(LitShadowUniformLayout)
    val material = texturedMaterial(SolidOrange)
    return try {
        val draws = listOf(
            RenderDrawCommand(ground, groundMaterial),
            RenderDrawCommand(level, material, model = Mat4(), extraUniformFloats = WHITE_FACTORS),
        )
        val input = compileShadowScene(draws)
        renderToTexture(target, input)
        runBlocking { readPixels(target) }.data
    } finally {
        level.destroy()
        ground.destroy()
        groundMaterial.destroy()
        material.destroy()
        target.destroy()
    }
}

private const val POST_HALF = 1.5f
private const val TRIANGLE = 3
