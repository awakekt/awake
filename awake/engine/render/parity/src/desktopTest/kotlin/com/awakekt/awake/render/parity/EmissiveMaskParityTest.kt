/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.passes.uniforms.skinnedMaterialFloats
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.webgpu.webGpuHeadlessPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A glTF emissive texture masks the emissive factor on skinned and static meshes alike (#1829): with
 * a half-black, half-white mask and a factor of 1, only the white half is brighter than the same
 * mesh without emission, on Vulkan and WebGPU. Before, `skinned_textured` added the factor
 * everywhere, so a creature with a painted emblem glowed all over.
 *
 * With no emissive texture the factor stands alone, as glTF specifies: the whole mesh glows as the
 * mask's white half does. A skinned mesh did so before; a textured one, binding a black neutral map,
 * didn't glow at all.
 */
class EmissiveMaskParityTest {
    @Test
    fun vulkanEmissiveTextureMasksTheGlow() = verify(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuEmissiveTextureMasksTheGlow() = verify(HeadlessUiBackend.WebGpu)

    private fun verify(backend: HeadlessUiBackend) = runBlocking {
        open(backend).use { session ->
            for (skinned in listOf(true, false)) {
                val label = "$backend ${if (skinned) "skinned_textured" else "textured"}"
                val plain = render(session.renderer, skinned, factor = false, mask = false)
                val masked = render(session.renderer, skinned, factor = true, mask = true)
                val factorOnly = render(session.renderer, skinned, factor = true, mask = false)
                println(
                    "EmissiveMask $label: plain ${plain.map { it.toList() }}, masked ${masked.map { it.toList() }}, " +
                        "factor alone ${factorOnly.map { it.toList() }}",
                )
                val (blackHalf, whiteHalf) = masked
                assertNear(plain[0], blackHalf, "$label: the mask's black half glows")
                assertTrue(
                    whiteHalf.indices.all { whiteHalf[it] >= plain[1][it] + GLOW_MARGIN },
                    "$label: the mask's white half is not brighter: ${plain[1].toList()} -> ${whiteHalf.toList()}",
                )
                factorOnly.forEach { assertNear(whiteHalf, it, "$label: with no emissive texture the factor alone") }
            }
        }
    }

    /** The left (black mask) and right (white mask) pixels of the quad, with an emissive [factor] of 1 or 0, through the [mask] or none. */
    private suspend fun render(renderer: Renderer, skinned: Boolean, factor: Boolean, mask: Boolean): List<IntArray> {
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val emissiveFactor = if (factor) Color.White else Color.Transparent
        val pbr = if (mask) PbrTextureSet(emissive = HALF_MASK) else PbrTextureSet()
        val mesh = renderer.createMesh(quad(skinned))
        val material = renderer.createMaterial(
            texture = GREY,
            uniformFloatCount = if (skinned) SkinnedUniformLayout.total else MaterialUniformLayouts.PbrTextured.total,
            pbrTextures = pbr,
        )
        val extras = if (skinned) {
            skinnedMaterialFloats(IDENTITY_JOINT, Color.White, emissiveFactor, unlit = false)
        } else {
            pbrMaterialFloats(metallic = 0f, roughness = 1f, baseColorFactor = Color.White, emissiveFactor = emissiveFactor, unlit = false)
        }
        return try {
            renderer.renderToTexture(target, compile(renderer, RenderDrawCommand(mesh, material, extraUniformFloats = extras)))
            val pixels = renderer.readPixels(target).data
            listOf(rgbAt(pixels, SIZE / 4), rgbAt(pixels, SIZE * 3 / 4))
        } finally {
            renderer.waitIdle()
            mesh.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private fun assertNear(expected: IntArray, actual: IntArray, label: String) = assertTrue(
        expected.indices.all { abs(expected[it] - actual[it]) <= ONE_LSB },
        "$label: ${expected.toList()} -> ${actual.toList()}",
    )

    private companion object {
        const val SIZE = 64
        const val MASK_SIZE = 16
        const val ONE_LSB = 1
        const val GLOW_MARGIN = 20
        const val HALF = 6f
        val IDENTITY_JOINT = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val LIGHT = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(0.3f, 0.3f, 0.3f), ambient = 0.2f)

        val GREY = solid(MASK_SIZE) { 100 }

        /** Black on the left half, white on the right, as a glTF emblem mask is. */
        val HALF_MASK = solid(MASK_SIZE) { x -> if (x < MASK_SIZE / 2) 0 else 255 }

        fun solid(size: Int, value: (x: Int) -> Int) = TextureAsset(
            data = ByteArray(size * size * 4) { index ->
                val texel = index / 4
                if (index % 4 == 3) 255.toByte() else value(texel % size).toByte()
            },
            width = size,
            height = size,
        )

        fun rgbAt(pixels: ByteArray, x: Int): IntArray {
            val base = (SIZE / 2 * SIZE + x) * 4
            return IntArray(3) { pixels[base + it].toInt() and 0xFF }
        }

        // Position, normal, colour and uv, and for the skinned quad four joints and four weights on joint 0.
        fun quad(skinned: Boolean): MeshGeometry {
            val corners = listOf(-HALF to -HALF, HALF to -HALF, HALF to HALF, -HALF to HALF)
            val uvs = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)
            val vertices = corners.zip(uvs).flatMap { (corner, uv) ->
                listOf(corner.first, 0f, corner.second, 0f, 1f, 0f, 1f, 1f, 1f, uv.first, uv.second) +
                    if (skinned) listOf(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f) else emptyList()
            }
            return MeshGeometry(
                vertices.toFloatArray(),
                intArrayOf(0, 1, 2, 2, 3, 0),
                if (skinned) VertexFormat.PositionNormalColorUvSkin else VertexFormat.PositionNormalColorUv,
            )
        }

        fun compile(renderer: Renderer, draw: RenderDrawCommand) = ScenePassCompiler.compile(
            lens = Lens(eye = Vec3f(0f, 6f, 9f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 50f),
            drawCalls = listOf(draw),
            light = LIGHT,
            environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false, exposure = 1f, fogColor = Color.Black, fogDensity = 0f),
            clipSpace = renderer.clipSpace,
            aspect = 1f,
            drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
        )

        suspend fun open(backend: HeadlessUiBackend): HeadlessRenderSession {
            val plan = EngineShowcaseRenderPlan.copy(contentFeatures = emptyList())
            if (backend == HeadlessUiBackend.WebGpu) return webGpuHeadlessPlan(plan, surface = HeadlessSurface(SIZE, SIZE))
            val ready = CompletableDeferred<Renderer>()
            val app = AppSpecBuilder().apply { ready { ready.complete(it) } }.build().createLifecycle()
            val engine = VulkanEngine(app, plan)
            engine.create(HeadlessSurface(SIZE, SIZE))
            val renderer = withTimeout(BOOT_TIMEOUT_MS) { ready.await() }
            return object : HeadlessRenderSession {
                override val renderer = renderer
                override fun close() = engine.dispose()
            }
        }

        const val BOOT_TIMEOUT_MS = 30_000L
    }
}
