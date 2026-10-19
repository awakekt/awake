/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.render.imagepass.ImagePassAddressMode
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.imagepass.ImagePassDestination
import com.awakekt.awake.render.imagepass.ImagePassFilterMode
import com.awakekt.awake.render.imagepass.ImagePassRect
import com.awakekt.awake.render.imagepass.ImagePassSamplerSpec
import com.awakekt.awake.render.imagepass.ImagePassSource
import com.awakekt.awake.render.imagepass.ImagePassStep
import com.awakekt.awake.render.imagepass.ImagePassTargetFormat
import com.awakekt.awake.render.imagepass.ImagePassTargetSpec
import com.awakekt.awake.render.imagepass.ImagePassTextureBinding

/**
 * Builds dual-filter (Kawase) [ImagePassChain]s for layer blur.
 */
object LayerBlurEffect {
    private val QUAD_INDICES: ShortArray = shortArrayOf(0, 1, 2, 0, 2, 3)

    /** Transient target identifier prefix for layer blur levels. */
    fun levelTargetId(level: Int): String = "layer_blur_level_$level"

    /**
     * Builds an [ImagePassChain] executing the downsampling and upsampling passes for [blur]
     * over a layer texture of size [width] by [height], reading from [source] and writing the final
     * blurred result into [destination].
     */
    @Suppress("LongMethod")
    fun buildChain(
        source: ImagePassSource,
        blur: BlurChain,
        width: Int,
        height: Int,
        destination: ImagePassDestination = ImagePassDestination.ActiveTarget(
            blendMode = BlendMode.SourceOver,
            isPremultiplied = true,
            blendEnabled = false,
        ),
    ): ImagePassChain {
        val targets = ArrayList<ImagePassTargetSpec>(blur.iterations)
        for (level in 1..blur.iterations) {
            targets.add(
                ImagePassTargetSpec(
                    id = levelTargetId(level),
                    width = BlurChain.levelSize(width, level),
                    height = BlurChain.levelSize(height, level),
                    format = ImagePassTargetFormat.Rgba8Unorm,
                ),
            )
        }

        val linearClampSampler = ImagePassSamplerSpec(
            filter = ImagePassFilterMode.Linear,
            addressMode = ImagePassAddressMode.ClampToEdge,
        )

        val steps = ArrayList<ImagePassStep>(blur.iterations * 2)

        // Downsampling passes: level 1 from source, then level k from level k-1
        for (level in 1..blur.iterations) {
            val src = if (level == 1) source else ImagePassSource.TransientTarget(levelTargetId(level - 1))
            val dst = ImagePassDestination.TransientTarget(levelTargetId(level))
            val srcW = if (level == 1) width else BlurChain.levelSize(width, level - 1)
            val srcH = if (level == 1) height else BlurChain.levelSize(height, level - 1)
            val vertices = buildBlurPassQuad(0, 0, srcW, srcH, blur.offset, isUpsample = false)

            steps.add(
                ImagePassStep(
                    shader = ImagePassShaderRegistry.require(BlurEffectShaders.BLUR_PASS_SHADER_ID),
                    inputs = listOf(
                        ImagePassTextureBinding(
                            binding = 0,
                            source = src,
                            sampler = linearClampSampler,
                        ),
                    ),
                    destination = dst,
                    quadVertices = vertices,
                    quadIndices = QUAD_INDICES,
                ),
            )
        }

        // Upsampling passes: level k from level k+1, down to level 0 (destination)
        for (level in blur.iterations - 1 downTo 0) {
            val src = ImagePassSource.TransientTarget(levelTargetId(level + 1))
            val dst = if (level == 0) destination else ImagePassDestination.TransientTarget(levelTargetId(level))
            val srcW = BlurChain.levelSize(width, level + 1)
            val srcH = BlurChain.levelSize(height, level + 1)
            val vertices = buildBlurPassQuad(0, 0, srcW, srcH, blur.offset, isUpsample = true)

            steps.add(
                ImagePassStep(
                    shader = ImagePassShaderRegistry.require(BlurEffectShaders.BLUR_PASS_SHADER_ID),
                    inputs = listOf(
                        ImagePassTextureBinding(
                            binding = 0,
                            source = src,
                            sampler = linearClampSampler,
                        ),
                    ),
                    destination = dst,
                    quadVertices = vertices,
                    quadIndices = QUAD_INDICES,
                ),
            )
        }

        return ImagePassChain(
            sourceBounds = ImagePassRect(0f, 0f, width.toFloat(), height.toFloat()),
            transientTargets = targets,
            steps = steps,
        )
    }

    @Suppress("LongParameterList")
    private fun buildBlurPassQuad(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        offset: Float,
        isUpsample: Boolean,
    ): FloatArray {
        val stride = 10
        val data = FloatArray(4 * stride)
        val upsampleFloat = if (isUpsample) 1.0f else 0.0f
        val cl = left.toFloat()
        val ct = top.toFloat()
        val cr = right.toFloat()
        val cb = bottom.toFloat()

        fun writeVertex(index: Int, x: Float, y: Float) {
            val cursor = index * stride
            data[cursor] = x
            data[cursor + 1] = y
            data[cursor + 2] = cl
            data[cursor + 3] = ct
            data[cursor + 4] = cr
            data[cursor + 5] = cb
            data[cursor + 6] = offset
            data[cursor + 7] = upsampleFloat
            data[cursor + 8] = 0f
            data[cursor + 9] = 0f
        }

        writeVertex(0, -1f, -1f)
        writeVertex(1, 1f, -1f)
        writeVertex(2, 1f, 1f)
        writeVertex(3, -1f, 1f)
        return data
    }
}
