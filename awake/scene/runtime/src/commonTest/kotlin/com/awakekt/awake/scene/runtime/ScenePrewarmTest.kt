/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A prewarm gets a tree's assets ready before it spawns, holds them until closed, and stops cleanly. */
class ScenePrewarmTest : PrewarmFixture() {
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
        // The boat and its mast draw meshes; the flag draws a sprite, which has no MeshRenderer.
        val (boat, mast) = spawned.entities
        listOf(boat, mast).forEach { assertNotNull(runtime.world.get<MeshRenderer>(it), "drawn on its first frame") }
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
}
