/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.meshlod.GltfLodCooker
import com.awakekt.awake.asset.meshlod.LodCookOutcome
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.lod.LodQuality
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.rendering.mesh.SceneLod
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A model cooked with `MSFT_lod` levels, placed at increasing distances through the project
 * runtime's model path: each copy gets an `LodGroup` on its own, draws the level its size on screen
 * calls for with the model's own material, and the frame draws far fewer triangles than the full
 * model everywhere would.
 */
class LodSceneTest {
    @AfterTest
    fun resetQuality() {
        SceneLod.quality = LodQuality.Default
    }

    @Test
    fun copiesFartherAwayDrawCoarserLevelsAndTheFrameDrawsFewerTriangles() = runTest {
        val renderer = playRocks()
        val drawn = renderer.mainDraws()
        assertEquals(DISTANCES.size, drawn.size)
        val triangles = drawn.map { renderer.triangles(it.mesh) }
        val full = triangles.max()
        assertEquals(full, triangles.first(), "the nearest copy draws the full model: $triangles")
        assertTrue(triangles.zipWithNext().all { (near, far) -> far <= near }, "farther copies never draw more: $triangles")
        assertTrue(triangles.distinct().size >= 3, "at least two levels besides the full one: $triangles")
        assertTrue(triangles.sum() < full * DISTANCES.size / 2, "fewer than half the triangles: ${triangles.sum()} of ${full * DISTANCES.size}")
        assertEquals(1, drawn.map { it.material }.distinct().size, "every level draws with the model's own material")
        assertEquals(3, renderer.sharedLevels, "each of the three levels draws the full model's vertex buffer")
    }

    @Test
    fun theGlobalQualityBringsEveryCopyBackToFullDetail() = runTest {
        SceneLod.quality = LodQuality(bias = -10)
        val renderer = playRocks()
        val triangles = renderer.mainDraws().map { renderer.triangles(it.mesh) }
        assertEquals(1, triangles.distinct().size, "the highest quality setting draws only the full model: $triangles")
    }

    private suspend fun playRocks(): CountingRenderer {
        val cooked = GltfLodCooker.cook(sphereGltf().encodeToByteArray())
        assertEquals(LodCookOutcome.Generated, cooked.outcome)
        val project = loadProject(files(MANIFEST_FILE to MANIFEST, SCENE_FILE to scene(), ROCK to cooked.bytes.decodeToString()))
        val renderer = CountingRenderer()
        val game = app { scene("play") { runProject(project) } }
        game.ready(renderer)
        repeat(FRAMES) { game.update(FRAME, WIDTH, HEIGHT) }
        return renderer
    }

    private fun scene(): String {
        val rocks = DISTANCES.mapIndexed { index, distance ->
            """{ "name": "Rock-$index", "transform": { "position": { "x": ${index % 2 * 3.0 - 1.5}, "y": 0.0, "z": ${-distance} } },
                 "components": [ { "component": "mesh_renderer", "mesh": "$ROCK", "material": "lit-shadow" } ] }"""
        }
        return """
        { "version": 1, "name": "rocks", "nodes": [
          { "name": "Camera", "components": [ { "component": "camera", "eye": { "x": 0.0, "y": 0.0, "z": 0.0 },
            "center": { "x": 0.0, "y": 0.0, "z": -1.0 }, "fovYDegrees": 60.0, "near": 0.1, "far": 500.0 } ] },
          ${rocks.joinToString(",\n")}
        ] }
        """.trimIndent()
    }

    /** Records each mesh's triangle count as it is made, and the last frame's draws. */
    private class CountingRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        private val trianglesByMesh = HashMap<Mesh, Int>()
        private val draws = ArrayList<GpuDrawRequest>()

        override fun createMesh(geometry: MeshGeometry): Mesh = super.createMesh(geometry).also { trianglesByMesh[it] = geometry.indices.size / 3 }

        /** Levels made to share their full mesh's vertices. */
        var sharedLevels = 0
            private set

        override fun createMeshSharingVertices(vertices: Mesh, geometry: MeshGeometry): Mesh {
            sharedLevels++
            return createMesh(geometry)
        }

        override val gpuDrawPreparer = GpuDrawPreparer { request, sourceIndex, _ ->
            if (sourceIndex == 0) draws.clear()
            draws += request.copy()
            null
        }

        fun mainDraws(): List<GpuDrawRequest> = draws.filter { !it.shadowsOnly }.sortedByDescending { it.model.m23 }

        fun triangles(mesh: Any): Int = trianglesByMesh.getValue(mesh as Mesh)
    }

    private companion object {
        val DISTANCES = listOf(4f, 8f, 20f, 40f, 90f, 180f)
        const val FRAMES = 3
        const val ROCK = "models/rock.gltf"
        const val MANIFEST_FILE = "awake.project.json"
        const val SCENE_FILE = "scenes/main.scene.json"
        const val MANIFEST =
            """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""

        /** A closed unit sphere, one vertex per position, as a positions-only glTF. */
        fun sphereGltf(segments: Int = 32, rings: Int = 16): String {
            val positions = mutableListOf(0f, 1f, 0f)
            for (ring in 1 until rings) {
                val theta = PI * ring / rings
                for (segment in 0 until segments) {
                    val phi = 2 * PI * segment / segments
                    positions += listOf((sin(theta) * cos(phi)).toFloat(), cos(theta).toFloat(), (sin(theta) * sin(phi)).toFloat())
                }
            }
            positions += listOf(0f, -1f, 0f)
            val bottom = positions.size / 3 - 1
            fun at(ring: Int, segment: Int) = 1 + (ring - 1) * segments + segment % segments
            val indices = mutableListOf<Int>()
            for (segment in 0 until segments) {
                indices += listOf(0, at(1, segment + 1), at(1, segment))
                indices += listOf(bottom, at(rings - 1, segment), at(rings - 1, segment + 1))
                for (ring in 1 until rings - 1) {
                    indices += listOf(at(ring, segment), at(ring, segment + 1), at(ring + 1, segment))
                    indices += listOf(at(ring, segment + 1), at(ring + 1, segment + 1), at(ring + 1, segment))
                }
            }
            val positionBytes = positions.flatMap { value -> (0 until 4).map { (value.toRawBits() ushr (it * 8)).toByte() } }.toByteArray()
            val indexBytes = indices.flatMap { value -> (0 until 4).map { (value ushr (it * 8)).toByte() } }.toByteArray()
            val buffer = positionBytes + indexBytes
            val count = positions.size / 3
            return """
            { "asset": {"version": "2.0"},
              "buffers": [{"uri": "data:application/octet-stream;base64,${Base64.encode(buffer)}", "byteLength": ${buffer.size}}],
              "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": ${positionBytes.size}},
                              {"buffer": 0, "byteOffset": ${positionBytes.size}, "byteLength": ${indexBytes.size}}],
              "accessors": [{"bufferView": 0, "componentType": 5126, "count": $count, "type": "VEC3", "min": [-1,-1,-1], "max": [1,1,1]},
                            {"bufferView": 1, "componentType": 5125, "count": ${indices.size}, "type": "SCALAR"}],
              "meshes": [{"name": "Rock", "primitives": [{"attributes": {"POSITION": 0}, "indices": 1}]}],
              "nodes": [{"name": "Rock", "mesh": 0}], "scenes": [{"nodes": [0]}], "scene": 0 }
            """.trimIndent()
        }
    }
}
