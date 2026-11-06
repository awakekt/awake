/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.asGpuImageSource
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.scene2d.SceneSprite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.reflect.KClass

/**
 * A scene with three meshes ([MESHES]), one material (`lit`) and textures decoded on a test scheduler
 * until [finishDecoding]: what the prewarm tests share. [BOAT] draws two of the meshes and a sprite.
 */
abstract class PrewarmFixture {
    protected class FakeMesh(override val sizeBytes: Long) : Mesh {
        override val format = VertexFormat.PositionNormalColor
        var destroyed = false
        override fun destroy() {
            destroyed = true
        }
    }

    private object FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    protected object NoServices : AppServiceLookup {
        override fun <T : Any> service(type: KClass<T>): T? = null
    }

    private val scheduler = TestCoroutineScheduler()
    protected val built = mutableMapOf<String, MutableList<FakeMesh>>()
    protected var reads = 0
    private val decoded = CompletableDeferred<Unit>()
    private val textures = AssetSourceTextureResolver(AssetSource { error("unused") }, StandardTestDispatcher(scheduler)) {
        reads++
        decoded.await()
        TextureAsset(ByteArray(4), 1, 1).asGpuImageSource()
    }
    protected val library = SceneAssetLibrary(
        meshFactories = MESHES.associateWith { name -> { FakeMesh(MESH_BYTES).also { built.getOrPut(name) { mutableListOf() } += it } } },
        materialFactories = mapOf("lit" to { FakeMaterial }),
        rendererFactories = emptyMap(),
        dynamicResolvers = listOf(textures),
    )
    protected val runtime = SceneAppLifecycleRuntime(spec()).apply { initialize(NoServices) }

    /** A spec whose renderables resolve through [library], as the scene runtime's do. */
    protected fun spec() = SceneAppSpec(
        sceneName = null,
        systems = emptyList(),
        scenePopulationBlock = {},
        renderableFactory = { request -> library.resolve(this, request) },
        assetLibraryFactory = { library },
        updateBlock = { _, _ -> },
        ui = null,
        onReadyBlock = {},
        onDisposeBlock = {},
        serviceRegistrations = emptyList(),
        infrastructureSystemsFactory = { emptyList() },
    )

    /** Lets every texture read finish decoding. */
    protected fun finishDecoding() {
        scheduler.runCurrent()
        decoded.complete(Unit)
        scheduler.runCurrent()
    }

    protected companion object {
        const val MESH_BYTES = 100L
        val MESHES = listOf("hull", "mast", "sail")
        val BOAT = SceneNode(
            name = "Boat",
            components = listOf(SceneMeshRenderer(mesh = "hull", material = "lit")),
            children = listOf(
                SceneNode(name = "Mast", components = listOf(SceneMeshRenderer(mesh = "mast", material = "lit"))),
                SceneNode(name = "Flag", components = listOf(SceneSprite(texture = "sprites/flag.png"))),
            ),
        )
    }
}
