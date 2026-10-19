/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.render.imagepass.ImagePassAddressMode
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.imagepass.ImagePassDestination
import com.awakekt.awake.render.imagepass.ImagePassFilterMode
import com.awakekt.awake.render.imagepass.ImagePassRect
import com.awakekt.awake.render.imagepass.ImagePassSamplerSpec
import com.awakekt.awake.render.imagepass.ImagePassSource
import com.awakekt.awake.render.imagepass.ImagePassStep
import com.awakekt.awake.render.imagepass.ImagePassTargetContext
import com.awakekt.awake.render.imagepass.ImagePassTargetFormat
import com.awakekt.awake.render.imagepass.ImagePassTargetSpec
import com.awakekt.awake.render.imagepass.ImagePassTextureBinding

/** The fallback alpha for a fully transparent tint; an opaque tint stays opaque. */
private const val FALLBACK_MIN_ALPHA = 0.6f

/**
 * Computes the fallback color used when a blur cannot be executed (e.g. quality is Off or
 * hardware cannot snapshot the destination).
 */
fun backdropFallbackColor(tint: Color): Color =
    tint.withAlpha(FALLBACK_MIN_ALPHA + (1f - FALLBACK_MIN_ALPHA) * tint.a)

/**
 * Helper for building dual-filter (Kawase) backdrop blur [ImagePassChain]s.
 */
object BackdropBlurEffect {
    /** Quad index array for a single full-NDC quad (two triangles). */
    val QUAD_INDICES: ShortArray = shortArrayOf(0, 1, 2, 0, 2, 3)

    /** Transient target identifier prefix for blur levels. */
    fun levelTargetId(level: Int): String = "blur_level_$level"

    /**
     * Builds an [ImagePassChain] executing the downsampling and upsampling passes for [blur]
     * over [region] of the backdrop.
     *
     * The final blurred result is left in transient target [levelTargetId] for [BlurChain.outputLevel].
     */
    fun buildBlurChain(
        blur: BlurChain,
        region: BackdropRegion,
        targetContext: ImagePassTargetContext,
        source: ImagePassSource = ImagePassSource.DestinationSnapshot,
    ): ImagePassChain {
        val width = targetContext.width
        val height = targetContext.height
        val steps = ArrayList<ImagePassStep>(blur.passCount)
        val targets = ArrayList<ImagePassTargetSpec>(blur.iterations)

        for (level in 1..blur.iterations) {
            val levelW = BlurChain.levelSize(width, level)
            val levelH = BlurChain.levelSize(height, level)
            targets.add(
                ImagePassTargetSpec(
                    id = levelTargetId(level),
                    width = levelW,
                    height = levelH,
                    format = ImagePassTargetFormat.Rgba8Unorm,
                ),
            )
        }

        val linearClampSampler = ImagePassSamplerSpec(
            filter = ImagePassFilterMode.Linear,
            addressMode = ImagePassAddressMode.ClampToEdge,
        )

        // Downsampling passes: level 1 from source, then level k from level k-1
        for (level in 1..blur.iterations) {
            val src = if (level == 1) source else ImagePassSource.TransientTarget(levelTargetId(level - 1))
            val dst = ImagePassDestination.TransientTarget(levelTargetId(level))
            val clamp = region.atLevel(level - 1, width, height)
            val vertices = buildBlurPassQuad(clamp, blur.offset, isUpsample = false)

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

        // Upsampling passes: level k from level k+1, back up to outputLevel
        for (level in blur.iterations - 1 downTo blur.outputLevel) {
            val src = ImagePassSource.TransientTarget(levelTargetId(level + 1))
            val dst = ImagePassDestination.TransientTarget(levelTargetId(level))
            val clamp = region.atLevel(level + 1, width, height)
            val vertices = buildBlurPassQuad(clamp, blur.offset, isUpsample = true)

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
            sourceBounds = ImagePassRect(
                region.left.toFloat(),
                region.top.toFloat(),
                region.width.toFloat(),
                region.height.toFloat(),
            ),
            transientTargets = targets,
            steps = steps,
        )
    }

    private fun buildBlurPassQuad(
        clamp: BackdropRegion,
        offset: Float,
        isUpsample: Boolean,
    ): FloatArray {
        val stride = 10
        val data = FloatArray(4 * stride)
        val upsampleFloat = if (isUpsample) 1.0f else 0.0f
        val cl = clamp.left.toFloat()
        val ct = clamp.top.toFloat()
        val cr = clamp.right.toFloat()
        val cb = clamp.bottom.toFloat()

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
