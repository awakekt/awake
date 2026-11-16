/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.renderer.uploadTexture
import com.awakekt.awake.vulkan.texture.Texture
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Renderer.maxAnisotropy` on Vulkan (#1660), through a content pipeline whose texture is uploaded
 * and registered as a real one is. Its probe samples 8-texel stripes with a footprint 8 times longer
 * along one axis than the other, through `textureSample` with implicit derivatives: lavapipe filters
 * that anisotropically, and ignores anisotropy for `textureSampleGrad` with explicit ones (checked on
 * this lavapipe with a standalone Vulkan probe), so the PBR textured shader's change shows only on a
 * real GPU -- recorded on #1660.
 *
 * The device fails on any validation error when it is destroyed: a sampler destroyed while bound,
 * destroyed twice, or never destroyed among them.
 */
class RendererAnisotropyTest {
    @Test
    fun aChangedAnisotropyRemakesTheSamplerAndTheContentSetTakesItBeforeDrawing() = withProbeRenderer(scale = 1f to 8f) { renderer, probe ->
        assertTrue(renderer.maxSupportedAnisotropy >= SAMPLES, "this device offers samplerAnisotropy up to ${renderer.maxSupportedAnisotropy}")
        val texture = renderer.uploadTexture(stripes()).also { probe.writeContentTextures(mapOf(1 to it)) }
        val off = renderer.frame()
        assertEquals(1, texture.samplerAnisotropy)

        // A content feature's own texture, created as VulkanContentFeatureGpu does, outside the renderer's registry.
        val own = { Texture(renderer.graphicsDevice, renderer.transferContext::runOneTimeCommands, stripes().data, TEXELS, TEXELS) }
        val before = own()
        renderer.maxAnisotropy = SAMPLES
        assertEquals(SAMPLES, texture.samplerAnisotropy, "the texture's sampler was made again at $SAMPLES")
        assertEquals(SAMPLES, before.samplerAnisotropy, "a content feature's texture took it through the device")
        own().also { assertEquals(SAMPLES, it.samplerAnisotropy, "one created later starts at it") }.destroy()
        before.destroy()
        val on = renderer.frame()
        assertTrue(probe.contentSamplerRewrites >= 1, "the content set took the new sampler before it drew")
        val changed = off.indices.step(4).count { kotlin.math.abs((off[it].toInt() and 0xFF) - (on[it].toInt() and 0xFF)) > 2 }
        println("RendererAnisotropy: $changed of ${SIZE * SIZE} pixels changed between 1 and $SAMPLES samples")
        assertTrue(changed > SIZE * SIZE / 2, "stripes resolve along the long axis at $SAMPLES samples: $changed changed")

        renderer.maxAnisotropy = 1
        assertContentEquals(off, renderer.frame(), "back at 1, the frame is the first one again")
    }

    @Test
    fun theSameValueAgainCreatesNothingAndAPendingSamplerOutlivesItsTexture() = withProbeRenderer(scale = 1f to 8f) { renderer, probe ->
        val texture = renderer.uploadTexture(stripes()).also { probe.writeContentTextures(mapOf(1 to it)) }
        renderer.maxAnisotropy = SAMPLES
        val sampler = texture.sampler.handle
        val pending = renderer.retiredSamplers.pending
        renderer.maxAnisotropy = SAMPLES
        renderer.maxAnisotropy = SAMPLES * 4
        renderer.maxAnisotropy = renderer.maxSupportedAnisotropy

        assertEquals(1, pending, "the replaced 1x sampler waits for the frames in flight")
        if (renderer.maxSupportedAnisotropy == SAMPLES) assertEquals(sampler, texture.sampler.handle, "the same value made nothing")
        // The texture goes while its replaced sampler is still waiting: each is destroyed once, by
        // its owner, and the device reports nothing left behind when it is destroyed.
        renderer.textureResources.release(texture)
        assertTrue(renderer.retiredSamplers.pending >= 1, "the retired sampler is still the queue's to destroy")
    }

    /**
     * What a frame with no change pays for the check: a material slot and a content slot are looked
     * up, as each draw binds them, without allocating -- one handle compared per slot.
     */
    @Test
    fun anUnchangedSettingAllocatesNothingWhereASlotIsBound() = withProbeRenderer(scale = 1f to 8f) { renderer, probe ->
        val texture = renderer.uploadTexture(stripes()).also { probe.writeContentTextures(mapOf(1 to it)) }
        renderer.maxAnisotropy = SAMPLES
        val material = renderer.createMaterial(texture = stripes()) as Material
        val block = checkNotNull(probe.uniformBlock)
        renderer.frame()
        // The offscreen frame's own slot, as renderToTexture binds them.
        fun bindBoth() {
            material.uniformSlot(FRAMES_IN_FLIGHT, 0)
            block.binding(FRAMES_IN_FLIGHT)
        }
        repeat(WARM_UP) { bindBoth() }
        val threads = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        // The quietest of several windows, as the other allocation gates read theirs: a JIT event can
        // land a few hundred bytes in one window, a per-bind allocation fills every window.
        val windows = LongArray(WINDOWS) {
            val before = threads.currentThreadAllocatedBytes
            repeat(BINDS) { bindBoth() }
            threads.currentThreadAllocatedBytes - before
        }
        println("RendererAnisotropy: ${windows.joinToString()} bytes per $BINDS unchanged binds of a material and a content slot")
        assertEquals(0L, windows.min(), "an unchanged sampler is one comparison per bind")
        assertEquals(SAMPLES, texture.samplerAnisotropy)
        material.destroy()
    }

    private companion object {
        const val SIZE = PROBE_SIZE
        const val SAMPLES = 8
        const val FRAMES_IN_FLIGHT = PROBE_FRAMES_IN_FLIGHT
        const val TEXELS = 256
        const val STRIPE = 8
        const val WARM_UP = 2_000
        const val BINDS = 10_000
        const val WINDOWS = 5

        fun stripes() = TextureAsset(
            data = ByteArray(TEXELS * TEXELS * 4) { index -> if (index % 4 == 3 || ((index / 4) % TEXELS / STRIPE) % 2 == 0) -1 else 0 },
            width = TEXELS,
            height = TEXELS,
        )
    }
}
