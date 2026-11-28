/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The budget bounds *retained* meshes -- ones nothing holds -- because a budget cannot evict what
 * is being drawn. Retaining is also what gives eviction anything to act on: with plain
 * refcounting the last release destroys immediately and there is no cache at all.
 */
class SceneAssetLibraryBudgetTest {

    private class FakeMesh(override val sizeBytes: Long) : Mesh {
        override val format = VertexFormat.PositionColorUv
        var destroyCount = 0
        override fun destroy() {
            destroyCount++
        }
    }

    private val built = mutableMapOf<String, MutableList<FakeMesh>>()

    private fun library(budget: Long) = SceneAssetLibrary(
        meshFactories = listOf("a", "b", "c").associateWith { name ->
            {
                FakeMesh(MESH_BYTES).also { built.getOrPut(name) { mutableListOf() } += it }
            }
        },
        materialFactories = emptyMap(),
        rendererFactories = emptyMap(),
        retainedMeshBudgetBytes = budget,
    )

    private fun buildsOf(name: String) = built[name].orEmpty()

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

    /** The default keeps the destroy-on-last-release behaviour that shipped before the budget. */
    @Test
    fun aZeroBudgetRetainsNothing() {
        val library = library(budget = 0)
        library.requireMesh(runtime, "a")

        library.releaseMesh("a")

        assertFalse(library.isMeshRetained("a"))
        assertEquals(1, buildsOf("a").single().destroyCount)
        assertEquals(0L, library.retainedMeshBytes())
    }

    /** The point of retaining: crossing a cell boundary back should not rebuild. */
    @Test
    fun aRetainedMeshIsRevivedRatherThanRebuilt() {
        val library = library(budget = MESH_BYTES * 4)
        val first = library.requireMesh(runtime, "a")
        library.releaseMesh("a")
        assertTrue(library.isMeshRetained("a"))
        assertEquals(0, buildsOf("a").single().destroyCount, "Retained, not destroyed.")

        val second = library.requireMesh(runtime, "a")

        assertSame(first, second, "Re-acquiring should hand back the retained mesh.")
        assertEquals(1, buildsOf("a").size, "Reviving must not run the factory again.")
        assertEquals(0L, library.retainedMeshBytes(), "A revived mesh is no longer retained.")
    }

    @Test
    fun theLeastRecentlyReleasedIsEvictedFirst() {
        val library = library(budget = MESH_BYTES * 2)
        listOf("a", "b", "c").forEach { library.requireMesh(runtime, it) }

        library.releaseMesh("a")
        library.releaseMesh("b")
        assertEquals(MESH_BYTES * 2, library.retainedMeshBytes(), "Both fit inside the budget.")

        library.releaseMesh("c")

        assertFalse(library.isMeshRetained("a"), "'a' was released first, so it goes first.")
        assertTrue(library.isMeshRetained("b"))
        assertTrue(library.isMeshRetained("c"))
        assertEquals(1, buildsOf("a").single().destroyCount)
        assertEquals(MESH_BYTES * 2, library.retainedMeshBytes())
    }

    /** Reviving must re-date a mesh, or eviction order goes stale. */
    @Test
    fun revivingAMeshMakesItTheMostRecentlyReleased() {
        val library = library(budget = MESH_BYTES * 2)
        listOf("a", "b", "c").forEach { library.requireMesh(runtime, it) }
        library.releaseMesh("a")
        library.releaseMesh("b")

        library.requireMesh(runtime, "a")
        library.releaseMesh("a")
        library.releaseMesh("c")

        assertTrue(library.isMeshRetained("a"), "'a' was released most recently of the three.")
        assertFalse(library.isMeshRetained("b"), "'b' is now the eldest and should be evicted.")
    }

    /** Retaining something larger than the whole budget would defeat having one. */
    @Test
    fun aMeshBiggerThanTheBudgetIsNotRetained() {
        val library = SceneAssetLibrary(
            meshFactories = mapOf("huge" to { FakeMesh(sizeBytes = 10_000) }),
            materialFactories = emptyMap(),
            rendererFactories = emptyMap(),
            retainedMeshBudgetBytes = 500,
        )
        library.requireMesh(runtime, "huge")

        library.releaseMesh("huge")

        assertFalse(library.isMeshRetained("huge"))
        assertEquals(0L, library.retainedMeshBytes())
    }

    /** Teardown has to free the retained set too, or the budget becomes a leak with a ceiling. */
    @Test
    fun disposeDestroysRetainedMeshes() {
        val library = library(budget = MESH_BYTES * 4)
        library.requireMesh(runtime, "a")
        library.releaseMesh("a")

        library.dispose()

        assertEquals(1, buildsOf("a").single().destroyCount)
        assertEquals(0L, library.retainedMeshBytes())
    }

