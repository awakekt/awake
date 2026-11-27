/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.pipeline.GpuDeviceClass
import com.awakekt.awake.render.renderer.SkinnedInstanceLayout
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.TextureRegion
import com.awakekt.awake.vulkan.commands.UploadBatcher
import com.awakekt.awake.vulkan.device.ReusableStagingBuffer
import com.awakekt.awake.vulkan.mesh.SkinnedInstanceBuffer
import com.awakekt.awake.vulkan.renderer.ensureImagePassTexture
import com.awakekt.awake.vulkan.texture.Texture
import com.awakekt.awake.vulkan.texture.TextureUploads
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies Wave 2 Vulkan memory pool growth, mobile staging caps with idle auto-reclamation,
 * and `trimMemory()` reclamation of `imagePassTextures` and CPU shader caches under
 * `VK_LAYER_KHRONOS_validation`.
 */
class VulkanPoolGrowthAndIdleReclaimTest {

    @Test
    fun skinnedInstanceBufferGrowsOnDemandAndRetiresOutgrownBuffers() =
        withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
            val buffer = SkinnedInstanceBuffer(renderer.graphicsDevice, maxInstances = 64, framesInFlight = FRAMES_IN_FLIGHT)
            try {
                assertEquals(0L, buffer.allocatedBytes, "SkinnedInstanceBuffer must allocate nothing before first use")
                assertEquals(0, buffer.capacityFor(0))
                assertEquals(0, buffer.capacityFor(1))

                val palette = identityPalette(joints = 2)
                val smallPalettes = List(2) { palette }
                val smallTints = List(2) { Vec4(1f, 0.5f, 0.25f, 1f) }
                buffer.update(0, smallPalettes, smallTints)
                assertEquals(SkinnedInstanceBuffer.MIN_CAPACITY, buffer.capacityFor(0))
                assertEquals(0, buffer.capacityFor(1), "Unused frame slots stay unallocated")
                val bytesPerInstance =
                    (SkinnedInstanceBuffer.FLOATS_PER_INSTANCE + SkinnedInstanceLayout.TINT_FLOATS).toLong() * Float.SIZE_BYTES
                assertEquals(SkinnedInstanceBuffer.MIN_CAPACITY * bytesPerInstance, buffer.allocatedBytes)
                val uploadsAfterFirst = buffer.uploads

                buffer.update(0, smallPalettes, smallTints)
                assertEquals(uploadsAfterFirst, buffer.uploads, "Unchanged palettes and tints skip upload")

                buffer.update(0, List(10) { palette }, List(10) { Vec4(0.2f, 0.8f, 0.4f, 1f) })
                assertEquals(16, buffer.capacityFor(0))
                assertEquals(1L, buffer.retiredCount)
                assertTrue(renderer.deferredResourceReaper.pending >= 1)

                repeat(FRAMES_IN_FLIGHT + 1) { renderer.presentWithoutScene() }
                assertEquals(0, renderer.deferredResourceReaper.pending, "Outgrown SkinnedInstanceBuffer must reap mid-session")
            } finally {
                buffer.destroy()
            }
        }

    @Test
    fun reusableStagingAndUploadBatcherEnforceMobileCapsAndAutoReclaimWhenIdle() =
        withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
            val device = renderer.graphicsDevice
            val previousClass = device.deviceClass
            val pool = device.stagingBufferPool
            val batcher = renderer.transferContext.uploadBatcher
            val prevPoolIdle = pool.idleReclaimFrames
            val prevBatcherIdle = batcher.idleReclaimFrames
            try {
                renderer.presentWithoutScene()
                device.deviceClass = GpuDeviceClass.Mobile
                pool.idleReclaimFrames = 3
                batcher.idleReclaimFrames = 3

                // Oversized mobile staging (> 2 MiB) is freed immediately instead of retained up to 16 MiB.
                pool.withStaging(ReusableStagingBuffer.MOBILE_MAX_RETAINED_BYTES + 65_536L) {}
                assertEquals(0L, pool.cachedBytes, "Mobile staging above 2 MiB must not stay cached")

                // Within mobile cap (512 KiB) stays warm, then auto-reclaims after 3 quiet frames.
                pool.withStaging(512L * 1024L) {}
                assertTrue(pool.cachedBytes >= 512L * 1024L)
                repeat(3) { renderer.presentWithoutScene() }
                assertEquals(0L, pool.cachedBytes, "Idle ReusableStagingBuffer must auto-reclaim after quiet frames")

                // UploadBatcher enforces mobile pooled-chunk cap (2) and auto-reclaims idle chunks.
                val flushSync: (((Long) -> Unit) -> Unit) = renderer.transferContext::runOneTimeCommands
                batcher.enqueue(
                    byteSize = UploadBatcher.MOBILE_MAX_POOLED_CHUNK_BYTES + 65_536L,
                    alignment = 16L,
                    flushSync = flushSync,
                    writeStaging = { _, _ -> },
                    recordCopy = { _, _, _ -> },
                )
                batcher.flushSynchronously(flushSync)
                assertEquals(0, batcher.freeChunkCount, "Mobile chunks above 2 MiB must not be retained in free pool")

                batcher.enqueue(
                    byteSize = 64L * 1024L,
                    alignment = 16L,
                    flushSync = flushSync,
                    writeStaging = { _, _ -> },
                    recordCopy = { _, _, _ -> },
                )
                batcher.flushSynchronously(flushSync)
                assertEquals(1, batcher.freeChunkCount)
                repeat(3) { renderer.presentWithoutScene() }
                assertEquals(0, batcher.freeChunkCount, "Idle UploadBatcher chunks must auto-reclaim after quiet frames")
            } finally {
                pool.idleReclaimFrames = prevPoolIdle
                batcher.idleReclaimFrames = prevBatcherIdle
                device.deviceClass = previousClass
            }
        }

    @Test
    fun textureUploadsBoundsFreePoolOnMobileAndAutoReclaimsWhenIdle() =
        withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
            val device = renderer.graphicsDevice
            val previousClass = device.deviceClass
            val uploads = renderer.textureUploads
            val prevIdle = uploads.idleReclaimFrames
            val writable = Texture(
                device,
                renderer.transferContext::runOneTimeCommands,
                ByteArray(TEX_EDGE * TEX_EDGE * 4) { -1 },
                TEX_EDGE,
                TEX_EDGE,
                filtering = TextureFiltering.BaseLevelLinear,
            )
            try {
                renderer.presentWithoutScene()
                device.deviceClass = GpuDeviceClass.Mobile
                uploads.idleReclaimFrames = 3
                val payload = ByteArray(TEX_EDGE * TEX_EDGE * 4) { 42 }
                val region = TextureRegion(layer = 0, x = 0, y = 0, width = TEX_EDGE, height = TEX_EDGE, data = payload)

                // Burst 6 writes into one frame slot (each 64 KiB); recycling must cap free list to 4 on mobile.
                renderer.transferContext.runOneTimeCommands { cmd ->
                    uploads.beginFrame(0, cmd)
                    repeat(6) { uploads.write(writable, region) }
                    uploads.endFrame()
                }
                renderer.transferContext.runOneTimeCommands { cmd ->
                    uploads.beginFrame(0, cmd)
                    uploads.endFrame()
                }
                assertTrue(
                    uploads.pooledBufferCount <= TextureUploads.MOBILE_MAX_FREE_BUFFERS,
                    "Mobile TextureUploads free pool must stay within ${TextureUploads.MOBILE_MAX_FREE_BUFFERS} buffers",
                )
                assertTrue(
                    uploads.pooledBytes <= TextureUploads.MOBILE_MAX_FREE_BYTES,
                    "Mobile TextureUploads free pool must stay within ${TextureUploads.MOBILE_MAX_FREE_BYTES} bytes",
                )
                assertTrue(uploads.pooledBufferCount > 0)

                // After 3 quiet frames, idle free staging buffers reclaim automatically.
                repeat(3) {
                    renderer.transferContext.runOneTimeCommands { cmd ->
                        uploads.beginFrame(0, cmd)
                        uploads.endFrame()
                    }
                }
                assertEquals(0, uploads.pooledBufferCount, "Idle TextureUploads buffers must auto-reclaim")
                assertEquals(0L, uploads.pooledBytes)
            } finally {
                uploads.idleReclaimFrames = prevIdle
                device.deviceClass = previousClass
                writable.destroy()
            }
        }

    @Test
    fun trimMemoryReclaimsImagePassTexturesAndInvokesOnTrimMemoryHook() =
        withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
            val asset = TextureAsset(ByteArray(32 * 32 * 4) { -1 }, 32, 32)
            val texturesBefore = renderer.textureResources.count
            val uploaded = renderer.ensureImagePassTexture(asset)
            assertEquals(1, renderer.imagePassTextures.size)
            assertEquals(texturesBefore + 1, renderer.textureResources.count)
            assertTrue(uploaded.imageView.handle != 0L)

            var trimHookCalls = 0
            renderer.onTrimMemory = { trimHookCalls += 1 }
            renderer.trimMemory()

            assertEquals(0, renderer.imagePassTextures.size, "trimMemory() must release cached imagePassTextures")
            assertEquals(texturesBefore, renderer.textureResources.count, "Released imagePassTextures must unregister from textureResources")
            assertEquals(1, trimHookCalls, "trimMemory() must invoke engine onTrimMemory hook")

            // Re-requesting the same asset after trim re-uploads cleanly with zero validation errors.
            val reuploaded = renderer.ensureImagePassTexture(asset)
            assertEquals(1, renderer.imagePassTextures.size)
            assertTrue(reuploaded.imageView.handle != 0L)
            renderer.presentWithoutScene()
        }

    private companion object {
        const val SIZE = 64
        const val FRAMES_IN_FLIGHT = 2
        const val TEX_EDGE = 128

        fun identityPalette(joints: Int): FloatArray = FloatArray(joints * 16).also { out ->
            val identity = Mat4().data
            for (joint in 0 until joints) identity.copyInto(out, joint * 16)
        }
    }
}
