/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.rendering.animation.AnimationUpdateRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * A project game opts its scenes into a distance update rate through [CoreSceneContent.AnimationUpdateRate],
 * since `runProject` builds the [com.awakekt.awake.scene.rendering.animation.AnimationSystem] itself.
 */
class AnimationUpdateRateContentTest {
    private fun services(content: SceneContent) = SceneHostServices.headless(
        input = { GameplayInput(Input().currentSnapshot, InputOwnership()) },
        content = content,
    )

    @Test
    fun withNoRateEveryModelIsPosedEveryFrame() {
        assertSame(AnimationUpdateRate.EveryFrame, services(SceneContent.Empty).animationUpdateRate())
    }

    @Test
    fun aRateTheGameLoadedIsTheOneTheAnimationSystemGets() {
        val rate = AnimationUpdateRate.byDistance(near = 20f, far = 60f, farInterval = 0.25f)
        val content = SceneContent.build { this[CoreSceneContent.AnimationUpdateRate] = rate }

        val read = services(content).animationUpdateRate()

        assertSame(rate, read)
        assertEquals(0.25f, read.intervalFor(100f))
    }
}
