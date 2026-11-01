/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.World
import com.awakekt.awake.ui.canvas.CanvasImage
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A binder that writes a steady state through resolved handles every frame, as a HUD does, looks
 * nothing up and builds no id: once warm, a frame allocates nothing.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class CanvasPartsAllocationProbeTest {
    @Test
    fun bindingASteadyStateThroughHandlesAllocatesNothingPerFrame() {
        val world = World()
        val bar = ActionBarParts().resolve(world, world.placeCanvas(actionBarFromParts().root))
        val icons = List(SLOT_COUNT) { CanvasImage("ui/icons/slot-$it.png") }
        val counts = List(SLOT_COUNT) { "$it" }
        val args = mapOf("count" to "3")
        fun frame() {
            bar.title.textArgs = args
            bar.energy.value = 0.5f
            val slots = bar.slots
            for (i in slots.indices) {
                val slot = slots[i]
                slot.icon.image = icons[i]
                slot.cooldown.value = 0.25f
                slot.count.text = counts[i]
                slot.button.enabled = true
            }
        }
        repeat(WARMUP_FRAMES) { frame() }

        val start = allocated()
        repeat(MEASURED_FRAMES) { frame() }

        assertEquals(0L, (allocated() - start) / MEASURED_FRAMES, "bytes per frame")
    }

    /**
     * The canvas's own step before each frame, bringing every element in step with its entity's
     * components, reads each element's components by type, as do the handles' component reads: a
     * steady HUD allocates nothing doing either (#1169). The quietest of several windows must be 0
     * bytes, so the JIT's own allocations, which can land in more than one window on a busy
     * machine, do not fail it; garbage made each frame shows in every window.
     */
    @Test
    fun syncingAndReadingTheComponentsOfASteadyHudAllocatesNothingPerFrame() {
        val world = World()
        val bar = ActionBarParts().resolve(world, world.placeCanvas(actionBarFromParts().root))
        world.syncCanvasElements()
        var size = 0f
        fun frame() {
            bar.energy.value = 0.5f
            world.syncCanvasElements()
            size = bar.title.words.fontSize
            val slots = bar.slots
            for (i in slots.indices) size += slots[i].count.words.fontSize
        }
        repeat(WARMUP_FRAMES) { frame() }

        val windows = LongArray(MEASURED_WINDOWS)
        repeat(MEASURED_WINDOWS) { window ->
            val start = allocated()
            repeat(MEASURED_FRAMES) { frame() }
            windows[window] = allocated() - start
        }
        windows.sort()

        println("CanvasSyncFrame: ${windows.joinToString()} bytes per $MEASURED_FRAMES frames (font sizes $size)")
        assertEquals(0L, windows[0], "fewest bytes over $MEASURED_FRAMES frames")
    }

    /** The gated sync the frame's callers use is as quiet as the full one when nothing changed. */
    @Test
    fun theGatedSyncOfASteadyHudAllocatesNothingPerFrame() {
        val world = World()
        ActionBarParts().resolve(world, world.placeCanvas(actionBarFromParts().root))
        world.syncActiveCanvasElements()
        fun frame() {
            repeat(CALLERS) { world.syncActiveCanvasElements() }
        }
        repeat(WARMUP_FRAMES) { frame() }

        val windows = LongArray(MEASURED_WINDOWS)
        repeat(MEASURED_WINDOWS) { window ->
            val start = allocated()
            repeat(MEASURED_FRAMES) { frame() }
            windows[window] = allocated() - start
        }
        windows.sort()

        println("CanvasSyncFrame gated: ${windows.joinToString()} bytes per $MEASURED_FRAMES frames")
        assertEquals(0L, windows[0], "fewest bytes over $MEASURED_FRAMES frames")
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private companion object {
        const val CALLERS = 3
        const val WARMUP_FRAMES = 300
        const val MEASURED_FRAMES = 600
        const val MEASURED_WINDOWS = 5
    }
}
