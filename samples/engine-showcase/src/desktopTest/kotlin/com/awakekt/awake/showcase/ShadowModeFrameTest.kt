/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.engine.bootstrap.dsl.appSpec
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.RenderSystem
import com.awakekt.awake.scene.rendering.debug.debugSettings
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.attachRenderableComponents
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.vulkan.renderer.readPresentedPixels
import kotlinx.coroutines.runBlocking
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue
import com.awakekt.awake.vulkan.renderer.Renderer as VulkanRenderer

/**
 * The three shadow modes have to look like three different things.
 *
 * A debug toggle that changes a flag nobody reads looks exactly like a toggle that works, and
 * the only way to tell is to render both and compare. So this renders the cascaded-shadows
 * showcase -- the one with casters at 6m, -6m, -26m and -60m, chosen because the fixed box
 * covers a volume at the origin and therefore CANNOT shadow the far ones -- once per mode:
 *
 * - no shadow at all: nothing on the ground is dark
 * - the single fixed box: the near caster shadows, the far ones do not
 * - cascades: every caster shadows
 *
 * The middle case is what cascades were built to replace, and this is the only test that shows
 * the difference rather than asserting cascades exist.
 *
 * IGNORED: it fails, and the numbers are the point. Measured none=37, single=42, cascaded=38 --
 * the three modes darken almost the same amount, which is the report that the cascade toggle
 * "does nothing" on screen.
 *
 * One cause was already found and fixed from these numbers: shadows OFF used to render the scene
 * DARKER than shadows on (brightest 104 against 144), because a primary draw with no cascades
 * was handed `mvp + light.directional` rather than the full lit-shadow block. All three modes
 * now peak at 144.
 *
 * What is left is the comparison itself. This frame is 320x180 and the ground covers about 9572
 * of its 57600 pixels, so a caster's shadow is worth a handful of pixels against a floor of 37
 * that are dark for other reasons -- silhouette edges, and cube faces turned from the light. A
 * closer camera or a larger frame is needed before the modes can be told apart at all.
 */
class ShadowModeFrameTest {

    @Test
    @Ignore("Fails: the three modes darken almost the same amount. See the KDoc for the numbers.")
    fun eachShadowModeRendersADifferentFrame() = runBlocking {
        val engine = ModeEngine(headlessLifecycle(), EngineShowcaseRenderPlan)
        val renderer = engine.boot(HeadlessSurface(WIDTH, HEIGHT))
        try {
            val none = renderer.shadowedGroundPixels(shadows = false, cascaded = false)
            val single = renderer.shadowedGroundPixels(shadows = true, cascaded = false)
            val cascaded = renderer.shadowedGroundPixels(shadows = true, cascaded = true)

            assertTrue(
                single > none * SHADOW_MARGIN && cascaded > single * SHADOW_MARGIN,
                "Dark ground pixels by mode: none=$none single=$single cascaded=$cascaded. Each " +
                    "mode should darken measurably more than the one before it -- the fixed box " +
                    "reaches this scene's near caster, and cascades reach the three past it.",
            )
        } finally {
            renderer.destroy()
        }
    }

    /** Ground pixels darker than a lit surface, after ten frames in the given mode. */
    private suspend fun Renderer.shadowedGroundPixels(shadows: Boolean, cascaded: Boolean): Int {
        val scene = SceneLoader.instantiate(SceneLoader.loadFromResource(SCENE))
        val ground = createMesh(generate { plane(size = GROUND_MESH_SIZE) })
        val cube = createMesh(generate { cube(size = 1f, colored = true) })
        val material = createMaterial(uniformFloatCount = LitShadowUniformLayout.total)
        try {
            scene.attachRenderableComponents { request ->
                MeshRenderer(if (request.meshRenderer.mesh == GROUND_MESH) ground else cube, material)
            }
            scene.world.debugSettings().cascadedShadows = cascaded
            shadowsEnabled = shadows
            val system = RenderSystem(this)
            repeat(FRAMES) { system.update(scene.world, FRAME_DELTA) }
            return (this as VulkanRenderer).readPresentedPixels().data.darkGroundPixels()
        } finally {
            ground.destroy()
            cube.destroy()
            material.destroy()
        }
    }

    /**
     * Ground darker than lit, brighter than the cleared sky.
     *
     * Between two cutoffs rather than below one: this scene's background is black, and counting
     * everything below the shadow cutoff counts the sky, which swamps the comparison.
     */
    private fun ByteArray.darkGroundPixels(): Int {
        var dark = 0
        for (index in (HEIGHT / 2) * WIDTH until WIDTH * HEIGHT) {
            val red = this[index * 4].toInt() and 0xFF
            if (red in BACKGROUND_CUTOFF until SHADOW_CUTOFF) dark++
        }
        return dark
    }

    private fun headlessLifecycle(): AwakeAppLifecycle = appSpec {
        window {
            title = "shadow-modes"
            size(WIDTH, HEIGHT)
        }
    }.createLifecycle()

    private class ModeEngine(lifecycle: AwakeAppLifecycle, plan: RenderPlan) : VulkanEngine(lifecycle, plan) {
        suspend fun boot(surface: HeadlessSurface): Renderer = createBackendResources(surface).renderer
    }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 180
        const val SCENE = "assets/examples/cascaded-shadows.scene.json"
        const val GROUND_MESH = "ground"
        const val GROUND_MESH_SIZE = 10f
        const val SHADOW_CUTOFF = 90
        const val BACKGROUND_CUTOFF = 20

        /** How much more ground each mode has to darken than the one before it. */
        const val SHADOW_MARGIN = 2
        const val FRAMES = 10
        const val FRAME_DELTA = 1f / 60f
    }
}
