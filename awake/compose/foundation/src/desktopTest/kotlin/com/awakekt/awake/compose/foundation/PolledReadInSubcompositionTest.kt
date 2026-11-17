/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.animation.rememberLoopingPhase
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxWithConstraints
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.dropShadow
import com.awakekt.awake.compose.ui.graphics.RectangleShape
import com.awakekt.awake.compose.ui.graphics.shadow.Shadow
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.StaleSkip
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A skipping host that verifies its skips reports no stale skip for an animation inside a
 * subcomposition (#1764).
 *
 * `BoxWithConstraints` composes its content during measure, so a skipped frame composes it there and
 * the verifier composes it again. `rememberLoopingPhase` advanced on each pass, so the second pass drew
 * one step further than the first and the verifier reported a skip that was not stale. It now advances
 * once a frame. The controls are the same pulse without the subcomposition, and a `mutableStateOf`
 * read inside it. Each case frames a pulsing shadow five times on a host that skips and verifies.
 */
class PolledReadInSubcompositionTest {
    private val input = FrameInput(400, 100)
    private val alpha = mutableStateOf(0.2f)

    private fun staleSkips(body: context(Composer) () -> Unit): List<StaleSkip> {
        val found = mutableListOf<StaleSkip>()
        val host = ComposeHost().apply {
            skipUnchangedComposition = true
            skipUnchangedMeasure = true
            verifySkippedCompositions = true
            onStaleSkip = { found += it }
        }
        repeat(FRAMES) { frame ->
            alpha.value = 0.2f + 0.1f * frame
            host.frame(input) { body() }
        }
        return found
    }

    context(_: Composer)
    private fun Pulse() {
        Box {
            Box(Modifier.size(40.dp).dropShadow(RectangleShape, Shadow(radius = 8.dp, alpha = rememberLoopingPhase(2.4f))))
        }
    }

    context(_: Composer)
    private fun Stated() {
        Box {
            Box(Modifier.size(40.dp).dropShadow(RectangleShape, Shadow(radius = 8.dp, alpha = alpha.value)))
        }
    }

    @Test
    fun aPolledReadInBoxWithConstraintsRecomposes() {
        val found = staleSkips { BoxWithConstraints(Modifier.fillMaxSize()) { Pulse() } }
        assertEquals(emptyList(), found, "the animation advanced twice in one frame")
    }

    @Test
    fun aPolledReadOutsideASubcompositionRecomposes() {
        assertEquals(emptyList(), staleSkips { Pulse() })
    }

    @Test
    fun aStateReadInBoxWithConstraintsRecomposes() {
        assertEquals(emptyList(), staleSkips { BoxWithConstraints(Modifier.fillMaxSize()) { Stated() } })
    }

    private companion object {
        const val FRAMES = 5
    }
}
