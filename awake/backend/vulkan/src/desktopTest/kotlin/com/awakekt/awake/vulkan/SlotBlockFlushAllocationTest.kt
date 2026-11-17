/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.material.PbrImageViews
import com.awakekt.awake.vulkan.material.SlotBlock
import com.awakekt.awake.vulkan.material.stageUniformBuffer
import com.awakekt.awake.vulkan.texture.Texture
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A slot block's flush writes the dirty range of its staged uniforms in place. Copying that range
 * out first, to hand the copy to `writeBufferMemoryFloats`, allocated its whole span every frame:
 * about 160 KB a frame in a populated scene, the largest Vulkan-backend share of a frame's garbage.
 */
class SlotBlockFlushAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun flushingAPartialDirtyRangeAllocatesNothingAndWritesIt() {
        withMaterial { device, material ->
            val dirtyBlocks = LinkedHashSet<SlotBlock>()
            val frames = List(2) { frame -> List(SLOTS) { slot -> FloatArray(FLOATS) { slot * 1_000f + frame * 100f + it } } }
            // Every slot once, so the block's memory holds all of them; then the middle slots change
            // each frame, a dirty range that is neither one slot nor the whole block.
            repeat(SLOTS) { material.stageUniformBuffer(0, it, frames[0][it], dirtyBlocks) }
            dirtyBlocks.forEach { it.flush(device.device) }
            dirtyBlocks.clear()

            // Every slot is in the first block; flushed directly, so the set's iterator isn't counted.
            val block = material.uniformSlot(0, 0).block
            var flushedBytes = 0L
            repeat(WARM_UP + FRAMES) { frame ->
                val values = frames[(frame + 1) % 2]
                for (slot in FIRST_DIRTY..LAST_DIRTY) material.stageUniformBuffer(0, slot, values[slot], dirtyBlocks)
                check(dirtyBlocks.size == 1 && block in dirtyBlocks) { "the dirty slots share one block" }
                val before = threads.currentThreadAllocatedBytes
                block.flush(device.device)
                if (frame >= WARM_UP) flushedBytes += threads.currentThreadAllocatedBytes - before
                dirtyBlocks.clear()
            }

            val perFrame = flushedBytes / FRAMES
            println("SlotBlock.flush of slots $FIRST_DIRTY..$LAST_DIRTY: $perFrame B/frame over $FRAMES frames")
            // The measurement's own few bytes aside: no copy of the range.
            assertTrue(perFrame < BUDGET_PER_FRAME, "flushing a dirty range allocated $perFrame B a frame")

            val last = frames[(WARM_UP + FRAMES) % 2]
            val expected = List(SLOTS) { slot -> if (slot in FIRST_DIRTY..LAST_DIRTY) last[slot] else frames[0][slot] }
            expected.forEachIndexed { slot, floats -> assertEquals(floats.toList(), readSlot(device, block, slot), "slot $slot") }
        }
    }

    private fun readSlot(device: GraphicsDevice, block: SlotBlock, slot: Int): List<Float> {
        val bytes = VulkanBuffers.readBufferMemoryBytes(device.device, block.memory, slot * block.stride, FLOATS * Float.SIZE_BYTES)
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer()
        return List(FLOATS) { floats[it] }
    }

    private fun withMaterial(body: (GraphicsDevice, Material) -> Unit) {
        val device = GraphicsDevice()
        device.createHeadless()
        try {
            val transfer = TransferContext(device)
            val texture = Texture(device, transfer::runOneTimeCommands, ByteArray(4) { -1 }, 1, 1)
            val view = texture.imageView.handle
            val material = Material(device)
            material.createResources(texture, PbrImageViews(view, view, view, view))
            try {
                body(device, material)
            } finally {
                material.destroy()
                texture.destroy()
                transfer.destroy()
            }
        } finally {
            device.destroy()
        }
    }

    private companion object {
        /** One MVP matrix, a default material's whole block. */
        const val FLOATS = 16
        const val SLOTS = 12
        const val FIRST_DIRTY = 3
        const val LAST_DIRTY = 8
        const val WARM_UP = 50
        const val FRAMES = 500
        const val BUDGET_PER_FRAME = 16L
    }
}
