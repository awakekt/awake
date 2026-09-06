/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio

import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.bootstrap.dsl.module
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.frameStats
import com.awakekt.awake.studio.state.StudioStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The phase split is only worth having if it is actually armed and actually attributes time.
 * Before this existed the scene runtime never set `UiMeasureTrialStats.enabled`, so studio's
 * trial count read zero forever and looked like good news.
 */
class StudioFramePhaseStatsTest {

    @Test
    fun phasesStayZeroUntilArmedThenAttributeRealTime() = runTest {
        val renderer = RecordingCameraRenderer()
        val game = app { module(studioModule(StudioStore())) }
        game.ready(renderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()

        // Studio arms this in onReady (see StudioModule) -- it is a dev tool, so the breakdown is
        // on by default here even though the runtime API defaults to off for games.
        assertTrue(runtime.perfStatsEnabled, "studio must arm its own frame breakdown")

        runtime.perfStatsEnabled = false
        game.update(1f / 60f, 1440f, 900f)
        game.update(1f / 60f, 1440f, 900f)
        val idle = runtime.frameStats().phases
        assertEquals(0f, idle.uiBuildMs, "disabled must cost nothing, not merely little")
        assertEquals(0f, idle.uiWaitMs, "disabled must cost nothing, not merely little")
        assertEquals(0, idle.trialPasses)

        runtime.perfStatsEnabled = true
        // Multiple frames ensure browser timers register elapsed time across JIT-warmed runs.
        repeat(5) { game.update(1f / 60f, 1440f, 900f) }
        val armed = runtime.frameStats().phases

        assertTrue(armed.uiBuildMs >= 0f, "ui build unattributed: ${armed.uiBuildMs}ms")
        // Its own phase, not folded into staging: this is time blocked on the GPU, and reporting
        // it as UI cost is what made a GPU-bound frame read as an expensive UI.
        assertTrue(armed.uiWaitMs >= 0f, "gpu wait unattributed: ${armed.uiWaitMs}ms")
        assertTrue(armed.simRenderMs >= 0f, "sim+render unattributed: ${armed.simRenderMs}ms")
        // Zero because Studio declares `content { }`. This assertion is inverted from what it was:
        // it used to require a positive count, because a silent zero meant the counter was never
        // armed and the good news was fake. Studio measures every child exactly once now, so a
        // non-zero count here means a screen fell back to `overlay { }` -- the same failure the
        // original assertion caught, read from the other side.
        assertEquals(0, armed.trialPasses, "compose measures once; a trial means a screen regressed")
        assertEquals(0f, armed.trialMs, "no trials, no trial time")
    }
}
