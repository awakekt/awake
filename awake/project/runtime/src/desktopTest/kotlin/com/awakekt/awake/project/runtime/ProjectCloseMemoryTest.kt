/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.ioDispatcher
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.physics.SceneMeshShape
import com.awakekt.awake.scene.physics.ScenePhysicsBody
import kotlinx.coroutines.runBlocking
import java.lang.ref.WeakReference
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProjectCloseMemoryTest {
    @Test
    fun closingLoadedProjectDropsSceneDocumentModelsAndContent() = runBlocking {
        val markerKey = SceneContentKey<ByteArray>("test.marker")
        val feature = object : SceneFeature {
            override val id: String = "com.awakekt.test.retention"
            override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
                content[markerKey] = ByteArray(1024 * 64) { 7 }
            }
            override fun plan(scene: SceneDocument, plan: SceneSystemPlan) = Unit
        }
        val project = loadProject(minimalProjectFiles(), listOf(feature), hasRenderer = false)
        assertEquals(ioDispatcher, project.sceneLoads)

        val (weakDoc, weakModels, weakContent) = captureWeakRefs(project, markerKey)
        project.close()

        assertTrue(project.scene.nodes.isEmpty(), "closed project should drop its loaded scene document")
        assertNull(project.content[markerKey], "closed project should drop its loaded scene content")

        var collected = false
        for (attempt in 0 until GC_ATTEMPTS) {
            if (weakDoc.get() == null && weakModels.get() == null && weakContent.get() == null) {
                collected = true
                break
            }
            @Suppress("ExplicitGarbageCollectionCall") // Required to verify WeakReference collection after close().
            System.gc()
            Thread.sleep(GC_WAIT_MS)
        }
        assertTrue(
            collected,
            "LoadedProject.close() must release scene document (${weakDoc.get() != null}), " +
                "models (${weakModels.get() != null}), and feature content (${weakContent.get() != null})",
        )
    }

    @Test
    fun loadCollisionMeshesReadsEachReferencedModelOnceWhenInterleaved() = runBlocking {
        val reads = HashMap<String, Int>()
        val gltfBytes = minimalTriangleGltf().encodeToByteArray()
        val files = AssetSource { path: AssetPath ->
            val clean = path.value.removePrefix("/")
            reads[clean] = (reads[clean] ?: 0) + 1
            if (clean == "models/a.gltf" || clean == "models/b.gltf") {
                Result.success(gltfBytes)
            } else {
                Result.failure(NoSuchElementException(clean))
            }
        }
        fun meshNode(name: String, path: String) = SceneNode(
            name = name,
            components = listOf<SceneComponent>(ScenePhysicsBody(SceneMeshShape(path))),
        )
        val scene = SceneDocument(
            name = "interleaved",
            nodes = listOf(
                meshNode("A1", "models/a.gltf"),
                meshNode("B1", "models/b.gltf"),
                meshNode("A2", "models/a.gltf"),
                meshNode("B2", "models/b.gltf"),
            ),
        )

        val source = loadCollisionMeshes(scene, files)

        assertEquals(1, reads["models/a.gltf"], "models/a.gltf must be read once")
        assertEquals(1, reads["models/b.gltf"], "models/b.gltf must be read once")
        val shapeA = assertNotNull(source.meshShape("models/a.gltf", null))
        assertSame(shapeA, source.meshShape("models/a.gltf", null))
        assertEquals(1, shapeA.triangleCount)
        assertEquals(1, assertNotNull(source.meshShape("models/b.gltf", null)).triangleCount)
    }

    private fun captureWeakRefs(
        project: LoadedProject,
        markerKey: SceneContentKey<ByteArray>,
    ): Triple<WeakReference<SceneDocument>, WeakReference<Any>, WeakReference<ByteArray>> = Triple(
        WeakReference(project.scene),
        WeakReference(project.models),
        WeakReference(checkNotNull(project.content[markerKey])),
    )

    private fun minimalProjectFiles(): AssetSource {
        val manifest =
            """{"formatVersion":1,"id":"com.awakekt.test.close","name":"close-test","version":"0.1.0","entryScene":"scenes/main.scene.json"}"""
                .encodeToByteArray()
        val scene = """{"version":1,"name":"main","nodes":[{"name":"HeavyNode"}]}""".encodeToByteArray()
        return AssetSource { path ->
            when (path.value.removePrefix("/")) {
                "awake.project.json" -> Result.success(manifest)
                "scenes/main.scene.json" -> Result.success(scene)
                else -> Result.failure(NoSuchElementException(path.value))
            }
        }
    }

    private fun minimalTriangleGltf(): String {
        val buf = java.nio.ByteBuffer.allocate(36 + 6).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f).forEach { buf.putFloat(it) }
        shortArrayOf(0, 1, 2).forEach { buf.putShort(it) }
        val b64 = Base64.getEncoder().encodeToString(buf.array())
        return """
        {
          "asset": { "version": "2.0" },
          "scene": 0,
          "scenes": [{ "nodes": [0] }],
          "nodes": [{ "mesh": 0 }],
          "meshes": [{ "primitives": [{ "attributes": { "POSITION": 0 }, "indices": 1 }] }],
          "accessors": [
            { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0, 0, 0], "max": [1, 1, 0] },
            { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
          ],
          "bufferViews": [
            { "buffer": 0, "byteOffset": 0, "byteLength": 36 },
            { "buffer": 0, "byteOffset": 36, "byteLength": 6 }
          ],
          "buffers": [{ "uri": "data:application/octet-stream;base64,$b64", "byteLength": 42 }]
        }
        """.trimIndent()
    }

    private companion object {
        const val GC_ATTEMPTS = 30
        const val GC_WAIT_MS = 25L
    }
}
