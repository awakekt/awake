/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.vulkan.mesh.SkinnedInstanceBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import com.awakekt.awake.vulkan.renderer.Renderer as VulkanRenderer

/**
 * Verifies [SkinnedInstanceBuffer] skips redundant palette and tint storage-buffer uploads when
 * the packed bits match what is already resident in that frame slot.
 */
class SkinnedInstanceBufferReuseTest {

    @Test
    fun unchangedPalettesAndTintsSkipStorageBufferUploads() {
        val session = vulkanHeadlessScene(SIZE, SIZE)
        try {
            val device = (session.renderer as VulkanRenderer).graphicsDevice
            val buffer = SkinnedInstanceBuffer(device, maxInstances = 4, framesInFlight = 1)
            try {
                val idleA = identityPalette(joints = 2)
                val idleB = identityPalette(joints = 2)
                val white = listOf(Vec4(1f, 1f, 1f, 1f), Vec4(1f, 1f, 1f, 1f))

                buffer.update(0, listOf(idleA, idleB), white)
                assertEquals(2L, buffer.uploads, "first frame uploads both palettes and tints")

                buffer.update(0, listOf(idleA, idleB), white)
                assertEquals(2L, buffer.uploads, "identical palettes and tints skip both uploads")

                val movedB = identityPalette(joints = 2).also { it[12] = 1.5f }
                buffer.update(0, listOf(idleA, movedB), white)
                assertEquals(3L, buffer.uploads, "changing only a palette uploads palettes without re-uploading tints")

                val tinted = listOf(Vec4(1f, 0.5f, 0.5f, 1f), Vec4(1f, 1f, 1f, 1f))
                buffer.update(0, listOf(idleA, movedB), tinted)
                assertEquals(4L, buffer.uploads, "changing only tints uploads tints without re-uploading palettes")

                buffer.update(0, listOf(idleA, movedB), tinted)
                assertEquals(4L, buffer.uploads, "steady frame after updates skips both uploads again")

                buffer.update(0, listOf(idleA), listOf(Vec4(1f, 0.5f, 0.5f, 1f)))
                assertEquals(6L, buffer.uploads, "shrinking instance count re-uploads the shorter slice")

                buffer.update(0, listOf(idleA), listOf(Vec4(1f, 0.5f, 0.5f, 1f)))
                assertEquals(6L, buffer.uploads, "steady frame at the shrunk count skips uploads")
            } finally {
                buffer.destroy()
            }
        } finally {
            session.close()
        }
    }

    private companion object {
        const val SIZE = 32

        fun identityPalette(joints: Int): FloatArray = FloatArray(joints * 16).also { out ->
            val identity = Mat4().data
            for (joint in 0 until joints) identity.copyInto(out, joint * 16)
        }
    }
}
