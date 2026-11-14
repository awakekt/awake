/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.vulkan.renderer.Renderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A draw culled earlier in the list leaves every later draw in its own slot, so none of them writes
 * its uniform block or uploads its instances again: a single draw's block holds its model, an
 * instanced group's buffer its copies'. Numbered by position, every later draw moved slot and
 * found another's there.
 */
class VulkanStableDrawSlotsTest {
    private val lens = Lens(eye = Vec3f(0f, 6f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f)

    /** Uniform blocks written and instance buffers uploaded by a frame of [draws], after the frames before it. */
    private fun written(renderer: Renderer, target: RenderTarget, draws: List<RenderDrawCommand>): Long {
        val before = renderer.graphicsDevice.drawBlockWrites + renderer.bufferPools.instanceUploads
        val pass = ScenePassCompiler.compile(
            lens = lens,
            drawCalls = draws,
            clipSpace = renderer.clipSpace,
            aspect = 1f,
            drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
        )
        renderer.renderToTexture(target, pass)
        return renderer.graphicsDevice.drawBlockWrites + renderer.bufferPools.instanceUploads - before
    }

    private fun culledFirstDrawWritesNothing(draws: (Renderer) -> List<RenderDrawCommand>) {
        val session = vulkanHeadlessScene(SIZE, SIZE)
        try {
            val renderer = session.renderer as Renderer
            val target = renderer.createRenderTarget(SIZE, SIZE)
            val all = draws(renderer)
            try {
                repeat(WARM_FRAMES) { written(renderer, target, all) }
                assertEquals(0L, written(renderer, target, all), "a still frame writes nothing")
                val firstMesh = all.first().mesh
                assertEquals(0L, written(renderer, target, all.filter { it.mesh !== firstMesh }), "no later draw moved slot")
                // Groups in reverse order, each group's copies in theirs: a group's instances reversed would be new contents.
                val reordered = all.filter { it.mesh !== firstMesh }.groupBy { it.mesh }.values.reversed().flatten()
                assertEquals(0L, written(renderer, target, reordered), "nor when reordered")
            } finally {
                all.map { it.mesh as Mesh }.distinct().forEach { it.destroy() }
                all.map { it.material as Material }.distinct().forEach { it.destroy() }
                target.destroy()
            }
        } finally {
            session.close()
        }
    }

    @Test
    fun singleDrawsKeepTheirSlotsWhenAnEarlierOneIsCulled() = culledFirstDrawWritesNothing { renderer ->
        val material = renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total)
        List(DRAWS) { i -> RenderDrawCommand(mesh = renderer.createMesh(quad(VertexFormat.PositionNormalColor)), material = material, model = at(i)) }
    }

    @Test
    fun instancedGroupsKeepTheirSlotsWhenAnEarlierOneIsCulled() = culledFirstDrawWritesNothing { renderer ->
        val white = TextureAsset(data = byteArrayOf(-1, -1, -1, -1), width = 1, height = 1)
        val material = renderer.createMaterial(texture = white, uniformFloatCount = MaterialUniformLayouts.PbrTextured.total, pbrTextures = null)
        val meshes = List(DRAWS) { renderer.createMesh(quad(VertexFormat.PositionNormalColorUv)) }
        List(DRAWS * COPIES) { i -> RenderDrawCommand(mesh = meshes[i % DRAWS], material = material, model = at(i)) }
    }

    @Test
    fun benchmarkThreeThousandRenderersOver190Meshes() {
        val session = vulkanHeadlessScene(SIZE, SIZE)
        try {
            val renderer = session.renderer as Renderer
            val target = renderer.createRenderTarget(SIZE, SIZE)
            val white = TextureAsset(data = byteArrayOf(-1, -1, -1, -1), width = 1, height = 1)
            val material = renderer.createMaterial(white, MaterialUniformLayouts.PbrTextured.total, pbrTextures = null)
            val meshes = List(BENCH_MESHES) { renderer.createMesh(quad(VertexFormat.PositionNormalColorUv)) }
            val all = List(BENCH_RENDERERS) { i ->
                RenderDrawCommand(mesh = meshes[i % BENCH_MESHES], material = material, model = at(i))
            }
            try {
                repeat(WARM_FRAMES) { written(renderer, target, all) }

                var stillUploads = 0L
                repeat(BENCH_FRAMES) { stillUploads += written(renderer, target, all) }

                var culledCopiesUploads = 0L
                val firstMesh = meshes[0]
                repeat(BENCH_FRAMES) { frame ->
                    val filtered = filterFirstMeshCopies(all, firstMesh, drop = (frame % 5) + 1)
                    culledCopiesUploads += written(renderer, target, filtered)
                }

                val withoutFirstGroup = all.filter { it.mesh !== firstMesh }
                written(renderer, target, all)
                var groupToggleUploads = 0L
                repeat(BENCH_FRAMES) { frame ->
                    groupToggleUploads += written(renderer, target, if (frame % 2 == 0) withoutFirstGroup else all)
                }

                assertEquals(0L, stillUploads, "still frames upload nothing")
                assertTrue(culledCopiesUploads <= BENCH_FRAMES.toLong(), "only the mesh whose copy count changed uploads")
                assertEquals(0L, groupToggleUploads, "a whole group leaving and returning re-uploads 0 buffers")
            } finally {
                meshes.forEach { it.destroy() }
                material.destroy()
                target.destroy()
            }
        } finally {
            session.close()
        }
    }

    private fun filterFirstMeshCopies(
        all: List<RenderDrawCommand>,
        firstMesh: Mesh,
        drop: Int,
    ): List<RenderDrawCommand> {
        var seen = 0
        return all.filter { cmd ->
            if (cmd.mesh === firstMesh) {
                seen++
                seen > drop
            } else {
                true
            }
        }
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
        const val WARM_FRAMES = 4
        const val BENCH_RENDERERS = 3_000
        const val BENCH_MESHES = 190
        const val BENCH_FRAMES = 50
    }
}
