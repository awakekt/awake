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
import com.awakekt.awake.render.effects.PostProcessingShaders
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.renderer.AntiAliasing
import com.awakekt.awake.scene.rendering.postprocessing.PostProcessing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * A renderer whose anti-aliasing is [AntiAliasing.Fxaa] draws every camera with the stack's FXAA
 * stage, whether or not the camera has a post-processing component (#1791).
 */
class FxaaAntiAliasingModeTest {
    private fun worldWithCamera(postProcessing: PostProcessing? = null): World = World().also { world ->
        val camera = world.create()
        world.add(camera, Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)))
        postProcessing?.let { world.add(camera, it) }
    }

    private fun ImagePassChain.shaderIds(): List<String> = steps.map { it.shader.id }

    private fun drawnChain(mode: AntiAliasing, world: World): ImagePassChain {
        val renderer = RenderSystemTest.RecordingRenderer().apply { antiAliasing = mode }
        RenderSystem3D(renderer).update(world, 1f / 60f)
        assertEquals(1, renderer.sceneSubmissionCount, "the scene was drawn")
        return renderer.lastPostPassChain
    }

    @Test
    fun theFxaaModeRunsFxaaOnACameraWithNoPostProcessing() {
        val chain = drawnChain(AntiAliasing.Fxaa, worldWithCamera())

        assertEquals(PostProcessingShaders.FXAA_SHADER_ID, chain.shaderIds().last())
    }

    @Test
    fun theFxaaModeRunsAfterTheCamerasOwnEffects() {
        val chain = drawnChain(AntiAliasing.Fxaa, worldWithCamera(PostProcessing(effects = listOf(BloomEffect()))))

        assertEquals(PostProcessingShaders.FXAA_SHADER_ID, chain.shaderIds().last())
        assertEquals(1, chain.shaderIds().count { it == PostProcessingShaders.FXAA_SHADER_ID })
    }

    @Test
    fun anyOtherModeDrawsTheCameraAsBefore() {
        assertSame(ImagePassChain.Empty, drawnChain(AntiAliasing.Off, worldWithCamera()))
        assertSame(ImagePassChain.Empty, drawnChain(AntiAliasing.Msaa4x, worldWithCamera()))
    }
}
