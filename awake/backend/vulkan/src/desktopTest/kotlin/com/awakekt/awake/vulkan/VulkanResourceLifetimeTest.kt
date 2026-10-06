/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.DEFAULT_SCENE_LIGHT
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import kotlin.test.Test

/**
 * A mesh or material freed while a submitted frame still draws with it is undefined behaviour, and
 * the validation layer says so (`VUID-vkDestroyBuffer-buffer-00922`). A headless device fails on
 * any validation error when it is destroyed, so closing the session is what makes this test fail.
 *
 * It submits without waiting ([Renderer.submitToTexture]) and frees what the frame draws at once,
 * as a scene swap does: an editor reloading a scene file destroys the old scene's meshes and
 * materials right after the last frames were submitted.
 */
class VulkanResourceLifetimeTest {
    @Test
    fun freeingMeshesAndMaterialsRightAfterSubmittedFramesLeavesNothingInUse() {
        vulkanHeadlessScene(SIZE, SIZE).use { session ->
            val renderer = session.renderer
            val target = renderer.createRenderTarget(SIZE, SIZE)
            repeat(ROUNDS) {
                val mesh = renderer.createMesh(PLANE)
                val material = renderer.createMaterial(LitShadowUniformLayout)
                renderer.submitToTexture(target, renderer.compile(listOf(RenderDrawCommand(mesh, material))))
                // The frame is submitted, not finished: the GPU may still read both.
                mesh.destroy()
                material.destroy()
            }
            target.destroy()
        }
    }

    private fun Renderer.compile(drawCalls: List<RenderDrawCommand>) = ScenePassCompiler.compile(
        lens = Lens(eye = Vec3f(0f, 4f, 6f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f),
        drawCalls = drawCalls,
        light = DEFAULT_SCENE_LIGHT,
        environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false),
        clipSpace = clipSpace,
        aspect = 1f,
        drawPreparer = requireNotNull((this as? GpuDrawPreparationSource)?.gpuDrawPreparer),
    )

    private companion object {
        const val SIZE = 64
        const val ROUNDS = 12
        const val HALF = 2f

        /** A ground plane: position, normal and colour, nine floats a vertex. */
        val PLANE = MeshGeometry(
            floatArrayOf(
                -HALF, 0f, -HALF, 0f, 1f, 0f, 1f, 1f, 1f,
                HALF, 0f, -HALF, 0f, 1f, 0f, 1f, 1f, 1f,
                HALF, 0f, HALF, 0f, 1f, 0f, 1f, 1f, 1f,
                -HALF, 0f, HALF, 0f, 1f, 0f, 1f, 1f, 1f,
            ),
            intArrayOf(0, 1, 2, 2, 3, 0),
            VertexFormat.PositionNormalColor,
        )
    }
}
