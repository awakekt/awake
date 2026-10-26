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

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private companion object {
        const val WARMUP_FRAMES = 300
        const val MEASURED_FRAMES = 600
    }
}
