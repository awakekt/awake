/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.scene.document.SceneDocument
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A scene plans its particle systems whenever something can draw them, so emitters spawned at runtime,
 * in prefabs the scene document doesn't hold, emit and draw.
 */
class ParticlesPlanTest {
    private fun planned(hasRenderer: Boolean): List<String> {
        val plan = SceneSystemPlan(hasPhysics = false, hasRenderer = hasRenderer)
        ParticlesCapability.plan(SceneDocument(), plan)
        return plan.specs.map { it.name }
    }

    @Test
    fun aSceneWithNoEmitterOfItsOwnStillPlansParticlesForOnesSpawnedLater() {
        assertEquals(listOf("particle-content", "particles"), planned(hasRenderer = true))
    }

    @Test
    fun aSceneWithNoRendererPlansNone() {
        assertEquals(emptyList(), planned(hasRenderer = false))
    }
}
