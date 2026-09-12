/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.material.Material as RenderMaterial
import com.awakekt.awake.render.mesh.Mesh as RenderMesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.pointShadowMatrices
import com.awakekt.awake.render.passes.uniforms.PointLight
import com.awakekt.awake.render.passes.uniforms.PointShadowLight
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.MAX_SHADOW_CASCADES
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.RenderTarget
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WebGPU's point-light shadow contact probe on a real device.
 *
 * Sibling to Vulkan's `RendererHeadlessCascadedShadowTest.aPointLightShadowStaysUnderRestingCaster`.
 * Proves that point-light shadows render through WebGPU with 6 cube faces and maintain proper
 * contact under a resting caster without detachment (peter-panning).
 */
class WebGpuPointLightShadowTest {

    @Test
    fun aPointLightShadowStaysUnderRestingCaster() {
        webGpuHeadlessScene().use { session ->
            val renderer = session.renderer
            val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
            try {
                val shadowed = renderer.renderPointRestingCaster(target, shadowed = true)
                val unshadowed = renderer.renderPointRestingCaster(target, shadowed = false)
                val gap = pointShadowGapFromFootprint(shadowed, unshadowed)
                assertTrue(
                    gap != null,
                    "The point light did not shadow any ground outside the caster footprint, so there " +
                        "is no contact gap to measure.",
                )
                assertTrue(
                    gap < MAX_POINT_CONTACT_GAP,
                    "The nearest point-shadowed ground is ${gap}m from the caster's footprint, past " +
                        "the ${MAX_POINT_CONTACT_GAP}m allowance. The point receiver bias is pulling " +
                        "the shadow away from its caster.",
                )
            } finally {
                target.destroy()
            }
        }
    }

    private fun Renderer.renderPointRestingCaster(target: RenderTarget, shadowed: Boolean): ByteArray {
        var groundMesh: RenderMesh? = null
        var casterMesh: RenderMesh? = null
        var material: RenderMaterial? = null
        try {
            val ground = createMesh(centredPlane(CONTACT_GROUND_HALF, y = 0f)).also { groundMesh = it }
            val caster = createMesh(generate { cube(size = CONTACT_HALF * 2f) }).also { casterMesh = it }
            val shared = createMaterial(LitShadowUniformLayout).also { material = it }
            val camera = topDownCamera(eyeHeight = POINT_CONTACT_EYE_HEIGHT, viewDistance = POINT_VIEW_DISTANCE).apply {
                projection = Lens.Projection.Orthographic
                orthoHalfHeight = POINT_CONTACT_VIEW_HALF
            }
            renderSceneToTexture(
                target,
                camera,
                listOf(
                    RenderDrawCommand(ground, shared),
                    RenderDrawCommand(caster, shared, Mat4().translate(0f, CONTACT_HALF, 0f)),
                ),
                pointCasterSceneLight(shadowed, clipSpace),
            )
            return runBlocking { readPixels(target) }.data
        } finally {
            casterMesh?.destroy()
            groundMesh?.destroy()
            material?.destroy()
        }
    }

    private fun pointCasterSceneLight(shadowed: Boolean, clipSpace: ClipSpace): SceneLight {
        val pointPosition = Vec3f(2f, 1.5f, 0f)
        val pointRange = 8f
        val pointBaseLayer = if (shadowed) MAX_SHADOW_CASCADES else -1
        return SceneLight(
            direction = Vec3f.ZERO,
            color = Vec3f.ZERO,
            points = listOf(
                PointLight(
                    position = pointPosition,
                    color = Vec3f(1f, 1f, 1f),
                    range = pointRange,
                    shadowBaseLayer = pointBaseLayer,
                ),
            ),
            pointShadows = if (shadowed) {
                listOf(
                    PointShadowLight(
                        baseLayer = pointBaseLayer,
                        viewProjections = pointShadowMatrices(
                            position = pointPosition,
                            range = pointRange,
                            clipSpace = clipSpace,
                        ).viewProjections,
                    ),
                )
            } else {
                emptyList()
            },
        )
    }

    private fun centredPlane(half: Float, y: Float) = MeshGeometry(
        floatArrayOf(
            -half, y, -half, 0f, 1f, 0f, 1f, 1f, 1f,
            half, y, -half, 0f, 1f, 0f, 1f, 1f, 1f,
            half, y, half, 0f, 1f, 0f, 1f, 1f, 1f,
            -half, y, half, 0f, 1f, 0f, 1f, 1f, 1f,
        ),
        intArrayOf(0, 1, 2, 2, 3, 0),
        VertexFormat.PositionNormalColor,
    )

    private fun topDownCamera(eyeHeight: Float, viewDistance: Float) = Lens(
        eye = Vec3f(0f, eyeHeight, 0.001f),
        center = Vec3f(0f, 0f, 0f),
        fovYRadians = 1f,
        near = 0.1f,
        far = viewDistance,
    )

    private fun pointShadowGapFromFootprint(shadowed: ByteArray, unshadowed: ByteArray): Float? {
        val metresPerPixel = 2f * POINT_CONTACT_VIEW_HALF / TARGET_SIZE
        var nearest: Float? = null
        for (y in 0 until TARGET_SIZE) {
            for (x in 0 until TARGET_SIZE) {
                val distance = pixelFootprintShadowDistance(x, y, metresPerPixel, shadowed, unshadowed)
                if (distance != null && (nearest == null || distance < nearest)) {
                    nearest = distance
                }
            }
        }
        return nearest
    }

    private fun pixelFootprintShadowDistance(
        x: Int,
        y: Int,
        metresPerPixel: Float,
        shadowed: ByteArray,
        unshadowed: ByteArray,
    ): Float? {
        val worldX = (x - TARGET_SIZE / 2f) * metresPerPixel
        val worldZ = (y - TARGET_SIZE / 2f) * metresPerPixel
        val dx = maxOf(0f, kotlin.math.abs(worldX) - CONTACT_HALF)
        val dz = maxOf(0f, kotlin.math.abs(worldZ) - CONTACT_HALF)
        if (dx == 0f && dz == 0f) return null

        val offset = (y * TARGET_SIZE + x) * BYTES_PER_PIXEL
        val shadowDelta = (unshadowed[offset].toInt() and 0xFF) - (shadowed[offset].toInt() and 0xFF)
        return if (shadowDelta >= POINT_SHADOW_DELTA) {
            kotlin.math.sqrt(dx * dx + dz * dz)
        } else {
            null
        }
    }

    private companion object {
        const val TARGET_SIZE = 128
        const val BYTES_PER_PIXEL = 4
        const val CONTACT_HALF = 1f
        const val CONTACT_GROUND_HALF = 8f
        const val POINT_CONTACT_EYE_HEIGHT = 8f
        const val POINT_CONTACT_VIEW_HALF = 4f
        const val POINT_VIEW_DISTANCE = 20f
        const val MAX_POINT_CONTACT_GAP = 0.08f
        const val POINT_SHADOW_DELTA = 8
    }
}
