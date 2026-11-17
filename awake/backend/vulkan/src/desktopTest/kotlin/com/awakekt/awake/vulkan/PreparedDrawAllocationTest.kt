/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.vulkan.renderer.Renderer
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What one more moving, shadowed draw adds to a frame's garbage on Vulkan (#1742): the slope between
 * a frame of [FEW] draws and one of [MANY], so the frame's fixed cost drops out.
 *
 * `prepareGpuDraw` built a `PreparedDrawCall` for every draw, only for `VulkanDrawPreparer` to copy
 * it into the draw's `GpuResolvedDraw` and drop it: one object per draw per frame, and the largest
 * Vulkan-backend share of a populated frame's garbage. The preparer now fills one in place.
 */
class PreparedDrawAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    private val camera = Lens(eye = Vec3f(4f, 8f, 12f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)

    @Test
    fun anotherDrawAddsOnlyItsResolvedDrawAndItsCompileShare() {
        val session = vulkanHeadlessScene(SIZE, SIZE)
        val renderer = session.renderer as Renderer
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val material = renderer.createMaterial(LitShadowUniformLayout)
        val meshes = List(MESHES) { renderer.createMesh(generate { cube(size = 0.2f + it * 0.01f) }) }
        try {
            val base = SceneLight(direction = Vec3f(1f, 2f, 0.5f), color = Vec3f(1f, 1f, 1f))
            val light = base.copy(cascades = shadowCascadeUniforms(base, camera, 1f, renderer.clipSpace))
            fun frames(count: Int) = List(2) { shift ->
                List(count) { i ->
                    val model = Mat4().apply { data[12] = (i % 25 - 12) * 0.5f + shift * 0.05f; data[14] = (i / 25 - 10) * 0.5f }
                    RenderDrawCommand(meshes[i % MESHES], material, model)
                }
            }
            val few = perFrame(renderer, target, frames(FEW), light)
            val many = perFrame(renderer, target, frames(MANY), light)
            val perDraw = (many - few) / (MANY - FEW)
            println("PreparedDrawAllocation: $FEW draws $few B a frame, $MANY draws $many B a frame, $perDraw B for each further draw")
            assertTrue(perDraw < PER_DRAW_BUDGET, "each further draw allocated $perDraw B a frame")
        } finally {
            meshes.forEach { it.destroy() }
            material.destroy()
            target.destroy()
            session.close()
        }
    }

    /** Bytes allocated by a frame of these draws, alternating between their two positions so they move. */
    private fun perFrame(renderer: Renderer, target: RenderTarget, frames: List<List<RenderDrawCommand>>, light: SceneLight): Long {
        repeat(WARM_UP) { renderer.renderSceneToTexture(target, camera, frames[it % 2], light) }
        val before = threads.currentThreadAllocatedBytes
        repeat(FRAMES) { renderer.renderSceneToTexture(target, camera, frames[it % 2], light) }
        return (threads.currentThreadAllocatedBytes - before) / FRAMES
    }

    private companion object {
        const val SIZE = 64
        const val MESHES = 50
        const val FEW = 250
        const val MANY = 500
        const val WARM_UP = 60
        const val FRAMES = 200

        /**
         * 463 B a further draw while each built a `PreparedDrawCall`, 375 B since. What remains is
         * the draw's `GpuResolvedDraw`, which the pass keeps, and its share of compiling the pass.
         */
        const val PER_DRAW_BUDGET = 420L
    }
}
