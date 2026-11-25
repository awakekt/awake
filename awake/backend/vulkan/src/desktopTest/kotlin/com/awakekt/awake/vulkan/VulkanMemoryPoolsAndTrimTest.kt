/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.DEFAULT_SCENE_LIGHT
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.ParticleUniformLayout
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.TextureRegion
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.material.PbrImageViews
import com.awakekt.awake.vulkan.material.SlotBlock
import com.awakekt.awake.vulkan.material.stageUniformBuffer
import com.awakekt.awake.vulkan.mesh.AlphaInstanceBuffer
import com.awakekt.awake.vulkan.mesh.FrameInstanceBuffer
import com.awakekt.awake.vulkan.mesh.InstanceBuffer
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.texture.Texture
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.awakekt.awake.render.renderer.Renderer as ContractRenderer

/**
 * Verifies Vulkan memory-pool reductions and `Renderer.trimMemory()` reclamation under
 * `VK_LAYER_KHRONOS_validation`:
 * - [AlphaInstanceBuffer] and [FrameInstanceBuffer] allocate lazily from small initial capacities.
 * - Outgrown [InstanceBuffer], [AlphaInstanceBuffer], and [FrameInstanceBuffer] slot buffers retire
 *   through `DeferredResourceReaper` mid-session rather than leaking until `destroy()`.
 * - Untinted instanced runs share a single immutable white buffer while tinted and particle
 *   auxiliary streams use dense slot counters with zero cross-pool gap-filling.
 * - [Material] allocates small initial `SlotBlock`s (4 slots for standard UBOs, 2 slots for large
 *   skinned UBOs) and grows progressively across blocks on overflow.
 * - Native-resolution swapchain depth targets are allocated lazily and skipped/retired when
 *   `renderScale < 1`.
 * - [ContractRenderer.trimMemory] reclaims staging pools, pooled instance/UI buffers, and empty
 *   `DEVICE_LOCAL` slabs while keeping caller-owned assets usable on subsequent frames.
 */
class VulkanMemoryPoolsAndTrimTest {

