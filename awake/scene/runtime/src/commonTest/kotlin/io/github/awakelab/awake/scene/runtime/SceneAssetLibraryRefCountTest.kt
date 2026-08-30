/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.mesh.Mesh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A streamed cell that unloads has to free its GPU memory, and must not free memory another cell
 * is still drawing. Both failures are silent -- a leak grows until something dies, and an early
 * destroy corrupts a live buffer -- so each is asserted directly.
 */
class SceneAssetLibraryRefCountTest {

    private class FakeMesh(override val sizeBytes: Long = 100) : Mesh {
        override val format = VertexFormat.PositionColorUv
        var destroyCount = 0
        override fun destroy() { destroyCount++ }
    }

    private class FakeMaterial : Material {
        var destroyCount = 0
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() { destroyCount++ }
    }

    private var meshesBuilt = 0
    private val builtMeshes = mutableListOf<FakeMesh>()

    private fun library() = SceneAssetLibrary(
        meshFactories = mapOf(
            "rock" to { meshesBuilt++; FakeMesh().also { builtMeshes += it } },
        ),
        materialFactories = mapOf("stone" to { FakeMaterial() }),
        rendererFactories = emptyMap(),
    )

    /**
     * A real runtime over an empty spec.
     *
     * The fake factories below ignore their receiver, but the typealiases declare one, and a
     * cast-from-null blows up in class init rather than staying unused. Nothing here touches
     * `renderer`, which is the only part that needs a backend.
     */
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

    @Test
    fun twoHoldersKeepAMeshAliveUntilBothRelease() {
        val library = library()
        library.requireMesh(runtime, "rock")
        library.requireMesh(runtime, "rock")
        assertEquals(2, library.meshHolderCount("rock"))
        assertEquals(1, meshesBuilt, "One build, shared -- not one per holder.")

        assertFalse(library.releaseMesh("rock"), "One holder left; nothing should be destroyed.")
        assertEquals(0, builtMeshes.single().destroyCount)

        assertTrue(library.releaseMesh("rock"), "The last release destroys.")
        assertEquals(1, builtMeshes.single().destroyCount)
        assertEquals(0, library.meshHolderCount("rock"))
    }

    /** A cell re-entering the radius must get a working mesh, not a destroyed one. */
    @Test
    fun acquiringAfterTheLastReleaseRebuilds() {
        val library = library()
        library.requireMesh(runtime, "rock")
        library.releaseMesh("rock")

        library.requireMesh(runtime, "rock")

        assertEquals(2, meshesBuilt, "The destroyed mesh must not be handed out again.")
        assertEquals(1, library.meshHolderCount("rock"))
    }

    /** Silence here is what destroys a buffer something still draws. */
    @Test
    fun releasingSomethingNothingHoldsFails() {
        val library = library()

        assertFailsWith<IllegalStateException> { library.releaseMesh("rock") }

        library.requireMesh(runtime, "rock")
        library.releaseMesh("rock")
        assertFailsWith<IllegalStateException> { library.releaseMesh("rock") }
    }

    /** Teardown outranks refcounting, but must not double-destroy what was already released. */
    @Test
    fun disposeDestroysWhatIsHeldAndNothingElse() {
        val library = library()
        library.requireMesh(runtime, "rock")
        library.requireMesh(runtime, "rock")
        val held = builtMeshes.single()

        library.dispose()

        assertEquals(1, held.destroyCount, "A leaked reference must not leak the GPU resource.")
        assertEquals(0, library.meshHolderCount("rock"))
    }

    @Test
    fun aReleasedMeshIsNotDestroyedAgainByDispose() {
        val library = library()
        library.requireMesh(runtime, "rock")
        library.releaseMesh("rock")
        val released = builtMeshes.single()

        library.dispose()

        assertEquals(1, released.destroyCount, "Released once, destroyed once.")
    }

    @Test
    fun materialsRefCountTheSameWay() {
        val library = library()
        library.requireMaterial(runtime, "stone")
        library.requireMaterial(runtime, "stone")

        assertFalse(library.releaseMaterial("stone"))
        assertTrue(library.releaseMaterial("stone"))
        assertEquals(0, library.materialHolderCount("stone"))
    }
}
