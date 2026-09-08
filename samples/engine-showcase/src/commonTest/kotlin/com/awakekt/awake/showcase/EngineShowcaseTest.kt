/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.scene.binding.destroy
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineShowcaseTest {
    init {
        DefaultSceneComponentResolvers.install()
    }

    @Test
    fun everyShowcaseUsesAStableIdAndAResourceScene() {
        assertEquals(EngineShowcases.map(EngineShowcase::id).toSet().size, EngineShowcases.size)
        assertTrue(EngineShowcases.all { it.scenePath.startsWith("assets/examples/") })
    }

    @Test
    fun everyShowcaseSceneLoadsAndInstantiates() = runTest {
        EngineShowcases.forEach { showcase ->
            val document = SceneLoader.loadFromResource(showcase.scenePath)
            val scene = SceneLoader.instantiate(document)

            assertEquals(showcase.id, document.name)
            assertTrue(scene.roots.isNotEmpty(), "${showcase.id} has no scene roots")
            scene.destroy()
        }
    }
}
