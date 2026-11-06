/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.error.AppCoroutineScope
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.asGpuImageSource
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.scene2d.SceneSprite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A prewarm gets a tree's assets ready before it spawns, holds them until closed, and stops cleanly. */
class ScenePrewarmTest {
    private class FakeMesh(override val sizeBytes: Long) : Mesh {
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

    private val scheduler = TestCoroutineScheduler()
    private val built = mutableMapOf<String, MutableList<FakeMesh>>()
    private var reads = 0
    private val decoded = CompletableDeferred<Unit>()
    private val textures = AssetSourceTextureResolver(AssetSource { error("unused") }, StandardTestDispatcher(scheduler)) {
        reads++
        decoded.await()
        TextureAsset(ByteArray(4), 1, 1).asGpuImageSource()
    }
    private val library = SceneAssetLibrary(
        meshFactories = MESHES.associateWith { name -> { FakeMesh(MESH_BYTES).also { built.getOrPut(name) { mutableListOf() } += it } } },
        materialFactories = mapOf("lit" to { FakeMaterial }),
        rendererFactories = emptyMap(),
        dynamicResolvers = listOf(textures),
    )
    private val runtime = SceneAppLifecycleRuntime(
        SceneAppSpec(
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
        ),
    ).apply { initialize(NoServices) }

    @Test
    fun aPrewarmedTreeSpawnsDrawnOnItsFirstFrameWithoutBuildingAgain() = runTest {
        val progress = mutableListOf<Int>()
        // --8<-- [start:prewarm]
        val prewarm = runtime.prewarm(BOAT) { settled, _ -> progress += settled }
        // --8<-- [end:prewarm]
        assertFalse(prewarm.isReady, "nothing is built before a frame works on it")

        library.runPrewarms()
        finishDecoding()
        library.runPrewarms()

        assertTrue(prewarm.isReady)
        // --8<-- [start:spawn]
        prewarm.await()
        val spawned = runtime.spawn(BOAT)
        prewarm.close()
        // --8<-- [end:spawn]
        assertEquals(listOf(1, 2, 3), progress)
        spawned.entities.forEach { assertNotNull(runtime.world.get<MeshRenderer>(it), "drawn on its first frame") }
        assertEquals(emptyList(), spawned.unattachedRenderables)
        assertSame(built.getValue("hull").single(), runtime.world.get<MeshRenderer>(spawned.root)?.mesh, "shared, not rebuilt")
        assertNotNull(library.textureImage("sprites/flag.png"), "the texture is loaded before it is first drawn")
    }

    /** A draw that asks for a texture while it is prewarming shares the one load. */
    @Test
    fun aTextureAskedForWhilePrewarmingLoadsOnce() = runTest {
        val prewarm = runtime.prewarm(SceneAssetReferences(textures = setOf("sprites/flag.png")))
        library.runPrewarms()

        assertEquals(null, library.textureImage("sprites/flag.png"), "a draw before it loads waits")
        finishDecoding()
        library.runPrewarms()

        assertTrue(prewarm.isReady)
        assertEquals(1, reads)
    }

    /** What a prewarm holds outlives another holder's release, until the prewarm is closed. */
    @Test
    fun aHeldMeshIsDestroyedOnlyOnceThePrewarmCloses() {
        val prewarm = runtime.prewarm(SceneAssetReferences(renderables = setOf(SceneRenderableKey("hull", "lit"))))
        library.runPrewarms()
        val spawned = runtime.spawn(SceneNode(components = listOf(SceneMeshRenderer(mesh = "hull", material = "lit"))))

        spawned.despawn()
        assertFalse(built.getValue("hull").single().destroyed, "the prewarm still holds it")
        prewarm.close()
        prewarm.close()

        assertTrue(built.getValue("hull").single().destroyed)
        assertEquals(0, library.meshHolderCount("hull"))
    }

    @Test
    fun aFrameUploadsNewMeshUpToItsBudget() {
        library.prewarmFrameBudgetBytes = MESH_BYTES + 1
        val prewarm = runtime.prewarm(SceneAssetReferences(renderables = MESHES.map { SceneRenderableKey(it, "lit") }.toSet()))

        library.runPrewarms()
        assertEquals(setOf("hull", "mast"), built.keys, "a frame stops once its budget is spent")
        library.runPrewarms()

        assertEquals(MESHES.toSet(), built.keys)
        assertTrue(prewarm.isReady)
    }

    /** One asset that can't be built fails alone: the rest are ready, and the prewarm says which. */
    @Test
    fun anAssetThatCannotBeBuiltFailsAlone() {
        val prewarm = runtime.prewarm(SceneAssetReferences(renderables = setOf(SceneRenderableKey("anchor", "lit"), SceneRenderableKey("hull", "lit"))))

        library.runPrewarms()

        assertTrue(prewarm.isReady)
        assertEquals(setOf("anchor + lit"), prewarm.failed.keys)
        assertEquals(1, library.meshHolderCount("hull"))
    }

    @Test
    fun closingBeforeItIsReadyStopsItAndReleasesWhatItBuilt() = runTest {
        val prewarm = runtime.prewarm(BOAT)
        library.runPrewarms()

        prewarm.close()

        assertFailsWith<CancellationException> { prewarm.await() }
        assertEquals(0, library.meshHolderCount("hull"))
        assertTrue(built.getValue("hull").single().destroyed)
    }

    @Test
    fun teardownStopsEveryPrewarmAndAClosedOneReleasesNothing() = runTest {
        val waiting = runtime.prewarm(BOAT)
        library.runPrewarms()

        library.dispose()

        assertFailsWith<CancellationException> { waiting.await() }
        assertTrue(built.getValue("hull").single().destroyed, "teardown destroys what it held")
        waiting.close()
    }

    /** A loading screen that closes while it waits leaks nothing: the next frame closes the prewarm. */
    @Test
    fun aCallerCancelledWhileWaitingGivesThePrewarmUp() = runTest {
        val prewarm = runtime.prewarm(BOAT)
        library.runPrewarms()
        val waiting = launch { prewarm.await() }
        testScheduler.runCurrent()

        waiting.cancelAndJoin()
        library.runPrewarms()

        assertFalse(prewarm.isReady)
        assertEquals(0, library.meshHolderCount("hull"))
        assertTrue(built.getValue("hull").single().destroyed)
    }

    /** The session's asset work is a child of the app's scope, so closing the app stops a prewarm. */
    @Test
    fun closingTheAppScopeStopsAPrewarm() = runTest {
        val app = AppCoroutineScope(NoServices)
        val appRuntime = SceneAppLifecycleRuntime(runtime.spec).apply { initialize(Services(app)) }
        val prewarm = appRuntime.prewarm(BOAT)

        app.close()

        assertFailsWith<CancellationException> { prewarm.await() }
    }

    private class Services(private val app: AppCoroutineScope) : AppServiceLookup {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> service(type: KClass<T>): T? = if (type == AppCoroutineScope::class) app as T else null
    }

    private fun finishDecoding() {
        scheduler.runCurrent()
        decoded.complete(Unit)
        scheduler.runCurrent()
    }

    private object NoServices : AppServiceLookup {
        override fun <T : Any> service(type: KClass<T>): T? = null
    }

    private companion object {
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
