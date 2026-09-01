/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

import io.github.awakelab.awake.scene.runtime.SceneLoader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That the terrain showcase is wired to the things that move in it.
 *
 * These are structural rather than behavioural on purpose. Whether a capsule stands on the
 * terrain is `CharacterControllerJoltTest`'s job and is asserted against real Jolt; what can
 * still break silently is the join between the two -- a renamed node in the scene document, or a
 * hook dropped from the showcase entry -- and the failure mode there is a demonstration that
 * loads and simply does nothing.
 */
class TerrainShowcaseWiringTest {

    private val terrain = EngineShowcases.single { it.id == "heightfield-terrain" }

    @Test
    fun theSceneDocumentStillContainsTheNodesTheDriversLookUpByName() = runTest {
        val document = SceneLoader.loadFromResource(terrain.scenePath)
        val names = document.nodes.mapNotNull { it.name }

        assertTrue("player" in names, "the character driver attaches to a node named 'player'")
        assertTrue(
            "heightfield-terrain" in names,
            "the terrain collider attaches to a node named 'heightfield-terrain'",
        )
        assertTrue(
            names.count { it.startsWith("falling-box-") } == 4,
            "the physics demonstration drops four boxes, found ${names.count { it.startsWith("falling-box-") }}",
        )
    }

    @Test
    fun theShowcaseAttachesAndDetachesWhatItSpawns() {
        // Both drivers create state outside the scene document -- Jolt bodies and a controller --
        // so both halves have to be present or switching showcases leaks one of them.
        assertNotNull(terrain.onActivated, "terrain physics and the character are attached on activation")
        assertNotNull(terrain.onDeactivated, "and released when the showcase switches away")
    }
}
