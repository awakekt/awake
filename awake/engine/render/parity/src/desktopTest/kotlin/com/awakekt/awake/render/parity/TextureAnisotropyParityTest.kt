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
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Anisotropic filtering on Vulkan through the real scene path (#1660): a `Nearest` texture samples
 * exactly as before at any setting, switching it back and forth while a scene keeps drawing raises no
 * validation error -- each material set takes the new sampler before its frame binds it, and the old
 * one is destroyed only after the frames in flight that used it -- and a request beyond the device is
 * clamped. Nothing here sets anisotropy for any other parity test: the renderer's default is 1, so
 * every baseline renders as before.
 *
 * That a linear texture filters differently is proved in `RendererAnisotropyTest`, not here: the PBR
 * textured shader samples with `textureSampleGrad`, and lavapipe ignores sampler anisotropy for
 * explicit gradients (a standalone Vulkan probe on this lavapipe: 0 of 16,384 pixels change between
 * 1x and 16x through `textureGrad`, all of them through `texture`). A real GPU applies it to both; that
 * pixel proof is a line on #1660.
 */
class TextureAnisotropyParityTest {
    @Test
    fun pixelArtSamplesTheSameAtAnySetting() = withVulkan { renderer ->
        assertTrue(renderer.maxSupportedAnisotropy >= SAMPLES, "lavapipe offers samplerAnisotropy up to ${renderer.maxSupportedAnisotropy}")
        val nearestOff = renderer.grazingGround(TextureFiltering.Nearest, anisotropy = 1)
        val nearestOn = renderer.grazingGround(TextureFiltering.Nearest, anisotropy = SAMPLES)
        assertEquals(0, nearestOff.indices.step(4).count { nearestOff.channelDelta(nearestOn, it) > 0 }, "pixel art samples as before")
    }

    @Test
    fun switchingWhileDrawingKeepsEveryFrameValid() = withVulkan { renderer ->
        // The device fails on any validation error when the session closes, a destroyed sampler
        // still bound among them.
        repeat(TOGGLES) { toggle ->
            renderer.maxAnisotropy = if (toggle % 2 == 0) SAMPLES else 1
            repeat(FRAMES_PER_TOGGLE) { renderer.grazingGround(TextureFiltering.Linear, anisotropy = renderer.maxAnisotropy) }
        }
        assertEquals(1, renderer.maxAnisotropy.coerceAtMost(1), "ends at 1")
    }

    @Test
    fun aRequestAboveTheDeviceIsClamped() = withVulkan { renderer ->
        renderer.maxAnisotropy = 1_000
        assertEquals(renderer.maxSupportedAnisotropy, renderer.maxAnisotropy)
        renderer.maxAnisotropy = 0
        assertEquals(1, renderer.maxAnisotropy)
    }

    /** A striped ground running away from a camera just above it, drawn with [anisotropy]. */
    private fun Renderer.grazingGround(filtering: TextureFiltering, anisotropy: Int): ByteArray {
        maxAnisotropy = anisotropy
        val material = texturedMaterial(stripes(filtering))
        val mesh = createMesh(ground())
        val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
        return try {
            val lens = Lens(eye = Vec3f(0f, EYE_HEIGHT, 0f), center = Vec3f(0f, 0f, -FAR), fovYRadians = 1f, near = 0.05f, far = 100f)
            val draw = RenderDrawCommand(mesh, material, extraUniformFloats = pbrMaterialFloats(metallic = 0f, roughness = 1f, baseColorFactor = Color.White, emissiveFactor = Color.Transparent, unlit = true))
            renderToTexture(
                target,
                ScenePassCompiler.compile(
                    lens = lens,
                    drawCalls = listOf(draw),
                    light = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f), ambient = 1f),
                    environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false),
                    clipSpace = clipSpace,
                    aspect = 1f,
                    drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
                ),
            )
            runBlocking { readPixels(target) }.data
        } finally {
            target.destroy()
            mesh.destroy()
            material.destroy()
        }
    }

    private fun withVulkan(block: (Renderer) -> Unit) {
        val game = app { window { size(SCENE_SIZE, SCENE_SIZE) } }
        openRuntimeSession(HeadlessUiBackend.Vulkan, game, SCENE_SIZE, SCENE_SIZE).use { session ->
            try {
                block(session.renderer)
            } finally {
                session.renderer.waitIdle()
            }
        }
    }

    private companion object {
        const val SAMPLES = 8
        const val EYE_HEIGHT = 0.4f
        const val FAR = 40f
        const val TEXELS = 256
        const val REPEAT = 2f
        const val STRIPE = 8
        const val TOGGLES = 6
        const val FRAMES_PER_TOGGLE = 3

        /** Black and white stripes [STRIPE] texels wide across the ground's width: what a grazing view blurs to grey without anisotropy. */
        fun stripes(filtering: TextureFiltering) = TextureAsset(
            data = ByteArray(TEXELS * TEXELS * 4) { index ->
                val x = (index / 4) % TEXELS
                if (index % 4 == 3 || (x / STRIPE) % 2 == 0) -1 else 0
            },
            width = TEXELS,
            height = TEXELS,
            filtering = filtering,
        )

        /** A ground from the camera's feet to [FAR] ahead, its UVs repeating [REPEAT] times. */
        fun ground() = MeshGeometry(
            floatArrayOf(
                -FAR, 0f, 1f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f,
                FAR, 0f, 1f, 0f, 1f, 0f, 1f, 1f, 1f, REPEAT, 0f,
                FAR, 0f, -FAR, 0f, 1f, 0f, 1f, 1f, 1f, REPEAT, REPEAT,
                -FAR, 0f, -FAR, 0f, 1f, 0f, 1f, 1f, 1f, 0f, REPEAT,
            ),
            intArrayOf(0, 2, 1, 2, 0, 3),
            VertexFormat.PositionNormalColorUv,
        )

        fun ByteArray.channelDelta(other: ByteArray, at: Int) = (0..2).maxOf { kotlin.math.abs((this[at + it].toInt() and 0xFF) - (other[at + it].toInt() and 0xFF)) }
    }
}
