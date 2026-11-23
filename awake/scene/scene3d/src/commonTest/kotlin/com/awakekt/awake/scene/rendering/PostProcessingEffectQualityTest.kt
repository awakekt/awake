/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.effects.BloomEffect
import com.awakekt.awake.render.effects.PostProcessingStack
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.scene.rendering.postprocessing.PostProcessing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * [SceneRenderPlanner3D] forwards the renderer's [EffectQuality] to [PostProcessingStack.buildChain]
 * so [EffectQuality.Off] skips camera post-processing and [EffectQuality.Low] runs bloom at quarter resolution.
 */
class PostProcessingEffectQualityTest {
    private fun worldWithBloomCamera(): World = World().also { world ->
        val camera = world.create()
        world.add(camera, Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)))
        world.add(camera, PostProcessing(effects = listOf(BloomEffect(intensity = 0.6f, radius = 12f))))
    }

    private fun drawnChain(quality: EffectQuality): ImagePassChain {
        val renderer = RenderSystemTest.RecordingRenderer().apply { effectQuality = quality }
        RenderSystem3D(renderer).update(worldWithBloomCamera(), 1f / 60f)
        assertEquals(1, renderer.sceneSubmissionCount, "the scene was drawn")
        return renderer.lastPostPassChain
    }

    @Test
    fun effectQualityOffDisablesCameraPostProcessing() {
        assertSame(ImagePassChain.Empty, drawnChain(EffectQuality.Off))
    }

    @Test
    fun effectQualityLowUsesQuarterResolutionBloomTargets() {
        val high = drawnChain(EffectQuality.High)
        val low = drawnChain(EffectQuality.Low)
        val highBright = assertNotNull(high.transientTargets.firstOrNull { it.id == PostProcessingStack.TRANSIENT_BLOOM_BRIGHT_ID })
        val lowBright = assertNotNull(low.transientTargets.firstOrNull { it.id == PostProcessingStack.TRANSIENT_BLOOM_BRIGHT_ID })
        assertEquals(highBright.width / 2, lowBright.width, "Low bloom bright-pass width should be half of High (quarter-res)")
        assertEquals(highBright.height / 2, lowBright.height, "Low bloom bright-pass height should be half of High (quarter-res)")
    }
}
