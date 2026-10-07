/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.DepthOnlyPipeline
import com.awakekt.awake.vulkan.texture.DepthTarget
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * No two frames in flight share a cascade's matrix slot.
 *
 * The CPU records the next frame while the GPU may not have drawn this one's shadow maps yet. When
 * the slots were shared, a moving camera had its shadow maps drawn with the next frame's cascades
 * and sampled with this frame's, and flat ground shadowed itself in patches.
 */
class DepthCascadeSlotsTest {

    @Test
    fun everyFrameAndCascadeHasItsOwnSlot() {
        val graphicsDevice = GraphicsDevice().apply { createHeadless() }
        val depthTarget = DepthTarget(graphicsDevice, layers = CASCADES, arrayed = true, comparison = true)
        val layout = Material.createDescriptorSetLayout(graphicsDevice)
        val pipeline = DepthOnlyPipeline(
            graphicsDevice,
            depthTarget.renderPass,
            layout,
            runBlocking { packShaderPair("shadow_depth") },
            VertexFormat.PositionNormalColor,
            depthTarget.size,
            cascadeCount = CASCADES,
            framesInFlight = FRAMES_IN_FLIGHT,
        )
        try {
            // Frame index FRAMES_IN_FLIGHT is the offscreen frame.
            val sets = (0..FRAMES_IN_FLIGHT).flatMap { frame -> (0 until CASCADES).map { pipeline.cascadeBinding(frame, it) } }
            assertEquals(sets.size, sets.toSet().size, "Two frames or cascades share a descriptor set: $sets")
        } finally {
            pipeline.destroy()
            depthTarget.destroy()
            VulkanDescriptors.vkDestroyDescriptorSetLayout(graphicsDevice.device, layout.handle)
            graphicsDevice.destroy()
        }
    }

    private companion object {
        const val CASCADES = 4
        const val FRAMES_IN_FLIGHT = 2
    }
}