    @Test
    fun sharedMaterialsReviveFromRetainedCacheEvictLruAndFreeOnTrimAndDispose() = runTest {
        val builtMaterials = mutableMapOf<String, MutableList<FakeMaterial>>()
        val reused = mutableListOf<String>()
        var loadRequests = 0
        val resolver = object : SceneAssetResolver {
            override fun canResolveMaterial(name: String): Boolean = name.startsWith("prop:")
            override fun sharedMaterialKey(name: String): String = "digest:${name.substringAfter("prop:")}"
            override fun createMaterial(runtime: SceneAppLifecycleRuntime, name: String): Material =
                FakeMaterial().also { builtMaterials.getOrPut(name) { mutableListOf() } += it }

            override fun loadMaterial(name: String, scope: CoroutineScope): SceneMeshLoad {
                loadRequests++
                return object : SceneMeshLoad {
                    override val failure: Throwable? = null
                    override fun poll(): Boolean = true
                    override fun close() = Unit
                }
            }

            override fun onMaterialReused(name: String) {
                reused += name
            }
        }
        val renderer = object : NoopRenderer() {
            override var textureMipSkip: Int = 1
        }
        val activeRuntime = readyRuntime(renderer)
        val library = SceneAssetLibrary(
            meshFactories = emptyMap(),
            materialFactories = emptyMap(),
            rendererFactories = emptyMap(),
            dynamicResolvers = listOf(resolver),
            retainedMeshBudgetBytes = MESH_BYTES * 4,
        ).apply { maxRetainedMaterials = 2 }

        // Load -> upload -> unload -> reload cycle revives without re-creating or re-reading from disk.
        val firstA = library.requireMaterial(activeRuntime, "prop:a")
        val firstB = library.requireMaterial(activeRuntime, "prop:b")
        val firstC = library.requireMaterial(activeRuntime, "prop:c")
        assertTrue(library.releaseMaterial("prop:a"))
        assertTrue(library.isMaterialRetained("prop:a"))
        assertTrue(library.isMaterialBuilt("prop:a"), "retained shared material reports built so callers skip reloading")
        assertNull(library.loadMaterial("prop:a", this) { renderer.textureMipSkip }, "loadMaterial skips disk read for retained shared material")
        assertEquals(0, loadRequests)
        assertEquals(0, builtMaterials.getValue("prop:a").single().destroyCount)

        val revivedA = library.requireMaterial(activeRuntime, "prop:a")
        assertSame(firstA, revivedA)
        assertEquals(listOf("prop:a"), reused)
        assertEquals(1, builtMaterials.getValue("prop:a").size)
        assertFalse(library.isMaterialRetained("prop:a"))

        // LRU eviction destroys the oldest released material when capacity (2) is exceeded.
        library.releaseMaterial("prop:b")
        library.releaseMaterial("prop:a")
        assertEquals(2, library.retainedMaterialCount())
        library.releaseMaterial("prop:c")
        assertEquals(2, library.retainedMaterialCount())
        assertFalse(library.isMaterialRetained("prop:b"), "'prop:b' was released earliest and must be evicted")
        assertEquals(1, builtMaterials.getValue("prop:b").single().destroyCount)
        assertTrue(library.isMaterialRetained("prop:a"))
        assertTrue(library.isMaterialRetained("prop:c"))

        // Re-acquiring an evicted material creates a fresh GPU material rather than reusing a destroyed one.
        val rebuiltB = library.requireMaterial(activeRuntime, "prop:b")
        assertNotSame(firstB, rebuiltB)
        assertEquals(2, builtMaterials.getValue("prop:b").size)

        // trimMemory() destroys retained idle materials without disturbing live held materials.
        library.trimMemory()
        assertEquals(0, library.retainedMaterialCount())
        assertEquals(1, builtMaterials.getValue("prop:a").single().destroyCount)
        assertEquals(1, builtMaterials.getValue("prop:c").single().destroyCount)
        assertEquals(0, builtMaterials.getValue("prop:b")[1].destroyCount, "live held material survives trimMemory")

        // Dispose releases remaining live + retained materials exactly once.
        library.releaseMaterial("prop:b")
        assertEquals(1, library.retainedMaterialCount())
        library.dispose()
        assertEquals(0, library.retainedMaterialCount())
        assertEquals(1, builtMaterials.getValue("prop:b")[1].destroyCount)
        assertEquals(1, builtMaterials.getValue("prop:a").single().destroyCount)
        assertSame(firstC, builtMaterials.getValue("prop:c").single())
        activeRuntime.dispose()
    }

    private class FakeMaterial : Material {
        var destroyCount = 0
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() {
            destroyCount++
        }
    }

    private suspend fun readyRuntime(renderer: NoopRenderer): SceneAppLifecycleRuntime = SceneAppLifecycleRuntime(
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
    ).also {
        it.initialize(NoServices)
        it.ready(renderer)
    }

    private object NoServices : AppServiceLookup {
        override fun <T : Any> service(type: KClass<T>): T? = null
    }

    private companion object {
        const val MESH_BYTES = 100L
    }
}
