/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.meshlod.GltfLodCooker
import com.awakekt.awake.asset.meshlod.LodCookOutcome
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.mesh.LodGroup
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A skinned character of two parts, a body and a head, cooked with `MSFT_lod` levels and played
 * through the project runtime: each part gets levels that draw its own vertices, joints and weights,
 * the head follows the body so they switch on the same frame, and the pose keeps animating.
 */
class LodSkinnedPartsTest {
    @Test
    fun aCharactersPartsSwitchTogetherKeepTheirSkinAndAnimate() = runTest {
        val cooked = GltfLodCooker.cook(characterGltf().encodeToByteArray())
        assertEquals(LodCookOutcome.Generated, cooked.outcome)
        assertEquals(2, cooked.meshes.size, "both parts get levels")
        val files = mapOf(MANIFEST_FILE to MANIFEST, SCENE_FILE to SCENE, MODEL to cooked.bytes.decodeToString())
        val project = loadProject(AssetSource { path -> runCatching { files.getValue(path.value).encodeToByteArray() } })
        val renderer = SharingRenderer()
        val game = app { scene("play") { runProject(project) } }
        game.ready(renderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        game.update(FRAME, WIDTH, HEIGHT)

        val parts = buildList { runtime.world.queryEach(MeshRenderer::class, LodGroup::class) { entity, _, _ -> add(entity) } }
        assertEquals(2, parts.size, "each part drawn has levels")
        val groups = parts.map { assertNotNull(runtime.world.get<LodGroup>(it)) }
        val leader = groups.single { it.follow == null }
        val follower = groups.single { it.follow != null }
        assertEquals(parts[groups.indexOf(leader)], follower.follow?.leader, "the head follows the body")
        assertTrue(groups.all { group -> group.levels.all { it.mesh.format.isSkinned } }, "every level is skinned")
        assertEquals(2 * (groups.first().levels.size - 1), renderer.sharedLevels, "every level draws its part's own vertex buffer")

        val pose = assertNotNull(runtime.world.get<SkinnedPose>(parts.first()))
        val before = pose.jointPalette.copyOf()
        val camera = assertNotNull(runtime.cameraLens())
        var switched = 0
        for (step in 0..60) {
            camera.eye = Vec3f(0f, 1f, 3f + step * 0.5f)
            val levelBefore = leader.activeLevel
            game.update(FRAME, WIDTH, HEIGHT)
            assertEquals(leader.activeLevel, follower.activeLevel, "both parts at ${camera.eye.z} away")
            if (leader.activeLevel != levelBefore) switched++
        }
        assertTrue(switched >= 2, "walking away crosses at least two levels: $switched")
        assertFalse(before.contentEquals(pose.jointPalette), "the character still animates at a coarser level")
    }

    private fun SceneAppLifecycleRuntime.cameraLens() = buildList { world.queryEach(Camera::class) { _, camera -> add(camera.lens) } }.firstOrNull()

    /** Counts the meshes made to share another's vertex buffer. */
    private class SharingRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }

        var sharedLevels = 0
            private set

