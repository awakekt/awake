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
import com.awakekt.awake.scene.binding.renderableRequests
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.rendering.mesh.MeshRendererBinding
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneRenderableRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/** A scene that unloads gives back the assets it drew, so the next scene builds its own. */
class SceneAssetReleaseTest {
    init {
        SceneComponentRegistry.registerGlobal(MeshRendererBinding)
    }

    private class CountedMesh : Mesh {
        override val format = VertexFormat.PositionColorUv
        override val sizeBytes = 0L
        var destroyed = false
        override fun destroy() {
            destroyed = true
        }
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
            renderableFactory = { error("unused") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { emptyList() },
        ),
    )

    private val built = mutableListOf<CountedMesh>()
    private val library = SceneAssetLibrary(
        meshFactories = mapOf("crate" to { CountedMesh().also(built::add) }),
        materialFactories = mapOf("lit" to { FakeMaterial }),
        rendererFactories = emptyMap(),
    )

    private fun scene(node: String) = SceneDocument(
        nodes = listOf(SceneNode(name = node, components = listOf(SceneMeshRenderer(mesh = "crate", material = "lit")))),
    )

    @Test
    fun aSceneSwitchFreesTheOldScenesMeshesBeforeTheNextOneBuildsItsOwn() {
        val world = World()
        val manager = SceneManager(world, onUnload = { scene -> scene.renderableRequests.forEach(library::releaseRenderable) })

        manager.switchTo(scene("Harbor")).renderableRequests.forEach { library.resolve(runtime, it) }
        val harborCrate = built.single()
        manager.switchTo(scene("Bay")).renderableRequests.forEach { library.resolve(runtime, it) }

        assertTrue(harborCrate.destroyed, "the first scene's crate is freed when it unloads")
        assertNotSame(harborCrate, built.last(), "the second scene builds its own crate")
        assertEquals(1, library.meshHolderCount("crate"), "only the second scene holds it")
        manager.close()
        assertEquals(0, library.meshHolderCount("crate"))
        assertEquals(0, library.materialHolderCount("lit"))
    }

    @Test
    fun releasingARequestTheLibraryNeverResolvedIsIgnored() {
        val request = SceneRenderableRequest(World().create(), SceneMeshRenderer(mesh = "crate", material = "lit"))
        library.releaseRenderable(request)
        library.resolve(runtime, request)
        library.releaseRenderable(request)
        library.releaseRenderable(request)
        assertEquals(0, library.meshHolderCount("crate"), "one resolve, one release")
    }

    @Test
    fun aMeshThatFailsToBuildHoldsNothing() {
        val request = SceneRenderableRequest(World().create(), SceneMeshRenderer(mesh = "missing", material = "lit"))
        assertFailsWith<IllegalStateException> { library.resolve(runtime, request) }
        assertEquals(0, library.meshHolderCount("missing"))
        assertEquals(0, library.materialHolderCount("lit"), "the material is never taken when the mesh fails")
    }

    @Test
    fun aMaterialThatFailsToBuildGivesBackTheMesh() {
        val request = SceneRenderableRequest(World().create(), SceneMeshRenderer(mesh = "crate", material = "missing"))
        assertFailsWith<IllegalStateException> { library.resolve(runtime, request) }
        assertEquals(0, library.meshHolderCount("crate"))
        assertTrue(built.single().destroyed, "a mesh nothing holds is freed")
    }
}