    @Test
    fun alphaInstanceBufferGrowsOnDemandAndRetiresOutgrownBuffers() =
        withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
            val buffer = AlphaInstanceBuffer(renderer.graphicsDevice, maxInstances = 4096, framesInFlight = FRAMES_IN_FLIGHT)
            try {
                assertEquals(0L, buffer.allocatedBytes, "AlphaInstanceBuffer must allocate nothing before first use")
                assertEquals(0, buffer.capacityFor(0))
                assertEquals(0, buffer.capacityFor(1))

                val smallTint = List(4) { Vec4(1f, 0.5f, 0.25f, 1f) }
                buffer.update(0, smallTint)
                assertEquals(AlphaInstanceBuffer.MIN_CAPACITY, buffer.capacityFor(0))
                assertEquals(0, buffer.capacityFor(1), "Unused frame slots stay unallocated")
                val expectedSmallBytes = AlphaInstanceBuffer.MIN_CAPACITY.toLong() * AlphaInstanceBuffer.FLOATS_PER_INSTANCE * Float.SIZE_BYTES
                assertEquals(expectedSmallBytes, buffer.allocatedBytes)
                val uploadsAfterFirst = buffer.uploads

                // Re-uploading identical tints skips redundant mapped-memory copies.
                buffer.update(0, smallTint)
                assertEquals(uploadsAfterFirst, buffer.uploads)

                val grownTint = List(40) { Vec4(0.2f, 0.8f, 0.4f, 0.9f) }
                buffer.update(0, grownTint)
                assertEquals(64, buffer.capacityFor(0))
                assertEquals(1L, buffer.retiredCount)
                assertTrue(renderer.deferredResourceReaper.pending >= 1)

                repeat(FRAMES_IN_FLIGHT + 1) { renderer.presentWithoutScene() }
                assertEquals(0, renderer.deferredResourceReaper.pending, "Outgrown AlphaInstanceBuffer must reap mid-session")
            } finally {
                buffer.destroy()
            }
        }

    @Test
    fun instanceBufferReapsOutgrownBuffersThroughDeferredReaper() =
        withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
            val buffer = InstanceBuffer(renderer.graphicsDevice, maxInstances = 4096, framesInFlight = FRAMES_IN_FLIGHT)
            try {
                buffer.update(0, List(4) { Mat4() })
                assertEquals(16, buffer.capacityFor(0))
                assertEquals(0L, buffer.retiredCount)

                buffer.update(0, List(40) { Mat4() })
                assertEquals(64, buffer.capacityFor(0))
                assertEquals(1L, buffer.retiredCount)

                buffer.update(0, List(200) { Mat4() })
                assertEquals(256, buffer.capacityFor(0))
                assertEquals(2L, buffer.retiredCount)
                assertTrue(
                    renderer.deferredResourceReaper.pending >= 2,
                    "Outgrown InstanceBuffer allocations must be queued in DeferredResourceReaper",
                )

                repeat(FRAMES_IN_FLIGHT + 1) { renderer.presentWithoutScene() }
                assertEquals(
                    0,
                    renderer.deferredResourceReaper.pending,
                    "Outgrown InstanceBuffer allocations must be freed once frames in flight complete",
                )
            } finally {
                buffer.destroy()
            }
        }

    @Test
    fun untintedRunsShareWhiteBufferAndParticleFramesUseDenseSlotsWithoutGapFilling() {
        val session = vulkanHeadlessScene(SIZE, SIZE)
        val renderer = session.renderer as Renderer
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val white = TextureAsset(byteArrayOf(-1, -1, -1, -1), 1, 1)
        val propMaterial = renderer.createMaterial(
            texture = white,
            uniformFloatCount = MaterialUniformLayouts.PbrTextured.total,
            pbrTextures = PbrTextureSet(),
        )
        val particleMaterial = renderer.createMaterial(ParticleUniformLayout, texture = white)
        val propMeshes = List(UNTINTED_RUN_COUNT + 1) {
            renderer.createMesh(MeshGeometry(TEXTURED_QUAD_VERTICES, PARTICLE_QUAD_INDICES, VertexFormat.PositionNormalColorUv))
        }
        val particleMesh = renderer.createMesh(
            MeshGeometry(PARTICLE_QUAD_VERTICES, PARTICLE_QUAD_INDICES, VertexFormat.PositionUv),
        )
        try {
            val draws = buildMixedInstancedDraws(propMeshes, propMaterial, particleMesh, particleMaterial)
            val camera = Lens(eye = Vec3f(0f, 1f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f)
            val passInput = ScenePassCompiler.compile(
                lens = camera,
                drawCalls = draws,
                light = DEFAULT_SCENE_LIGHT,
                environment = EnvironmentUniforms.Default,
                clipSpace = renderer.clipSpace,
                aspect = 1f,
                viewport = null,
                drawPreparer = renderer.offscreenDrawPreparer,
            )
            renderer.renderToTexture(target, passInput)

            assertEquals(UNTINTED_RUN_COUNT + 2, renderer.bufferPools.instanceBufferPoolSize)
            assertTrue(renderer.bufferPools.hasWhiteAlphaInstanceBuffer, "Untinted instanced runs must share one white buffer")
            assertEquals(2, renderer.bufferPools.alphaInstanceBufferPoolSize)
            assertEquals(1, renderer.bufferPools.frameInstanceBufferPoolSize)
            val offscreenFrameSlot = renderer.commandBuffers.size
            assertEquals(AlphaInstanceBuffer.MIN_CAPACITY, renderer.bufferPools.alphaInstanceBufferForRun(0).capacityFor(offscreenFrameSlot))
            assertEquals(FrameInstanceBuffer.MIN_CAPACITY, renderer.bufferPools.frameInstanceBufferForRun(0).capacityFor(offscreenFrameSlot))
        } finally {
            propMeshes.forEach { it.destroy() }
            particleMesh.destroy()
            propMaterial.destroy()
            particleMaterial.destroy()
            target.destroy()
            session.close()
        }
    }

    private fun buildMixedInstancedDraws(
        propMeshes: List<com.awakekt.awake.render.mesh.Mesh>,
        propMaterial: com.awakekt.awake.render.material.Material,
        particleMesh: com.awakekt.awake.render.mesh.Mesh,
        particleMaterial: com.awakekt.awake.render.material.Material,
    ): List<RenderDrawCommand> {
        val untintedRuns = List(UNTINTED_RUN_COUNT) { index ->
            RenderDrawCommand(mesh = propMeshes[index], material = propMaterial, instanceModels = listOf(Mat4(), Mat4()))
        }
        val tintedRun = RenderDrawCommand(
            mesh = propMeshes[UNTINTED_RUN_COUNT],
            material = propMaterial,
            instanceModels = listOf(Mat4(), Mat4(), Mat4()),
            instanceColors = listOf(Vec4(1f, 0f, 0f, 1f), Vec4(0f, 1f, 0f, 1f), Vec4(0f, 0f, 1f, 1f)),
        )
        val particleRun = RenderDrawCommand(
            mesh = particleMesh,
            material = particleMaterial,
            transparent = true,
            instanceModels = listOf(Mat4(), Mat4()),
            instanceColors = listOf(Vec4(1f, 1f, 1f, 0.8f), Vec4(1f, 1f, 1f, 0.5f)),
            instanceFrames = listOf(0f, 1f),
            extraUniformFloats = PARTICLE_EXTRAS,
        )
        return untintedRuns + tintedRun + particleRun
    }

    @Test
    fun materialSlotBlocksStartSmallAndGrowDynamicallyAcrossBlocks() {
        val device = GraphicsDevice().apply { createHeadless() }
        val transfer = TransferContext(device)
        val texture = Texture(device, transfer::runOneTimeCommands, ByteArray(4) { -1 }, 1, 1)
        val view = texture.imageView.handle
        val pbr = PbrImageViews(view, view, view, view)
        val standardMaterial = Material(device, uniformFloatCount = STANDARD_UBO_FLOATS).apply { createResources(texture, pbr) }
        val skinnedMaterial = Material(device, uniformFloatCount = SKINNED_UBO_FLOATS).apply { createResources(texture, pbr) }
        try {
            val dirtyStandard = LinkedHashSet<SlotBlock>()
            standardMaterial.stageUniformBuffer(0, 0, FloatArray(STANDARD_UBO_FLOATS) { it.toFloat() }, dirtyStandard)
            assertEquals(1, standardMaterial.slotBlockCount)
            assertEquals(Material.INITIAL_SLOTS_PER_BLOCK, standardMaterial.allocatedSlotCapacity)
            dirtyStandard.forEach { it.flush(device.device) }
            dirtyStandard.clear()

            // Overflow across 20 slots -> 4 + 8 + 16 = 28 capacity across 3 blocks.
            val standardPayloads = List(20) { slot -> FloatArray(STANDARD_UBO_FLOATS) { slot * 100f + it } }
            standardPayloads.forEachIndexed { slot, values ->
                standardMaterial.stageUniformBuffer(0, slot, values, dirtyStandard)
            }
            assertEquals(3, standardMaterial.slotBlockCount)
            assertEquals(4 + 8 + 16, standardMaterial.allocatedSlotCapacity)
            dirtyStandard.forEach { it.flush(device.device) }
            dirtyStandard.clear()
            standardPayloads.forEachIndexed { slotIndex, expected ->
                val slot = standardMaterial.uniformSlot(0, slotIndex)
                assertEquals(expected.toList(), readSlotFloats(slot.block, slot.indexInBlock, STANDARD_UBO_FLOATS))
            }

            // Large skinned UBO starts at 2 slots (16x smaller than 32) and grows 2 -> 4 -> 8 across blocks.
            val dirtySkinned = LinkedHashSet<SlotBlock>()
            skinnedMaterial.stageUniformBuffer(0, 0, FloatArray(SKINNED_UBO_FLOATS) { 1f }, dirtySkinned)
            assertEquals(1, skinnedMaterial.slotBlockCount)
            assertEquals(Material.LARGE_UBO_INITIAL_SLOTS_PER_BLOCK, skinnedMaterial.allocatedSlotCapacity)
            dirtySkinned.forEach { it.flush(device.device) }
            dirtySkinned.clear()

            val skinnedPayloads = List(10) { slot -> FloatArray(SKINNED_UBO_FLOATS) { slot * 10f + (it % 7) } }
            skinnedPayloads.forEachIndexed { slot, values ->
                skinnedMaterial.stageUniformBuffer(0, slot, values, dirtySkinned)
            }
            assertEquals(3, skinnedMaterial.slotBlockCount)
            assertEquals(2 + 4 + 8, skinnedMaterial.allocatedSlotCapacity)
            dirtySkinned.forEach { it.flush(device.device) }
            dirtySkinned.clear()
            val lastSkinnedSlot = skinnedMaterial.uniformSlot(0, 9)
            assertEquals(
                skinnedPayloads[9].toList(),
                readSlotFloats(lastSkinnedSlot.block, lastSkinnedSlot.indexInBlock, SKINNED_UBO_FLOATS),
            )
        } finally {
            standardMaterial.destroy()
            skinnedMaterial.destroy()
            texture.destroy()
            transfer.destroy()
            device.destroy()
        }
    }

    @Test
    fun swapchainDepthIsAllocatedLazilyAndSkippedWhenRenderScaleBelowOne() =
        withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
            assertTrue(renderer.depthImages.isEmpty(), "Swapchain depth must not allocate eagerly at construction")
            assertTrue(renderer.framebuffers.isEmpty(), "Swapchain scene framebuffers must not allocate eagerly")

            renderer.renderScale = 0.5f
            repeat(2) { renderer.presentWithoutScene() }
            assertTrue(renderer.depthImages.isEmpty(), "Scaled frames must never allocate native-resolution swapchain depth")
            assertTrue(renderer.depthImageMemories.isEmpty())
            assertTrue(renderer.framebuffers.isEmpty())

            renderer.renderScale = 1.0f
            renderer.presentWithoutScene()
            assertEquals(
                renderer.swapchainManager.imageViews.size,
                renderer.depthImages.size,
                "Direct swapchain 3D pass must lazily allocate swapchain depth images",
            )
            assertEquals(renderer.swapchainManager.imageViews.size, renderer.framebuffers.size)

            renderer.renderScale = 0.5f
            repeat(FRAMES_IN_FLIGHT + 2) { renderer.presentWithoutScene() }
            assertTrue(renderer.depthImages.isEmpty(), "Switching back to scaled rendering must retire swapchain depth")
            assertTrue(renderer.framebuffers.isEmpty())
            assertEquals(0, renderer.deferredResourceReaper.pending, "Retired swapchain depth must reap after frames complete")
        }

    @Test
    fun trimMemoryReclaimsStagingPoolsBufferPoolsAndEmptySlabsCleanly() =
        withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
            val mesh = renderer.createMesh(
                MeshGeometry(TRIANGLE_VERTICES, TRIANGLE_INDICES, VertexFormat.PositionNormalColor),
            )
            val material = renderer.createMaterial(
                texture = TextureAsset(ByteArray(32 * 32 * 4) { -1 }, 32, 32),
            )
            val writableTexture = Texture(
                renderer.graphicsDevice,
                renderer.transferContext::runOneTimeCommands,
                ByteArray(16 * 16 * 4) { -1 },
                16,
                16,
                filtering = TextureFiltering.BaseLevelLinear,
            )
            try {
                // Warm UploadBatcher free chunks through frame-deferred asset creation + presentation.
                renderer.drawUi(listOf(UiDrawPrimitive.Quad(0f, 0f, 16f, 16f, Color.White)), null)
                repeat(FRAMES_IN_FLIGHT + 2) { renderer.presentWithoutScene() }

                // Warm ReusableStagingBuffer cache.
                renderer.graphicsDevice.stagingBufferPool.withStaging(128L * 1024L) {}

                // Warm TextureUploads staging pool.
                renderer.transferContext.runOneTimeCommands { cmd ->
                    renderer.textureUploads.beginFrame(0, cmd)
                    renderer.textureUploads.write(
                        writableTexture,
                        TextureRegion(layer = 0, x = 0, y = 0, width = 8, height = 8, data = ByteArray(8 * 8 * 4) { 64 }),
                    )
                    renderer.textureUploads.endFrame()
                }

                // Warm GpuBufferPoolManager instance pools.
                renderer.bufferPools.instanceBufferForRun(0).update(0, List(8) { Mat4() })
                renderer.bufferPools.alphaInstanceBufferForRun(0).update(0, List(8) { Vec4(1f, 1f, 1f, 1f) })
                renderer.bufferPools.frameInstanceBufferForRun(0).update(0, List(8) { 0f })
                renderer.bufferPools.whiteInstanceColorBinding

                assertTrue(renderer.transferContext.uploadBatcher.freeChunkCount > 0)
                assertTrue(renderer.graphicsDevice.stagingBufferPool.cachedBytes > 0L)
                assertTrue(renderer.textureUploads.pooledBufferCount > 0)
                assertTrue(renderer.bufferPools.liveBufferCount > 0)

                val contractRenderer: ContractRenderer = renderer
                contractRenderer.trimMemory()

                assertEquals(0, renderer.transferContext.uploadBatcher.freeChunkCount)
                assertEquals(0L, renderer.transferContext.uploadBatcher.retainedStagingBytes)
                assertEquals(0L, renderer.graphicsDevice.stagingBufferPool.cachedBytes)
                assertEquals(0, renderer.textureUploads.pooledBufferCount)
                assertEquals(0L, renderer.textureUploads.pooledBytes)
                assertEquals(0, renderer.bufferPools.liveBufferCount)
                assertEquals(0, renderer.deferredResourceReaper.pending)

                // Subsequent frames must continue rendering cleanly with existing caller-owned resources.
                renderer.drawUi(listOf(UiDrawPrimitive.Quad(0f, 0f, 16f, 16f, Color.White)), null)
                renderer.presentWithoutScene()
                assertFalse(renderer.graphicsDevice.device == 0L)
            } finally {
                writableTexture.destroy()
                mesh.destroy()
                material.destroy()
                repeat(FRAMES_IN_FLIGHT + 1) { renderer.presentWithoutScene() }
            }
        }

    @Test
    fun deviceMemorySlabAllocatorSeparatesSmallAndLargeTiersAndReclaimsEmptySlabs() {
        val device = GraphicsDevice().apply { createHeadless() }
        val transfer = TransferContext(device)
        val allocator = device.deviceMemoryAllocator
        try {
            val smallTexture = Texture(device, transfer::runOneTimeCommands, ByteArray(16 * 16 * 4) { -1 }, 16, 16)
            val slabBytesWithSmallOnly = allocator.liveSlabBytes
            assertTrue(slabBytesWithSmallOnly > 0L)

            val largePixels = ByteArray(LARGE_TEX_EDGE * LARGE_TEX_EDGE * 4) { 127 }
            val largeTextures = List(LARGE_TEX_COUNT) {
                Texture(device, transfer::runOneTimeCommands, largePixels, LARGE_TEX_EDGE, LARGE_TEX_EDGE)
            }
            val peakSlabBytes = allocator.liveSlabBytes
            assertTrue(peakSlabBytes > slabBytesWithSmallOnly, "Large streaming textures must allocate Large-tier slabs")

            // Unloading large streaming textures frees oversized Large slabs even while smallTexture remains resident.
            largeTextures.forEach { it.destroy() }
            assertTrue(
                allocator.liveSlabBytes < peakSlabBytes,
                "Destroying large transient textures must release grown Large slabs while small assets remain",
            )

            // trimEmptySlabs frees even the single warm empty Large slab, leaving only smallTexture's Small slab.
            allocator.trimEmptySlabs()
            assertEquals(
                slabBytesWithSmallOnly,
                allocator.liveSlabBytes,
                "trimEmptySlabs must reclaim all empty Large slabs without disturbing the resident Small slab",
            )

            smallTexture.destroy()
            allocator.trimEmptySlabs()
            assertEquals(0, allocator.liveSlabCount)
            assertEquals(0L, allocator.liveSlabBytes)
        } finally {
            transfer.destroy()
            device.destroy()
        }
    }

    private fun readSlotFloats(block: SlotBlock, slotIndex: Int, floatCount: Int): List<Float> {
        val bytes = VulkanBuffers.readMappedMemoryBytes(block.mappedPtr, slotIndex * block.stride, floatCount * Float.SIZE_BYTES)
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer()
        return List(floatCount) { floats[it] }
    }

    private companion object {
        const val SIZE = 64
        const val FRAMES_IN_FLIGHT = 2
        const val UNTINTED_RUN_COUNT = 25
        const val STANDARD_UBO_FLOATS = 24
        const val SKINNED_UBO_FLOATS = 1216
        const val LARGE_TEX_EDGE = 512
        const val LARGE_TEX_COUNT = 6

        val TRIANGLE_VERTICES = floatArrayOf(
            -0.5f, -0.5f, 0f, 0f, 0f, 1f, 1f, 0f, 0f,
            0.5f, -0.5f, 0f, 0f, 0f, 1f, 0f, 1f, 0f,
            0f, 0.5f, 0f, 0f, 0f, 1f, 0f, 0f, 1f,
        )
        val TRIANGLE_INDICES = intArrayOf(0, 1, 2)

        val TEXTURED_QUAD_VERTICES = floatArrayOf(
            -0.5f, -0.5f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, 0f,
            0.5f, -0.5f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 0f,
            0.5f, 0.5f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f,
            -0.5f, 0.5f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, 1f,
        )

        val PARTICLE_QUAD_VERTICES = floatArrayOf(
            -0.5f, -0.5f, 0f, 0f, 0f,
            0.5f, -0.5f, 0f, 1f, 0f,
            0.5f, 0.5f, 0f, 1f, 1f,
            -0.5f, 0.5f, 0f, 0f, 1f,
        )
        val PARTICLE_QUAD_INDICES = intArrayOf(0, 1, 2, 0, 2, 3)
        val PARTICLE_EXTRAS = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f, 0f, 0f)
    }
}
