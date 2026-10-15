/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.blueprint

import com.awakekt.awake.ecs.World
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A fixed step over started blueprints allocates nothing, whether they wait, sit idle or listen to
 * Buttons nobody presses.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class BlueprintSystemAllocationProbe {
    @Test
    fun aStepOverWaitingAndIdleBlueprintsAllocatesNothing() {
        val waiting = graph {
            node("start", "event.start")
            node("wait", "flow.delay", "seconds" to 1_000_000f)
            wire("start.then", "wait.exec")
        }
        val idle = graph {
            node("start", "event.start")
            node("pressed", "event.button.pressed")
            node("label", "ui.set-text", "text" to "pressed")
            wire("pressed.then", "label.exec")
        }
        val system = BlueprintSystem(graphs = { if (it == "waiting") waiting else idle })
        val world = World()
        repeat(BLUEPRINTS) { i ->
            val entity = world.create()
            world.add(entity, BlueprintComponent(if (i % 2 == 0) "waiting" else "idle"))
            world.add(entity, CanvasElement().apply { kind = CanvasElementKind.Button })
        }

        repeat(WARMUP) { system.update(world, STEP) }
        val before = allocated()
        repeat(MEASURED) { system.update(world, STEP) }

        assertEquals(0L, (allocated() - before) / MEASURED)
    }

    private fun allocated(): Long = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private companion object {
        const val BLUEPRINTS = 100
        const val WARMUP = 5_000
        const val MEASURED = 2_000
    }
}
