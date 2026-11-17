/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.ShadowBudget
import com.awakekt.awake.render.renderer.ShadowDepthFormat
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.webgpu.renderer.Renderer
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WebGPU honours [ShadowDepthFormat.Depth16Unorm] as Vulkan does since #1703 (#1709): the shadow array
 * is built in `depth16unorm` at half the bytes, every caster pipeline and render bundle declares the
 * same attachment, and the picture at a 1024 map matches the 32-bit one.
 *
 * The acne and bias check: one scene, a box on a plane under a low sun, drawn at a 1024 map once per
 * format. 16-bit depth resolves light-space depth in 1/65536 steps, so the rasterizer bias's constant
 * term is coarser; acne would show as lit ground the 32-bit picture leaves lit and the 16-bit one
 * darkens, and a bias too large as a shadow that lifts off its caster. Both show as pixels that
 * differ between the two pictures. A phone GPU is still to be run (#1709); this is lavapipe.
 */
class WebGpuShadowDepth16Test {
    @Test
    fun aSixteenBitShadowArrayHalvesItsBytesAndDrawsTheThirtyTwoBitPicture() {
        val deep = render(ShadowDepthFormat.Depth32Float)
        val shallow = render(ShadowDepthFormat.Depth16Unorm)

        assertEquals(ShadowDepthFormat.Depth32Float, deep.format)
        assertEquals(ShadowDepthFormat.Depth16Unorm, shallow.format, "the renderer reports the format it was built in")
        assertEquals(deep.bytes / 2, shallow.bytes, "16-bit depth halves the shadow array")
        assertEquals(emptyList(), shallow.errors, "every pipeline drawing into the 16-bit array declares its format")

        val shadowed32 = darkened(deep.lit, deep.unlit)
        val shadowed16 = darkened(shallow.lit, shallow.unlit)
        val differing = differing(deep.lit, shallow.lit)
        val pixels = SIZE * SIZE
        println(
            "WebGpuShadowDepth16: shadow bytes 32-bit ${deep.bytes} 16-bit ${shallow.bytes}; " +
                "shadowed pixels 32-bit $shadowed32 16-bit $shadowed16 of $pixels; differing $differing",
        )
        assertTrue(shadowed32 > pixels / MIN_SHADOW_SHARE, "the box casts a shadow worth comparing: $shadowed32 px")
        assertTrue(
            differing <= MAX_DIFFERING,
            "16-bit depth changed $differing of $pixels pixels against 32-bit: acne or a lifted shadow",
        )
        assertTrue(abs(shadowed16 - shadowed32) <= MAX_DIFFERING, "the shadow covers the same ground")
    }

    private class Run(val format: ShadowDepthFormat, val bytes: Long, val lit: ByteArray, val unlit: ByteArray, val errors: List<String>)

    private fun render(format: ShadowDepthFormat): Run = webGpuHeadlessScene(format).use { session ->
        val renderer = session.renderer as Renderer
        renderer.shadowBudget = ShadowBudget(cascades = ShadowBudget.DEFAULT_CASCADES, mapSize = MAP_SIZE)
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val material = renderer.createMaterial(LitShadowUniformLayout)
        val ground = renderer.createMesh(generate { plane(size = 20f, colored = false) })
        val box = renderer.createMesh(generate { cube(size = 2f, colored = false) })
        try {
            val draws = listOf(RenderDrawCommand(ground, material), RenderDrawCommand(box, material, Mat4().translate(0f, 1f, 0f)))
            val lit = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
            repeat(FRAMES) { renderer.renderSceneToTexture(target, lens, draws, lit) }
            val litPixels = runBlocking { renderer.readPixels(target) }.data
            val bytes = renderer.shadowMapBytes
            renderer.renderSceneToTexture(target, lens, draws, sun)
            val unlitPixels = runBlocking { renderer.readPixels(target) }.data
            Run(renderer.shadowDepthFormat, bytes, litPixels, unlitPixels, WebGpuUncapturedErrors.drain())
        } finally {
            box.destroy()
            ground.destroy()
            material.destroy()
            target.destroy()
        }
    }

    /** Pixels the shadow darkens: their green channel falls by more than [THRESHOLD]. */
    private fun darkened(lit: ByteArray, unlit: ByteArray): Int =
        (0 until SIZE * SIZE).count { pixel -> channel(unlit, pixel) - channel(lit, pixel) > THRESHOLD }

    private fun differing(a: ByteArray, b: ByteArray): Int =
        (0 until SIZE * SIZE).count { pixel -> abs(channel(a, pixel) - channel(b, pixel)) > THRESHOLD }

    private fun channel(pixels: ByteArray, pixel: Int): Int = pixels[pixel * 4 + 1].toInt() and 0xFF

    private companion object {
        const val SIZE = 256
        const val MAP_SIZE = 1024
        const val FRAMES = 3
        const val THRESHOLD = 8
        const val MIN_SHADOW_SHARE = 50

        /** Pixels the two pictures may differ by. Both draw the same picture here (0); with the
         * rasterizer bias taken off the 16-bit pipelines, 52 pixels of acne. */
        const val MAX_DIFFERING = 16
        val sun = SceneLight(direction = Vec3f(-1f, 0.18f, 0.3f), color = Vec3f(1f, 1f, 1f))
        val lens = Lens(eye = Vec3f(0f, 7f, 9f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)
    }
}
