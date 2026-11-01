/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.asset.shaderdocument.ShaderDocuments
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.core.image.decodeImage
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.asGpuImageSource
import com.awakekt.awake.render.texture.toTextureAsset
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * A shader effect's texture, given as a decoded image, is copied on the GPU by WebGPU in a browser
 * (awakekt/awake#1184) and draws what the same pixels given as a [TextureAsset] draw. The document is
 * a textured plane sampling its one texture; the two frames are rendered through the real scene pass
 * and compared pixel by pixel.
 *
 * Measured exact on headless Chromium with SwiftShader: the 64x64 plane covers about the texture's
 * own size, so it samples the base level, which the copy fills bit for bit. A view that reached the
 * smaller mips would differ by their GPU rounding (`ExternalImageUploadBrowserTest`).
 *
 * Negative control, measured: `flipY: false` in the copy fails the frame at texel 0, GPU
 * (45,18,18,255) against CPU (97,173,63,255).
 */
class ShaderEffectImageBrowserTest {
    @Test
    fun aShaderEffectsImageIsCopiedOnTheGpuAndDrawsAsItsPixelsDo() = runTest(timeout = 2.minutes) {
        val engine = ImageUploadEngine()
        try {
            val renderer = engine.boot()
            val gpu = assertNotNull(engine.contentGpu)
            val document = ShaderDocuments.compile(POOL)
            val image = decodeImage(encodedImage("image/png", 64, 64, translucent = false)).asGpuImageSource()

            val copiesBefore = gpu.copies
            val gpuFrame = renderer.frameWith(document.contentFeature(document.newInputs(), emptyMap(), mapOf("ripples" to image)))
            assertEquals(1, gpu.copies - copiesBefore, "the effect's image is copied on the GPU")
            val cpuFrame = renderer.frameWith(document.contentFeature(document.newInputs(), mapOf("ripples" to image.toTextureAsset())))
            assertEquals(1, gpu.copies - copiesBefore, "a TextureAsset is uploaded, not copied")

            assertTrue(cpuFrame.data.any { it != 0.toByte() }, "the plane drew something")
            assertTexels("shader effect frame", cpuFrame, gpuFrame, tolerance = 0)
            assertEquals(emptyList(), engine.errors, "WebGPU validation errors")
        } finally {
            engine.destroy()
        }
    }

    // One frame of the plane seen from above, with the effect attached for it alone.
    private suspend fun Renderer.frameWith(source: ContentFeatureSource): TextureAsset {
        val attached = attachContentFeature(source)
        val target = createRenderTarget(SIZE, SIZE)
        try {
            renderToTexture(
                target,
                ScenePassCompiler.compile(
                    lens = Lens(eye = Vec3f(0f, EYE_HEIGHT, 0.01f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 20f),
                    drawCalls = emptyList(),
                    clipSpace = clipSpace,
                    aspect = 1f,
                    drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
                ),
            )
            return readPixels(target)
        } finally {
            target.destroy()
            attached.detach()
        }
    }

    private companion object {
        const val SIZE = 64
        const val EYE_HEIGHT = 3f
        const val POOL = """
            {"name":"Pool","surface":"plane","plane":{"size":[4,4],"segments":1},
             "textures":[{"name":"ripples"}],
             "fragment":{"color":{"op":"sample","texture":"ripples","uv":{"op":"input","input":"uv"}}}}
        """
    }
}
