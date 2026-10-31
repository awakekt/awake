/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.render.effects.ImageEffectShaders
import com.awakekt.awake.render.imagepass.ImagePassAddressMode
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.imagepass.ImagePassDestination
import com.awakekt.awake.render.imagepass.ImagePassFilterMode
import com.awakekt.awake.render.imagepass.ImagePassRect
import com.awakekt.awake.render.imagepass.ImagePassSamplerSpec
import com.awakekt.awake.render.imagepass.ImagePassSource
import com.awakekt.awake.render.imagepass.ImagePassStep
import com.awakekt.awake.render.imagepass.ImagePassTextureBinding
import com.awakekt.awake.render.texture.RenderTarget

/**
 * The upscale a backend runs when `Renderer.renderScale` is below 1: one bilinear copy of the
 * scaled scene target over the whole presented surface, before the UI draws on it.
 *
 * Built here, once, so every backend's image-pass executor runs the same step with the same shader.
 */
object SceneUpscale {
    private val LinearClamp = ImagePassSamplerSpec(ImagePassFilterMode.Linear, ImagePassAddressMode.ClampToEdge)
    private val FullUv: FloatArray = ImageEffectShaders.packCopyUniforms(0f, 0f, 1f, 1f)

    /**
     * The chain copying [scene]'s colour, filtered bilinearly, over an [outputWidth] by
     * [outputHeight] surface. It replaces every pixel it covers: blending is off, so the surface's
     * previous contents never show through.
     */
    fun chain(scene: RenderTarget, outputWidth: Int, outputHeight: Int): ImagePassChain = ImagePassChain(
        sourceBounds = ImagePassRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat()),
        transientTargets = emptyList(),
        steps = listOf(
            ImagePassStep(
                shader = ImageEffectShaders.CopySpec,
                inputs = listOf(
                    ImagePassTextureBinding(
                        binding = SOURCE_TEXTURE_BINDING,
                        source = ImagePassSource.RenderTargetColor(scene),
                        sampler = LinearClamp,
                    ),
                ),
                destination = ImagePassDestination.ActiveTarget(blendEnabled = false),
                uniformData = FullUv,
            ),
        ),
    )

    /** [ImageEffectShaders.CopyShader]'s texture binding; its sampler is the next one. */
    private const val SOURCE_TEXTURE_BINDING = 1
}
