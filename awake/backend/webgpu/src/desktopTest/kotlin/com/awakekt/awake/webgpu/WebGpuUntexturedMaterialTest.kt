/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.renderer.createMaterial
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * An untextured material on a `PositionNormalColorUv` mesh draws through the textured pipeline,
 * which samples a base-colour texture it does not have. Drawn once or folded with its copies into
 * the textured instanced pipeline, it fails naming the material and the mesh, not as a cast
 * failure in the recorder.
 */
class WebGpuUntexturedMaterialTest {
    private fun renderCopies(copies: Int) = webGpuHeadlessScene().use { session ->
        val renderer = session.renderer
        val mesh = renderer.createMesh(quad())
        val material = renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total)
        val draws = List(copies) { i -> RenderDrawCommand(mesh = mesh, material = material, model = Mat4().apply { data[12] = i * 0.3f }) }
        val lens = Lens(eye = Vec3f(0f, 3f, 3f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 20f)
        val pass = ScenePassCompiler.compile(
            lens = lens,
            drawCalls = draws,
            clipSpace = renderer.clipSpace,
            aspect = 1f,
            drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
        )
        renderer.renderToTexture(renderer.createRenderTarget(SIZE, SIZE), pass)
    }

    private fun assertNamesTheMissingTexture(copies: Int) {
        val failure = assertFailsWith<IllegalStateException> { renderCopies(copies) }
        val message = failure.message.orEmpty()
        assertTrue("no base-colour texture" in message && "Material@" in message && "Mesh@" in message, message)
    }

    @Test
    fun aSingleDrawFailsNamingTheMaterialAndTheMesh() = assertNamesTheMissingTexture(copies = 1)

    @Test
    fun copiesFoldedIntoTheInstancedPipelineFailTheSameWay() = assertNamesTheMissingTexture(copies = 3)

    private fun quad(): MeshGeometry {
        val corners = listOf(-0.5f to -0.5f, 0.5f to -0.5f, 0.5f to 0.5f, -0.5f to 0.5f)
        val vertices = corners.flatMap { (x, z) -> listOf(x, 0f, z, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f) }
        return MeshGeometry(vertices.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionNormalColorUv)
    }

    private companion object {
        const val SIZE = 32
    }
}
