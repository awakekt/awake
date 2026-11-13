/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.game.TouchAim
import com.awakekt.awake.compose.game.TouchAimState
import com.awakekt.awake.compose.game.TouchStick
import com.awakekt.awake.compose.game.TouchStickState
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.AxisAction
import com.awakekt.awake.core.input.ButtonAction
import com.awakekt.awake.core.input.InputActions
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * With a finger held on a Compose stick and aim, the bridge that feeds them to the input actions
 * allocates nothing each frame once warm. The quietest of several windows must be 0 bytes, so the
 * JIT's own allocations do not fail the probe; one allocating frame would show in every window.
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class TouchControlInputAllocationProbeTest {
    @Test
    fun aHeldStickAndAimFeedTheActionsWithoutAllocating() {
        val actions = InputActions(listOf(AxisAction("move"), AxisAction("aim"), ButtonAction("fire")))
        val world = World().apply { add(create(), actions); add(create(), MovementControl()) }
        val stick = TouchStickState()
        val aim = TouchAimState()
        world.add(world.create(), TouchStickInput(stick))
        world.add(world.create(), TouchAimInput(aim, "aim", "fire"))
        val host = ComposeHost()
        fun frame(vararg touches: PointerFrame) = host.frame(FrameInput(300, 100, pointers = touches.toList())) {
            Row {
                TouchStick(Modifier.size(100.dp), stick)
                TouchAim(Modifier.size(100.dp), aim, radius = 50.dp)
            }
        }
        frame()
        frame(PointerFrame(1, 50, 50, down = true, pressed = true), PointerFrame(2, 150, 50, down = true, pressed = true))
        frame(PointerFrame(1, 75, 50, down = true), PointerFrame(2, 150, 0, down = true))
        assertTrue(stick.isHeld && aim.isAiming)
        val system = TouchControlInputSystem()
        fun step() {
            actions.beginFrame()
            system.update(world, 1f / 60f)
        }
        repeat(WARMUP) { step() }
        assertEquals(0.5f, actions.axisX("move"), 0.001f)
        assertTrue(actions.isActive("fire"))

        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        val windows = LongArray(WINDOWS)
        repeat(WINDOWS) { window ->
            val before = bean.currentThreadAllocatedBytes
            repeat(FRAMES) { step() }
            windows[window] = bean.currentThreadAllocatedBytes - before
        }
        assertEquals(0L, windows.min(), "a held finger allocated in every window: ${windows.toList()}")
    }

    private companion object {
        const val WARMUP = 20_000
        const val WINDOWS = 5
        const val FRAMES = 1_000
    }
}
