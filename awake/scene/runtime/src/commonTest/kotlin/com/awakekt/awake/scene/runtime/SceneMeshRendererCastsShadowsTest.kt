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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A scene file can turn a mesh's shadow off (a decal, a ground card), the live renderer gets it,
 * and a file written before the field existed still casts.
 */
class SceneMeshRendererCastsShadowsTest {

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
        meshFactories = mapOf("card" to { FakeMesh }),
        materialFactories = mapOf("decal" to { FakeMaterial }),
        rendererFactories = emptyMap(),
    )

    @Test
    fun aMeshThatDoesNotCastSurvivesTheFileAndReachesTheLiveRenderer() {
        val authored = SceneMeshRenderer(mesh = "card", material = "decal", castsShadows = false)
        val document = SceneDocument(nodes = listOf(SceneNode(name = "groundCard", components = listOf(authored))))

        val encoded = SceneLoader.encode(document)
        assertTrue("\"castsShadows\":false" in encoded.filterNot(Char::isWhitespace), "The field is written: $encoded")
        val decoded = SceneLoader.decode(encoded).nodes.single().components.single()
        assertEquals(authored, decoded)

        val entity = World().create()
        assertFalse(library.resolve(runtime, SceneRenderableRequest(entity, decoded as SceneMeshRenderer)).castsShadows)
    }

    @Test
    fun aSceneRendererWithoutTheFieldStillCasts() {
        val decoded = SceneLoader.decode(
            """{"nodes":[{"name":"rock","components":[{"component":"mesh_renderer","mesh":"card","material":"decal"}]}]}""",
        ).nodes.single().components.single() as SceneMeshRenderer

        assertTrue(decoded.castsShadows)
        assertTrue(library.resolve(runtime, SceneRenderableRequest(World().create(), decoded)).castsShadows)
    }
}
