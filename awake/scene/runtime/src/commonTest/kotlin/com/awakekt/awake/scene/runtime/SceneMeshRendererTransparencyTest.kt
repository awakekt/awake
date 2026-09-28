/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
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

/** A scene file can ask for a blended mesh (water, glass), and the live renderer gets it. */
class SceneMeshRendererTransparencyTest {

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
        meshFactories = mapOf("surface" to { FakeMesh }),
        materialFactories = mapOf("tinted" to { FakeMaterial }),
        rendererFactories = emptyMap(),
    )

    @Test
    fun aTransparentSceneRendererSurvivesTheFileAndReachesTheLiveRenderer() {
        val authored = SceneMeshRenderer(mesh = "surface", material = "tinted", transparent = true)
        val document = SceneDocument(nodes = listOf(SceneNode(name = "water", components = listOf(authored))))

        val decoded = SceneLoader.decode(SceneLoader.encode(document)).nodes.single().components.single()
        assertEquals(authored, decoded)

        val entity = World().create()
        assertTrue(library.resolve(runtime, SceneRenderableRequest(entity, decoded as SceneMeshRenderer)).transparent)
    }

    @Test
    fun aSceneRendererWithoutTheFieldStaysOpaque() {
        val decoded = SceneLoader.decode(
            """{"nodes":[{"name":"rock","components":[{"component":"meshRenderer","mesh":"surface","material":"tinted"}]}]}""",
        ).nodes.single().components.single() as SceneMeshRenderer

        assertFalse(decoded.transparent)
        assertFalse(library.resolve(runtime, SceneRenderableRequest(World().create(), decoded)).transparent)
    }
}
