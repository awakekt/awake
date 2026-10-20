/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.destroy
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.canvas.canvasFollowLimits
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneValidator
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import com.awakekt.awake.showcase.EngineShowcases
import com.awakekt.awake.showcase.loadEngineShowcaseDocument
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManyFollowersWiringTest {
    init {
        DefaultSceneComponentResolvers.install()
    }

    @Test
    fun manyFollowersSceneDeclaresSeparatePlatesAndQuestLimits() = runTest {
        val showcase = EngineShowcases.first { it.id == "many-followers" }
        val document = loadEngineShowcaseDocument(showcase)

        val issues = SceneValidator.validate(document)
        assertTrue(issues.isEmpty(), "Scene validation issues: $issues")

        val world = World()
        val scene = SceneLoader.instantiate(document, world)

        val limits = world.canvasFollowLimits()
        val plateLimit = limits.find { it.group == "plates" }
        val questLimit = limits.find { it.group == "quest" }

        assertTrue(plateLimit != null, "plates limit missing from world!")
        assertEquals(150, plateLimit.max)

        assertTrue(questLimit != null, "quest limit missing from world!")
        assertEquals(8, questLimit.max)

        scene.destroy()
    }
}
