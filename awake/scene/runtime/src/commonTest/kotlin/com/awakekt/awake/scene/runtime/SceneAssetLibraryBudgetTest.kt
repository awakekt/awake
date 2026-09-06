/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.mesh.Mesh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private companion object {
        const val MESH_BYTES = 100L
    }
}
