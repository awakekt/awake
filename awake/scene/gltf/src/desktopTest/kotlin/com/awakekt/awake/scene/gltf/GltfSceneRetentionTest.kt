/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.core.host.DeviceProfile
import kotlinx.coroutines.test.runTest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifies refcounted + LRU eviction and `trimMemory()` on [GltfAssetResolver.loadedScenes] so
 * skinned skeletons and animations from visited zones stay bounded on 4 GB RAM devices.
 */
@OptIn(ExperimentalEncodingApi::class)
class GltfSceneRetentionTest {

    @Test
    fun evictsLeastRecentlyUsedUnheldSkinnedScenesAndRespectsAcquireAndKeep() = runTest {
        val bytes = skinnedTexturedTriangle().encodeToByteArray()
        val resolver = GltfAssetResolver().apply { maxRetainedScenes = 2 }

        resolver.preload("a.gltf", bytes)
        resolver.preload("b.gltf", bytes)
        assertNotNull(resolver.getLoadedScene("a.gltf"))
        assertNotNull(resolver.getLoadedScene("b.gltf"))

        // Hold 'a' and pin 'b' via keepAfterUpload; loading 'c' cannot evict either held/kept scene.
        resolver.acquireModel("a.gltf")
        resolver.keepAfterUpload("b.gltf")
        assertEquals(1, resolver.modelHolderCount("a.gltf"))
        resolver.preload("c.gltf", bytes)
        assertNotNull(resolver.getLoadedScene("a.gltf"), "Held model must not be evicted")
        assertNotNull(resolver.getLoadedScene("b.gltf"), "keepAfterUpload model must not be evicted")
        assertNotNull(resolver.getLoadedScene("c.gltf"))

        // Releasing 'a' allows excess eviction to drop the oldest unheld/unkept scene ('a').
        resolver.releaseModel("a.gltf")
        assertEquals(0, resolver.modelHolderCount("a.gltf"))
        assertNull(resolver.getLoadedScene("a.gltf"), "Oldest unheld model must evict when released over cap")
        assertNotNull(resolver.getLoadedScene("b.gltf"))
        assertNotNull(resolver.getLoadedScene("c.gltf"))

        // trimMemory() drops remaining unheld/unkept scenes ('c') while preserving keepAfterUpload ('b').
        resolver.trimMemory()
        assertNotNull(resolver.getLoadedScene("b.gltf"), "keepAfterUpload model survives trimMemory()")
        assertNull(resolver.getLoadedScene("c.gltf"), "Unheld model is dropped on trimMemory()")

        assertEquals(
            GltfAssetResolver.LOW_RAM_MAX_RETAINED_SCENES,
            GltfAssetResolver.defaultMaxRetainedScenes(DeviceProfile(totalMemoryBytes = 4L * 1024 * 1024 * 1024)),
        )
        assertEquals(
            GltfAssetResolver.DEFAULT_MAX_RETAINED_SCENES,
            GltfAssetResolver.defaultMaxRetainedScenes(DeviceProfile(totalMemoryBytes = 16L * 1024 * 1024 * 1024)),
        )
    }

    private fun skinnedTexturedTriangle(): String {
        fun floats(vararg values: Float): ByteArray = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            .apply { values.forEach(::putFloat) }.array()
        val positions = floats(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val indices = byteArrayOf(0, 0, 1, 0, 2, 0, 0, 0)
        val joints = ByteArray(12)
        val weights = floats(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
        val uvs = floats(0f, 0f, 1f, 0f, 0f, 1f)
        val inverseBind = floats(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val buffer = positions + indices + joints + weights + uvs + inverseBind
        var offset = 0
        val views = listOf(positions, indices, joints, weights, uvs, inverseBind).joinToString(",") { part ->
            """{"buffer":0,"byteOffset":$offset,"byteLength":${if (part === indices) 6 else part.size}}""".also { offset += part.size }
        }
        return """
            {
              "buffers": [{"uri":"data:application/octet-stream;base64,${Base64.encode(buffer)}","byteLength":${buffer.size}}],
              "bufferViews": [$views],
              "accessors": [
                {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"},
                {"bufferView":2,"componentType":5121,"count":3,"type":"VEC4"},
                {"bufferView":3,"componentType":5126,"count":3,"type":"VEC4"},
                {"bufferView":4,"componentType":5126,"count":3,"type":"VEC2"},
                {"bufferView":5,"componentType":5126,"count":1,"type":"MAT4"}
              ],
              "materials": [{"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}}],
              "textures": [{"source":0}],
              "images": [{"uri":"data:image/png;base64,$PIXEL_PNG"}],
              "meshes": [{"primitives":[{"attributes":{"POSITION":0,"JOINTS_0":2,"WEIGHTS_0":3,"TEXCOORD_0":4},"indices":1,"material":0}]}],
              "nodes": [{"name":"Root"}, {"mesh":0,"skin":0}],
              "skins": [{"inverseBindMatrices":5,"joints":[0]}],
              "scenes": [{"nodes":[0,1]}],
              "scene": 0
            }
        """.trimIndent()
    }

    private companion object {
        const val PIXEL_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }
}
