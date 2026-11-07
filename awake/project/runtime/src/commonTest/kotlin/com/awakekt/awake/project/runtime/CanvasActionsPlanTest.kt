/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.scene.document.SceneDocument
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A scene plans its canvas actions only where something draws the canvas: a headless host, such as a game
 * server, takes no touches and would only walk its canvas elements every frame for nothing.
 */
class CanvasActionsPlanTest {
    private fun planned(hasRenderer: Boolean): List<String> {
        val plan = SceneSystemPlan(hasPhysics = false, hasRenderer = hasRenderer)
        ControlsCapability.plan(SceneDocument(), plan)
        return plan.specs.map { it.name }
    }

    @Test
    fun aSceneThatIsDrawnPlansItsCanvasActions() {
        assertTrue("canvas-actions" in planned(hasRenderer = true))
    }

    @Test
    fun aHeadlessScenePlansNone() {
        assertFalse("canvas-actions" in planned(hasRenderer = false))
    }
}
