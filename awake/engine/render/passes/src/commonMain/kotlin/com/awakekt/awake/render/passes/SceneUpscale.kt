/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.render.effects.ImageEffectShaders
import com.awakekt.awake.render.effects.UpscaleShaders
import com.awakekt.awake.render.imagepass.ImagePassAddressMode
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.imagepass.ImagePassDestination
import com.awakekt.awake.render.imagepass.ImagePassFilterMode
import com.awakekt.awake.render.imagepass.ImagePassRect
import com.awakekt.awake.render.imagepass.ImagePassSamplerSpec
import com.awakekt.awake.render.imagepass.ImagePassSource
import com.awakekt.awake.render.imagepass.ImagePassStep
import com.awakekt.awake.render.imagepass.ImagePassTextureBinding
import com.awakekt.awake.render.renderer.SceneUpscaleMode
import com.awakekt.awake.render.renderer.SceneUpscaling
import com.awakekt.awake.render.texture.RenderTarget

/**
 * The upscale a backend runs when `Renderer.renderScale` is below 1: copies (and optionally
 * sharpens, #1830) the scaled scene target over the whole presented surface, after anti-aliasing
 * and post-processing and before the UI draws at full resolution.
 *
 * Built here, once, so every backend's image-pass executor runs the same step with the same shader.
 */
object SceneUpscale {
    private val LinearClamp = ImagePassSamplerSpec(ImagePassFilterMode.Linear, ImagePassAddressMode.ClampToEdge)
    private val NearestClamp = ImagePassSamplerSpec(ImagePassFilterMode.Nearest, ImagePassAddressMode.ClampToEdge)
    private val FullUv: FloatArray = ImageEffectShaders.packCopyUniforms(0f, 0f, 1f, 1f)

    /**
     * The chain copying [scene]'s colour, filtered bilinearly, over an [outputWidth] by
     * [outputHeight] surface. It replaces every pixel it covers: blending is off, so the surface's
     * previous contents never show through.
     */
    fun chain(scene: RenderTarget, outputWidth: Int, outputHeight: Int): ImagePassChain =
        chain(scene, outputWidth, outputHeight, SceneUpscaling.Off)

    /**
     * The scale-up chain over [scene] into an [outputWidth] by [outputHeight] surface using
     * [upscaling]: plain bilinear copy ([SceneUpscaleMode.Off]), 5-tap contrast-adaptive sharpening
     * ([SceneUpscaleMode.Sharpen]), or 12-tap edge-thinning upscale and sharpening
     * ([SceneUpscaleMode.UpscaleAndSharpen]), all in a single pass with blending off.
     */
    fun chain(
        scene: RenderTarget,
        outputWidth: Int,
        outputHeight: Int,
        upscaling: SceneUpscaling,
    ): ImagePassChain {
        val shader = when (upscaling.mode) {
            SceneUpscaleMode.Off -> ImageEffectShaders.CopySpec
            SceneUpscaleMode.Sharpen -> UpscaleShaders.CasSharpenSpec
            SceneUpscaleMode.UpscaleAndSharpen -> UpscaleShaders.CasUpscaleSpec
        }
        val sampler = when (upscaling.mode) {
            SceneUpscaleMode.UpscaleAndSharpen -> NearestClamp
            SceneUpscaleMode.Off, SceneUpscaleMode.Sharpen -> LinearClamp
        }
        val uniforms = when (upscaling.mode) {
            SceneUpscaleMode.Off -> FullUv
            SceneUpscaleMode.Sharpen ->
                UpscaleShaders.packCasSharpenUniforms(outputWidth, outputHeight, upscaling.strength)
            SceneUpscaleMode.UpscaleAndSharpen ->
                UpscaleShaders.packCasUpscaleUniforms(scene.width, scene.height, upscaling.strength)
        }
        return ImagePassChain(
            sourceBounds = ImagePassRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat()),
            transientTargets = emptyList(),
            steps = listOf(
                ImagePassStep(
                    shader = shader,
                    inputs = listOf(
                        ImagePassTextureBinding(
                            binding = SOURCE_TEXTURE_BINDING,
                            source = ImagePassSource.RenderTargetColor(scene),
                            sampler = sampler,
                        ),
                    ),
                    destination = ImagePassDestination.ActiveTarget(blendEnabled = false),
                    uniformData = uniforms,
                ),
            ),
        )
    }

    /** Source texture binding shared by every scale-up shader; its sampler is the next one. */
    private const val SOURCE_TEXTURE_BINDING = 1
}
