/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An unlit draw reproduces its texel on screen through a frame with distance fog, static and
 * skinned, on both backends (#1178), while a lit draw and a transparent one over a lit ground are
 * still fogged.
 *
 * `pbr_material.unlit` promises the stored colour, skipping lighting, tone mapping and fog. The
 * shaders kept it: with the plan's content features stripped, both backends drew the texel
 * exactly. But `depthFogContentFeature` is a covering, a full-screen pass after the geometry that
 * blends fog over every pixel scene depth holds, and it painted over unlit draws too. With a pale
 * fog that reads as "a few levels brighter, more on the darker channels": each channel moves
 * toward the fog colour, and the darker ones are further from it.
 *
 * Negative control, measured before the fix on lavapipe, identical on Vulkan and WebGPU: the skin
 * texel `[221, 173, 152]` drew `[223, 189, 176]` static and skinned, the dark `[96, 64, 32]` drew
 * `[133, 109, 89]`, and the lit dark texel `[132, 107, 81]`, which this test still requires.
 */
class UnlitOverFogParityTest {

    @Test
    fun vulkanUnlitDrawsKeepTheirTexelUnderFog() = verify(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuUnlitDrawsKeepTheirTexelUnderFog() = verify(HeadlessUiBackend.WebGpu)

    /**
     * Exempting unlit draws changes nothing else the fog covers: a transparent plane over a lit
     * ground, which writes no scene depth, is still fogged by the ground's distance, as before.
     * Compared with the same plan's frame without its fog.
     */
    @Test
    fun aTransparentDrawIsStillFoggedOnBothBackends() {
        for (backend in BACKEND_ORDER) {
            val fogged = openUnlitOverFogSession(backend).use { unlitSceneCentreRgb(it.renderer.renderTransparentOverFogScene()) }
            val clear = openUnlitOverFogSession(backend, plan = UnlitOverFogPlan.copy(contentFeatures = emptyList())).use {
                unlitSceneCentreRgb(it.renderer.renderTransparentOverFogScene())
            }
            assertTrue(
                fogged[BLUE] >= clear[BLUE] + MIN_TRANSPARENT_FOG_LIFT,
                "$backend: the transparent plane is no longer fogged: ${fogged.toList()} with fog, ${clear.toList()} without",
            )
        }
    }

    private fun verify(backend: HeadlessUiBackend) {
        val measured = mutableListOf<String>()
        var failed = false
        openUnlitOverFogSession(backend).use { session ->
            val renderer = session.renderer
            for ((texel, skinned) in CASES) {
                val rgb = unlitSceneCentreRgb(renderer.renderUnlitOverFogScene(texel, skinned, unlit = true))
                measured += "${if (skinned) "skinned" else "static"} ${texel.toList()} -> ${rgb.toList()}"
                if ((0..2).any { abs(rgb[it] - texel[it]) > TOLERANCE }) failed = true
            }
            // The fog still runs over lit draws: the dark texel's blue rises toward the haze's.
            val lit = unlitSceneCentreRgb(renderer.renderUnlitOverFogScene(DARK, skinned = false, unlit = false))
            measured += "lit static ${DARK.toList()} -> ${lit.toList()}"
            assertTrue(lit[BLUE] >= DARK[BLUE] + MIN_FOG_LIFT, "$backend: fog no longer covers a lit draw: ${measured.joinToString()}")
        }
        assertTrue(!failed, "$backend: an unlit draw left its texel by more than $TOLERANCE: ${measured.joinToString()}")
    }

    private companion object {
        const val TOLERANCE = 2
        const val BLUE = 2

        /** Lit, the dark texel's blue is near 20 before fog; the haze at about 28% lifts it past 80. */
        const val MIN_FOG_LIFT = 30

        /** The haze lifts the transparent pair's blue by about a quarter of its distance to 242. */
        const val MIN_TRANSPARENT_FOG_LIFT = 15
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        val SKIN = intArrayOf(0xDD, 0xAD, 0x98)
        val DARK = intArrayOf(0x60, 0x40, 0x20)
        val CASES = listOf(SKIN, DARK).flatMap { texel -> listOf(texel to false, texel to true) }
    }
}
