/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A showcase that spawns entities outside its scene document has to be told when it stops being
 * the one running, or those entities survive the switch: streamed terrain from one demonstration
 * was left hanging under the next one, which is what this hook exists to prevent.
 */
class ShowcaseDeactivationTest {

    @Test
    fun theShowcaseThatSpawnsItsOwnEntitiesCleansThemUp() {
        val streamed = EngineShowcases.single { it.id == "streamed-nav" }

        assertTrue(
            streamed.onDeactivated != null,
            "streamed-nav spawns terrain entities of its own, so it must clean them up on switch.",
        )
    }

    /** A showcase whose entities all come from its scene document needs no hook, and has none. */
    @Test
    fun sceneOnlyShowcasesDeclareNoCleanup() {
        val sceneOnly = EngineShowcases.filter { it.id in setOf("empty", "point-lights") }

        assertEquals(2, sceneOnly.size)
        sceneOnly.forEach { showcase ->
            assertTrue(showcase.onDeactivated == null, "${showcase.id} has nothing of its own to clean up.")
        }
    }
}