        override fun createMeshSharingVertices(vertices: Mesh, geometry: MeshGeometry): Mesh {
            sharedLevels++
            return createMesh(geometry)
        }
    }

    private companion object {
        const val MODEL = "models/villager.gltf"
        const val MANIFEST_FILE = "awake.project.json"
        const val SCENE_FILE = "scenes/main.scene.json"
        const val MANIFEST =
            """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SCENE = """
{ "version": 1, "name": "villager", "nodes": [
  { "name": "Camera", "components": [ { "component": "camera", "eye": { "x": 0.0, "y": 1.0, "z": 3.0 },
    "center": { "x": 0.0, "y": 1.0, "z": 0.0 }, "fovYDegrees": 60.0, "near": 0.1, "far": 500.0 } ] },
  { "name": "Villager", "children": [
    { "name": "Body", "components": [ { "component": "meshRenderer", "mesh": "gltf-primitive:$MODEL#0", "material": "skinned-material" } ] },
    { "name": "Head", "components": [ { "component": "meshRenderer", "mesh": "gltf-primitive:$MODEL#1", "material": "skinned-material" } ] }
  ] }
] }
"""

        /**
         * Two skinned tubes on one two-joint skin, a body below and a narrower head above, each blended
         * from the lower joint to the upper, with a clip that turns the upper joint.
         */
        fun characterGltf(): String {
            val body = tube(radius = 0.5f, bottom = 0f, top = 1.5f)
            val head = tube(radius = 0.3f, bottom = 1.5f, top = 2f)
            val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
            val inverseBinds = identity + floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, -1f, 0f, 1f)
            val chunks = mutableListOf<ByteArray>()
            fun add(bytes: ByteArray): Int {
                chunks += bytes
                return chunks.lastIndex
            }
            val accessors = mutableListOf<String>()
            fun accessor(view: Int, type: Int, count: Int, kind: String, extra: String = ""): Int {
                accessors += """{"bufferView":$view,"componentType":$type,"count":$count,"type":"$kind"$extra}"""
                return accessors.lastIndex
            }
            fun mesh(part: Tube): String {
                val count = part.positions.size / 3
                val position = accessor(add(floats(part.positions)), FLOAT, count, "VEC3", ""","min":${part.min},"max":${part.max}""")
                val joints = accessor(add(part.joints), UNSIGNED_BYTE, count, "VEC4")
                val weights = accessor(add(floats(part.weights)), FLOAT, count, "VEC4")
                val indices = accessor(add(ints(part.indices)), UNSIGNED_INT, part.indices.size, "SCALAR")
                return """{"primitives":[{"attributes":{"POSITION":$position,"JOINTS_0":$joints,"WEIGHTS_0":$weights},"indices":$indices}]}"""
            }
            val meshes = listOf(mesh(body), mesh(head))
            val bind = accessor(add(floats(inverseBinds)), FLOAT, 2, "MAT4")
            val times = accessor(add(floats(floatArrayOf(0f, 1f))), FLOAT, 2, "SCALAR")
            val turns = accessor(add(floats(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0.7071f, 0.7071f))), FLOAT, 2, "VEC4")
            var offset = 0
            val views = chunks.map { chunk ->
                while (offset % 4 != 0) offset++
                """{"buffer":0,"byteOffset":$offset,"byteLength":${chunk.size}}""".also { offset += chunk.size }
            }
            val buffer = ByteArray(offset)
            var at = 0
            chunks.forEach { chunk ->
                while (at % 4 != 0) at++
                chunk.copyInto(buffer, at)
                at += chunk.size
            }
            return """
            { "asset": {"version": "2.0"},
              "buffers": [{"uri": "data:application/octet-stream;base64,${Base64.encode(buffer)}", "byteLength": ${buffer.size}}],
              "bufferViews": [${views.joinToString(",")}],
              "accessors": [${accessors.joinToString(",")}],
              "meshes": [${meshes.joinToString(",")}],
              "nodes": [{"name": "Root", "children": [1]}, {"name": "Neck", "translation": [0, 1, 0]},
                        {"name": "Body", "mesh": 0, "skin": 0}, {"name": "Head", "mesh": 1, "skin": 0}],
              "skins": [{"inverseBindMatrices": $bind, "joints": [0, 1]}],
              "animations": [{"channels": [{"sampler": 0, "target": {"node": 1, "path": "rotation"}}],
                              "samplers": [{"input": $times, "output": $turns, "interpolation": "LINEAR"}]}],
              "scenes": [{"nodes": [0, 2, 3]}], "scene": 0 }
            """.trimIndent()
        }

        class Tube(val positions: FloatArray, val joints: ByteArray, val weights: FloatArray, val indices: IntArray, val min: List<Float>, val max: List<Float>)

        /** A closed tube along y, 24 around and 16 high, weighted from joint 0 at [bottom] to joint 1 at [top]. */
        fun tube(radius: Float, bottom: Float, top: Float, segments: Int = 24, rings: Int = 16): Tube {
            val positions = ArrayList<Float>()
            val weights = ArrayList<Float>()
            for (ring in 0..rings) {
                val t = ring.toFloat() / rings
                for (segment in 0 until segments) {
                    val phi = 2 * PI * segment / segments
                    positions += listOf((cos(phi) * radius).toFloat(), bottom + (top - bottom) * t, (sin(phi) * radius).toFloat())
                    weights += listOf(1f - t, t, 0f, 0f)
                }
            }
            val indices = ArrayList<Int>()
            for (ring in 0 until rings) {
                for (segment in 0 until segments) {
                    val a = ring * segments + segment
                    val b = ring * segments + (segment + 1) % segments
                    indices += listOf(a, a + segments, b, b, a + segments, b + segments)
                }
            }
            val count = positions.size / 3
            val joints = ByteArray(count * 4) { if (it % 4 == 1) 1 else 0 }
            return Tube(positions.toFloatArray(), joints, weights.toFloatArray(), indices.toIntArray(), listOf(-radius, bottom, -radius), listOf(radius, top, radius))
        }

        fun floats(values: FloatArray) = ByteArray(values.size * 4).also { bytes ->
            values.forEachIndexed { i, value -> for (b in 0 until 4) bytes[i * 4 + b] = (value.toRawBits() ushr (b * 8)).toByte() }
        }

        fun ints(values: List<Int>) = ints(values.toIntArray())

        fun ints(values: IntArray) = ByteArray(values.size * 4).also { bytes ->
            values.forEachIndexed { i, value -> for (b in 0 until 4) bytes[i * 4 + b] = (value ushr (b * 8)).toByte() }
        }

        const val FLOAT = 5126
        const val UNSIGNED_BYTE = 5121
        const val UNSIGNED_INT = 5125
    }
}
