/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import com.awakekt.awake.vulkan.debug.PerFrameUniformSlots
import com.awakekt.awake.vulkan.enums.VkShaderStageFlagBits
import com.awakekt.awake.vulkan.renderer.Renderer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that [PerFrameUniformSlots.uniformBlock]'s `write` reuses its [UniformWriter] and
 * compares floats without allocating per frame on unchanged or updated content uniform writes (#1742).
 */
class ContentUniformBlockAllocationTest {
    @Test
    fun writingContentUniformBlockAllocatesNothingInSteadyState() {
        val session = vulkanHeadlessScene(SIZE, SIZE)
        val renderer = session.renderer as Renderer
        val layout = MaterialUniformLayouts.PbrMaterial
        val slots = PerFrameUniformSlots(
            graphicsDevice = renderer.graphicsDevice,
            uniformBytes = layout.total * Float.SIZE_BYTES,
            stageFlags = VkShaderStageFlagBits.FRAGMENT.value,
            framesInFlight = 2,
        )
        try {
            val block = slots.uniformBlock(layout)
            var roughness = 0.5f
            val fill: UniformWriter.() -> Unit = {
                put(UniformFields.PbrFactors, 0.2f, roughness, 0f, 0f)
            }
            // Populate both frame-in-flight slots first.
            block.write(0, fill)
            block.write(1, fill)
            val writesAfterWarmup = renderer.graphicsDevice.contentUniformWrites

            val unchangedReading = readAllocation(unitsPerWindow = ITERATIONS) {
                for (i in 0 until ITERATIONS) {
                    block.write(i and 1, fill)
                }
            }
            assertEquals(writesAfterWarmup, renderer.graphicsDevice.contentUniformWrites, "unchanged writes skip upload")
            assertAllocatesNothing(unchangedReading, "Vulkan PerFrameUniformSlots.uniformBlock unchanged write")

            val changedReading = readAllocation(unitsPerWindow = ITERATIONS) {
                for (i in 0 until ITERATIONS) {
                    roughness = 0.25f + (i and 7) * 0.05f
                    block.write(i and 1, fill)
                }
            }
            assertAllocatesNothing(changedReading, "Vulkan PerFrameUniformSlots.uniformBlock changed write")
        } finally {
            slots.destroy()
            session.close()
        }
    }

    private companion object {
        const val SIZE = 64
        const val ITERATIONS = 500
    }
}
