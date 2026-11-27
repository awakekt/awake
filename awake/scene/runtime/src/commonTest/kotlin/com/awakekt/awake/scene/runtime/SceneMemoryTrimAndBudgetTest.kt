/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.host.DeviceProfile
import com.awakekt.awake.render.mesh.Mesh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies [SceneAssetLibrary.trimRetainedMeshes], [SceneAssetLibrary.trimMemory], and
 * [DeviceProfile]-scaled retained mesh budgets.
 */
class SceneMemoryTrimAndBudgetTest {

    private class FakeMesh(override val sizeBytes: Long) : Mesh {
        override val format = VertexFormat.PositionColorUv
        var destroyCount = 0
        override fun destroy() {
            destroyCount++
        }
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

    @Test
    fun trimRetainedMeshesEvictsEldestIdleMeshesAndTrimMemoryNotifiesResolvers() {
        val meshes = mutableMapOf<String, FakeMesh>()
        var resolverTrims = 0
        val resolver = object : SceneAssetResolver {
            override fun trimMemory() {
                resolverTrims += 1
            }
        }
        val library = SceneAssetLibrary(
            meshFactories = listOf("a", "b", "c", "live").associateWith { name ->
                { FakeMesh(MESH_BYTES).also { meshes[name] = it } }
            },
            materialFactories = emptyMap(),
            rendererFactories = emptyMap(),
            dynamicResolvers = listOf(resolver),
            retainedMeshBudgetBytes = MESH_BYTES * 4,
        )

        library.requireMesh(runtime, "a")
        library.requireMesh(runtime, "b")
        library.requireMesh(runtime, "c")
        library.requireMesh(runtime, "live")
        library.releaseMesh("a")
        library.releaseMesh("b")
        library.releaseMesh("c")
        assertEquals(MESH_BYTES * 3, library.retainedMeshBytes())

        // Trim down to 1 mesh budget: destroys oldest two idle meshes (a, b), keeps c and live.
        val freedPartial = library.trimRetainedMeshes(targetBudgetBytes = MESH_BYTES)
        assertEquals(MESH_BYTES * 2, freedPartial)
        assertEquals(MESH_BYTES, library.retainedMeshBytes())
        assertEquals(1, meshes.getValue("a").destroyCount)
        assertEquals(1, meshes.getValue("b").destroyCount)
        assertEquals(0, meshes.getValue("c").destroyCount)
        assertEquals(0, meshes.getValue("live").destroyCount)
        assertTrue(library.isMeshRetained("c"))

        // trimMemory() drains remaining idle retained meshes and notifies installed resolvers.
        library.trimMemory()
        assertEquals(0L, library.retainedMeshBytes())
        assertFalse(library.isMeshRetained("c"))
        assertEquals(1, meshes.getValue("c").destroyCount)
        assertEquals(0, meshes.getValue("live").destroyCount, "Live held mesh must never be destroyed on trim")
        assertEquals(1, resolverTrims)
    }

    @Test
    fun defaultRetainedMeshBudgetScalesWithDeviceProfile() {
        assertEquals(
            SceneAssetLibrary.LOW_RAM_RETAINED_MESH_BUDGET_BYTES,
            SceneAssetLibrary.defaultRetainedMeshBudgetBytes(DeviceProfile(isLowRamDevice = true)),
        )
        assertEquals(
            SceneAssetLibrary.LOW_RAM_RETAINED_MESH_BUDGET_BYTES,
            SceneAssetLibrary.defaultRetainedMeshBudgetBytes(DeviceProfile(totalMemoryBytes = 4L * GIB)),
        )
        assertEquals(
            SceneAssetLibrary.MID_RAM_RETAINED_MESH_BUDGET_BYTES,
            SceneAssetLibrary.defaultRetainedMeshBudgetBytes(DeviceProfile(totalMemoryBytes = 6L * GIB)),
        )
        assertEquals(
            SceneAssetLibrary.HIGH_RAM_RETAINED_MESH_BUDGET_BYTES,
            SceneAssetLibrary.defaultRetainedMeshBudgetBytes(DeviceProfile(totalMemoryBytes = 16L * GIB)),
        )
        assertEquals(
            SceneAssetLibrary.HIGH_RAM_RETAINED_MESH_BUDGET_BYTES,
            SceneAssetLibrary.defaultRetainedMeshBudgetBytes(DeviceProfile(totalMemoryBytes = null)),
        )
    }

    private companion object {
        const val MESH_BYTES = 1024L
        const val GIB = 1024L * 1024L * 1024L
    }
}
