/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The scene DSL sets how many fixed steps a frame runs, and leaves Core's default when it doesn't. */
class SceneFixedStepDslTest {
    @Test
    fun aSceneRunsFiveFixedStepsAFrameUnlessItSaysOtherwise() {
        assertEquals(5, sceneApp {}.maxFixedStepsPerFrame)
        assertEquals(15, sceneApp { maxFixedStepsPerFrame(15) }.maxFixedStepsPerFrame)
    }

    @Test
    fun aCapBelowOneIsRefused() {
        assertFailsWith<IllegalArgumentException> { sceneApp { maxFixedStepsPerFrame(0) } }
    }
}
