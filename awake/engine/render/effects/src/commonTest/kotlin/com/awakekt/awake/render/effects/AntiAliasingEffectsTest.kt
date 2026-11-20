/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.renderer.AntiAliasing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The [AntiAliasing.Fxaa] setting adds the stack's FXAA stage to a camera that has none, and leaves
 * every other camera's effects alone (#1791).
 */
class AntiAliasingEffectsTest {
    private val bloom = BloomEffect()

    @Test
    fun theFxaaModeAddsAStageAtTheRenderersQuality() {
        assertEquals(listOf(FxaaEffect(EffectQuality.Medium)), effectsForAntiAliasing(emptyList(), AntiAliasing.Fxaa, EffectQuality.Medium))
        assertEquals(listOf(bloom, FxaaEffect(EffectQuality.High)), effectsForAntiAliasing(listOf(bloom), AntiAliasing.Fxaa, EffectQuality.High))
    }

    @Test
    fun aStageTheSceneDisabledDoesNotCountAsOne() {
        val disabled = listOf(FxaaEffect(enabled = false))
        val off = listOf(FxaaEffect(EffectQuality.Off))

        assertEquals(disabled + FxaaEffect(EffectQuality.High), effectsForAntiAliasing(disabled, AntiAliasing.Fxaa, EffectQuality.High))
        assertEquals(off + FxaaEffect(EffectQuality.High), effectsForAntiAliasing(off, AntiAliasing.Fxaa, EffectQuality.High))
    }

    @Test
    fun aStageTheSceneListsIsKeptAsListed() {
        val listed = listOf(FxaaEffect(EffectQuality.Low), bloom)

        assertSame(listed, effectsForAntiAliasing(listed, AntiAliasing.Fxaa, EffectQuality.High))
    }

    @Test
    fun effectQualityOffStillRunsTheFxaaTheAppAskedFor() {
        assertEquals(listOf(FxaaEffect(EffectQuality.Low)), effectsForAntiAliasing(emptyList(), AntiAliasing.Fxaa, EffectQuality.Off))
    }

    @Test
    fun everyOtherModeLeavesTheEffectsAsTheyAre() {
        val listed = listOf(bloom)
        val others = AntiAliasing.entries - AntiAliasing.Fxaa

        others.forEach { mode -> assertSame(listed, effectsForAntiAliasing(listed, mode, EffectQuality.High), "$mode") }
    }

    @Test
    fun eachLowerTierNeedsMoreContrastBeforeItFilters() {
        val thresholds = listOf(EffectQuality.Low, EffectQuality.Medium, EffectQuality.High)
            .map { PostProcessingShaders.fxaaEdgeThreshold(it.ordinal) }

        assertEquals(listOf(0.25f, 0.166f, 0.125f), thresholds)
        val packed = PostProcessingShaders.packFxaaUniforms(width = 4, height = 4, qualityTier = EffectQuality.Low.ordinal)
        assertEquals(0.25f, packed[2], "the shader reads the tier's threshold from fxaaParams.z")
    }
}
