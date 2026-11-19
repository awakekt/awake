/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.texture.Texture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies concurrent [TransferContext.runOneTimeCommands] uploads across threads do not corrupt the
 * shared upload command buffer or violate Vulkan's external queue synchronization rules.
 */
class TransferContextConcurrencyTest {
    @Test
    fun concurrentOneTimeUploadsSerializeCleanlyWithoutValidationErrors() {
        val device = GraphicsDevice()
        device.createHeadless()
        val transfer = TransferContext(device)
        val pool = Executors.newFixedThreadPool(2)
        val textures = java.util.Collections.synchronizedList(mutableListOf<Texture>())
        try {
            assertTrue(device.validationLayerEnabled, "Install VK_LAYER_KHRONOS_validation to run the queue sync gate")
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val futures = List(2) { worker ->
                pool.submit {
                    ready.countDown()
                    start.await(5, TimeUnit.SECONDS)
                    repeat(6) { iter ->
                        val pixel = ((worker * 16 + iter) and 0xFF).toByte()
                        val tex = Texture(
                            device,
                            transfer::runOneTimeCommands,
                            byteArrayOf(pixel, pixel, pixel, -1),
                            1,
                            1,
                        )
                        textures += tex
                    }
                }
            }
            ready.await(5, TimeUnit.SECONDS)
            start.countDown()
            futures.forEach { it.get(15, TimeUnit.SECONDS) }
        } finally {
            device.queueLock.withLock {
                textures.forEach(Texture::destroy)
            }
            transfer.destroy()
            pool.shutdownNow()
            device.destroy()
        }
    }
}
