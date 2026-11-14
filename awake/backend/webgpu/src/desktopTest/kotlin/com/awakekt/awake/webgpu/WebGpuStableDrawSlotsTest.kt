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
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.webgpu.renderer.Renderer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A draw culled earlier in the list leaves every later draw in its own slot, so none of them packs
 * its uniform block or uploads its instances again: a single draw's block holds its model, an
 * instanced group's buffer its copies'. Numbered by position, every later draw moved slot and
 * found another's there.
 */
class WebGpuStableDrawSlotsTest {
    private val lens = Lens(eye = Vec3f(0f, 6f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f)

    /** Uniform blocks packed and instance buffers uploaded by a frame of [draws], after the frames before it. */
    private fun packed(renderer: Renderer, draws: List<RenderDrawCommand>): Long {
        val before = renderer.uniformUploads.packedBlocks + renderer.bufferPools.instanceUploads
        val pass = ScenePassCompiler.compile(
            lens = lens,
            drawCalls = draws,
            clipSpace = renderer.clipSpace,
            aspect = 1f,
            drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
        )
        renderer.renderToTexture(target, pass)
        return renderer.uniformUploads.packedBlocks + renderer.bufferPools.instanceUploads - before
    }

    private lateinit var target: com.awakekt.awake.render.texture.RenderTarget

    private fun culledFirstDrawPacksNothing(draws: (Renderer) -> List<RenderDrawCommand>) = webGpuHeadlessScene().use { session ->
        val renderer = session.renderer as Renderer
        target = renderer.createRenderTarget(SIZE, SIZE)
        val all = draws(renderer)
        repeat(2) { packed(renderer, all) }
        assertEquals(0L, packed(renderer, all), "a still frame packs nothing")
        val firstMesh = all.first().mesh
        assertEquals(0L, packed(renderer, all.filter { it.mesh !== firstMesh }), "no later draw moved slot")
        // Groups in reverse order, each group's copies in theirs: a group's instances reversed would be new contents.
        val reordered = all.filter { it.mesh !== firstMesh }.groupBy { it.mesh }.values.reversed().flatten()
        assertEquals(0L, packed(renderer, reordered), "nor when reordered")
    }

    @Test
    fun singleDrawsKeepTheirSlotsWhenAnEarlierOneIsCulled() = culledFirstDrawPacksNothing { renderer ->
        val material = renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total)
        List(DRAWS) { i -> RenderDrawCommand(mesh = renderer.createMesh(quad(VertexFormat.PositionNormalColor)), material = material, model = at(i)) }
    }

    @Test
    fun instancedGroupsKeepTheirSlotsWhenAnEarlierOneIsCulled() = culledFirstDrawPacksNothing { renderer ->
        val white = TextureAsset(data = byteArrayOf(-1, -1, -1, -1), width = 1, height = 1)
        val material = renderer.createMaterial(texture = white, uniformFloatCount = MaterialUniformLayouts.PbrTextured.total, pbrTextures = null)
        val meshes = List(DRAWS) { renderer.createMesh(quad(VertexFormat.PositionNormalColorUv)) }
        List(DRAWS * COPIES) { i -> RenderDrawCommand(mesh = meshes[i % DRAWS], material = material, model = at(i)) }
    }

    private fun at(i: Int) = Mat4().apply { data[12] = (i % 10 - 5) * 0.4f; data[14] = (i / 10 - 2) * 0.4f }

    private fun quad(format: VertexFormat): MeshGeometry {
        val uv = format == VertexFormat.PositionNormalColorUv
        val corners = listOf(-0.1f to -0.1f, 0.1f to -0.1f, 0.1f to 0.1f, -0.1f to 0.1f)
        val vertices = corners.flatMap { (x, z) -> listOf(x, 0f, z, 0f, 1f, 0f, 1f, 1f, 1f) + if (uv) listOf(0f, 0f) else emptyList() }
        return MeshGeometry(vertices.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), format)
    }

    private companion object {
        const val SIZE = 32
        const val DRAWS = 12
        const val COPIES = 3
    }
}
