/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.rendering.mesh.MeshRendererBinding
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneRenderableRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A scene file gives a mesh its own draw distance, the live renderer gets it, and an older file draws at any distance. */
class SceneMeshRendererDrawDistanceTest {

    init {
        SceneComponentRegistry.registerGlobal(MeshRendererBinding)
    }

    private object FakeMesh : Mesh {
        override val format = VertexFormat.PositionColorUv
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private object FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private val runtime = SceneAppLifecycleRuntime(
        SceneAppSpec(
            sceneName = null,
            systems = emptyList(),
            scenePopulationBlock = {},
            renderableFactory = { error("no renderable requested in this test") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { emptyList() },
        ),
    )

    private val library = SceneAssetLibrary(
        meshFactories = mapOf("pebble" to { FakeMesh }),
        materialFactories = mapOf("stone" to { FakeMaterial }),
        rendererFactories = emptyMap(),
    )

    @Test
    fun aDrawDistanceSurvivesTheFileAndReachesTheLiveRenderer() {
        val authored = SceneMeshRenderer(mesh = "pebble", material = "stone", maxDrawDistance = 40f)
        val document = SceneDocument(nodes = listOf(SceneNode(name = "clutter", components = listOf(authored))))

        val encoded = SceneLoader.encode(document)
        assertTrue("\"maxDrawDistance\":40.0" in encoded.filterNot(Char::isWhitespace), "The field is written: $encoded")
        val decoded = SceneLoader.decode(encoded).nodes.single().components.single() as SceneMeshRenderer
        assertEquals(authored, decoded)

        assertEquals(40f, library.resolve(runtime, SceneRenderableRequest(World().create(), decoded)).maxDrawDistance)
    }

    @Test
    fun aRendererWithoutTheFieldDrawsAtAnyDistance() {
        val decoded = SceneLoader.decode(
            """{"nodes":[{"name":"rock","components":[{"component":"mesh_renderer","mesh":"pebble","material":"stone"}]}]}""",
        ).nodes.single().components.single() as SceneMeshRenderer

        assertEquals(0f, decoded.maxDrawDistance)
        assertEquals(0f, library.resolve(runtime, SceneRenderableRequest(World().create(), decoded)).maxDrawDistance)
    }

    @Test
    fun aNegativeOrEndlessDistanceIsRejected() {
        listOf(-1f, Float.POSITIVE_INFINITY, Float.NaN).forEach { distance ->
            val issues = SceneMeshRenderer(mesh = "pebble", material = "stone", maxDrawDistance = distance).validate("n")
            assertTrue(issues.any { "maxDrawDistance" in it.message }, "$distance: $issues")
        }
        assertTrue(SceneMeshRenderer(mesh = "pebble", material = "stone", maxDrawDistance = 0f).validate("n").isEmpty())
    }
}
