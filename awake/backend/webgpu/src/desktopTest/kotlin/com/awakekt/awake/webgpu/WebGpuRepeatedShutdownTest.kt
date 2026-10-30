/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.pipeline.MultisampleState
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Boots the complete showcase plan at 4x and shuts it down again, [BOOTS] times in one process, and
 * asks wgpu-native after each shutdown what its instance still holds. A full teardown leaves
 * nothing: any device, queue, texture, view, bind group or pipeline still registered keeps its
 * device's native memory alive, which is how repeated boots used to exhaust device requests.
 *
 * Measured on wgpu-native over lavapipe: all 12 shutdowns report no live objects. Before this fix
 * the first shutdown left 35 render pipelines, 35 pipeline layouts, 74 bind group layouts, 15 shader
 * modules, 37 texture views, 7 bind groups, 3 buffers, 3 samplers and 3 command buffers. Negative
 * control: skipping the queue release leaves `queues=2` after the first shutdown, the timing
 * upgrade's discarded queue and the engine's own, each keeping its native device, and fails there.
 */
class WebGpuRepeatedShutdownTest {
    @Test
    fun repeatedFullPlanBootsReleaseEveryNativeObject() = runBlocking {
        repeat(BOOTS) { boot ->
            val engine = HeadlessWebGpuEngine(plan = EngineShowcaseRenderPlan.copy(multisample = MultisampleState(SAMPLES)))
            val leftover = try {
                drawFrame(engine.boot(HeadlessSurface(SIZE, SIZE)))
                engine.destroyAndReport()
            } catch (failure: Throwable) {
                runCatching { engine.destroy() }
                throw failure
            }
            println("Boot ${boot + 1}: live objects after shutdown $leftover, ${residentKilobytes()} kB resident")
            assertEquals(emptyMap(), leftover, "Boot ${boot + 1} left native objects alive after shutdown")
        }
    }

    /** A lit scene into a 4x target, a UI layer over it, and a readback: every kind of object a frame creates. */
    private suspend fun drawFrame(renderer: Renderer) {
        val scene = renderer.createRenderTarget(SIZE, SIZE)
        val layer = renderer.createRenderTarget(SIZE, SIZE)
        val mesh = renderer.createMesh(generate { cube(size = 2f) })
        val material = renderer.createMaterial(LitShadowUniformLayout)
        val composite = renderer.createMaterial(renderTarget = scene)
        try {
            val camera = Lens(eye = Vec3f(2f, 2f, 4f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 20f)
            val light = SceneLight(direction = Vec3f(-0.4f, -1f, -0.3f), color = Vec3f(1f, 1f, 1f))
            renderer.renderSceneToTexture(scene, camera, listOf(RenderDrawCommand(mesh, material)), light)
            renderer.drawUiToTexture(layer, listOf(UiDrawPrimitive.Quad(0f, 0f, SIZE / 2f, SIZE / 2f, Color(1f, 0f, 0f, 1f))))
            val pixels = renderer.readPixels(scene).data
            assertTrue(pixels.any { it.toInt() != 0 }, "The scene must draw before shutdown")
        } finally {
            renderer.waitIdle()
            composite.destroy()
            material.destroy()
            mesh.destroy()
            layer.destroy()
            scene.destroy()
        }
    }

    /** This process's resident set from procfs where there is one, for the log only. */
    private fun residentKilobytes(): String = runCatching {
        java.io.File("/proc/self/status").readLines().first { it.startsWith("VmRSS:") }.filter(Char::isDigit)
    }.getOrDefault("unknown")

    private companion object {
        const val BOOTS = 12
        const val SIZE = 64
        const val SAMPLES = 4
    }
}
