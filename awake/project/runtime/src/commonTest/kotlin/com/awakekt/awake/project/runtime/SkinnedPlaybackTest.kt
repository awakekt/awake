/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class SkinnedPlaybackTest {
    @Test
    fun aSkinnedModelPlaysItsFirstClip() = runTest {
        val files = mapOf(
            "awake.project.json" to MANIFEST,
            "scenes/main.scene.json" to SCENE,
            MODEL to skinnedTriangleGltf(),
        )
        val project = loadPlayableProject(AssetSource { path -> runCatching { files.getValue(path.value).encodeToByteArray() } })
        val game = app { scene("play") { playProject(project) } }
        game.ready(TestRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        game.update(DELTA, WIDTH, HEIGHT)

        val poses = mutableListOf<SkinnedPose>()
        runtime.world.queryEach(Animator::class, SkinnedPose::class) { _, _, pose -> poses += pose }
        assertEquals(1, poses.size, "the model must get exactly one animator")
        val before = poses.single().jointPalette.copyOf()
        repeat(FRAMES) { game.update(DELTA, WIDTH, HEIGHT) }

        assertFalse(before.contentEquals(poses.single().jointPalette), "the joints must move as the clip plays")
    }

    /** Two parts of one model are one character: one animator, and both parts draw its pose. */
    @Test
    fun aModelsPartsShareOneAnimator() = runTest {
        val files = mapOf(
            "awake.project.json" to MANIFEST,
            "scenes/main.scene.json" to PARTS_SCENE,
            MODEL to skinnedTriangleGltf(parts = 2),
        )
        val project = loadPlayableProject(AssetSource { path -> runCatching { files.getValue(path.value).encodeToByteArray() } })
        val game = app { scene("play") { playProject(project) } }
        game.ready(TestRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        game.update(DELTA, WIDTH, HEIGHT)

        val animated = mutableListOf<SkinnedPose>()
        runtime.world.queryEach(Animator::class, SkinnedPose::class) { _, _, pose -> animated += pose }
        val drawn = mutableListOf<SkinnedPose>()
        runtime.world.queryEach(MeshRenderer::class, SkinnedPose::class) { _, _, pose -> drawn += pose }
        assertEquals(1, animated.size, "the parts must share one animator")
        assertEquals(2, drawn.size)
        drawn.forEach { assertSame(animated.single(), it, "each part must draw the shared pose") }
    }

    private class TestRenderer : NoopRenderer(), GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    /**
     * One triangle skinned to two joints. The second joint turns 90 degrees about Z over one second,
     * carrying the triangle's top vertex with it.
     */
    private fun skinnedTriangleGltf(parts: Int = 1): String {
        val positions = floats(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val joints = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0)
        val weights = floats(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
        val indices = byteArrayOf(0, 0, 1, 0, 2, 0, 0, 0)
        val identity = floats(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val inverseBind = identity + floats(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, -1f, 0f, 1f)
        val times = floats(0f, 1f)
        val rotations = floats(0f, 0f, 0f, 1f, 0f, 0f, 0.7071f, 0.7071f)
        val chunks = listOf(positions, joints, weights, indices, inverseBind, times, rotations)
        val buffer = chunks.reduce(ByteArray::plus)
        val offsets = chunks.runningFold(0) { offset, chunk -> offset + chunk.size }
        fun view(index: Int) = """{"buffer":0,"byteOffset":${offsets[index]},"byteLength":${chunks[index].size}}"""
        return """
        {
          "asset": {"version": "2.0"},
          "buffers": [{"uri": "data:application/octet-stream;base64,${Base64.encode(buffer)}", "byteLength": ${buffer.size}}],
          "bufferViews": [${chunks.indices.joinToString(",") { view(it) }}],
          "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
            {"bufferView": 1, "componentType": 5121, "count": 3, "type": "VEC4"},
            {"bufferView": 2, "componentType": 5126, "count": 3, "type": "VEC4"},
            {"bufferView": 3, "componentType": 5123, "count": 3, "type": "SCALAR"},
            {"bufferView": 4, "componentType": 5126, "count": 2, "type": "MAT4"},
            {"bufferView": 5, "componentType": 5126, "count": 2, "type": "SCALAR"},
            {"bufferView": 6, "componentType": 5126, "count": 2, "type": "VEC4"}
          ],
          "meshes": [{"primitives": [{"attributes": {"POSITION": 0, "JOINTS_0": 1, "WEIGHTS_0": 2}, "indices": 3}]}],
          "nodes": [
            {"name": "Root", "children": [1]},
            {"name": "Tip", "translation": [0, 1, 0]},
            ${(1..parts).joinToString(",") { """{"name": "Body $it", "mesh": 0, "skin": 0}""" }}
          ],
          "skins": [{"inverseBindMatrices": 4, "joints": [0, 1]}],
          "animations": [{"channels": [{"sampler": 0, "target": {"node": 1, "path": "rotation"}}],
                          "samplers": [{"input": 5, "output": 6, "interpolation": "LINEAR"}]}],
          "scenes": [{"nodes": [0, ${(2 until 2 + parts).joinToString(",")}]}],
          "scene": 0
        }
        """.trimIndent()
    }

    private fun floats(vararg values: Float): ByteArray {
        val bytes = ByteArray(values.size * 4)
        values.forEachIndexed { index, value ->
            val bits = value.toRawBits()
            for (byte in 0 until 4) bytes[index * 4 + byte] = (bits ushr (byte * 8)).toByte()
        }
        return bytes
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val FRAMES = 20
        const val MODEL = "models/arm.gltf"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SCENE = """
{ "version": 1, "name": "arm", "nodes": [
  { "name": "Arm", "components": [ { "component": "meshRenderer", "mesh": "$MODEL", "material": "skinned-material" } ] }
] }
"""
        const val PARTS_SCENE = """
{ "version": 1, "name": "arm", "nodes": [
  { "name": "Arm", "children": [
    { "name": "Upper", "components": [ { "component": "meshRenderer", "mesh": "gltf-primitive:$MODEL#0", "material": "skinned-material" } ] },
    { "name": "Lower", "components": [ { "component": "meshRenderer", "mesh": "gltf-primitive:$MODEL#1", "material": "skinned-material" } ] }
  ] }
] }
"""
    }
}
