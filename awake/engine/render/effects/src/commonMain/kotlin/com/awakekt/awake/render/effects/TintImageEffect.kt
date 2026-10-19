/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.render.imagepass.ImagePassAddressMode
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.imagepass.ImagePassDestination
import com.awakekt.awake.render.imagepass.ImagePassFilterMode
import com.awakekt.awake.render.imagepass.ImagePassPlacement
import com.awakekt.awake.render.imagepass.ImagePassRect
import com.awakekt.awake.render.imagepass.ImagePassSamplerSpec
import com.awakekt.awake.render.imagepass.ImagePassSource
import com.awakekt.awake.render.imagepass.ImagePassStep
import com.awakekt.awake.render.imagepass.ImagePassTargetContext
import com.awakekt.awake.render.imagepass.ImagePassTargetFormat
import com.awakekt.awake.render.imagepass.ImagePassTargetSpec
import com.awakekt.awake.render.imagepass.ImagePassTextureBinding
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.texture.TextureAsset
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared image-pass effect that snapshots a region of the active destination target into a pooled
 * transient target and composites a tinted version back into the active target.
 *
 * Exercises the full image-pass pipeline end-to-end across Vulkan and WebGPU:
 * 1. Interrupting the active render pass and snapshotting the destination (`DestinationSnapshot`).
 * 2. Allocating and rendering into a pooled `TransientTarget` (`tint_work`) via `ImageCopyShader`.
 * 3. Resuming the active render pass with `loadOp = LOAD` and rendering `tint_work` back into
 *    `ActiveTarget` with `ImageTintShader` inside `bounds`.
 */
object TintImageEffect {
    /** Transient target identifier used by [buildChain]. */
    const val TRANSIENT_TARGET_ID: String = "tint_work"

    /**
     * Quality profile parameters for [TintImageEffect].
     *
     * @property enabled Whether GPU image passes should execute for this tier.
     * @property scaleDivisor Resolution divisor applied to the transient work target.
     */
    data class QualityProfile(
        val enabled: Boolean,
        val scaleDivisor: Int,
    )

    /**
     * Resolves the [QualityProfile] for [quality].
     */
    fun profileFor(quality: EffectQuality): QualityProfile = when (quality) {
        EffectQuality.Off -> QualityProfile(enabled = false, scaleDivisor = 1)
        EffectQuality.Low -> QualityProfile(enabled = true, scaleDivisor = 2)
        EffectQuality.Medium -> QualityProfile(enabled = true, scaleDivisor = 1)
        EffectQuality.High -> QualityProfile(enabled = true, scaleDivisor = 1)
    }

    /**
     * Builds the [ImagePassChain] for tinting [bounds] inside [targetContext].
     *
     * Returns [ImagePassChain.Empty] when [targetContext]'s quality tier is [EffectQuality.Off],
     * when destination snapshots are unsupported, when [strength] is `<= 0f`, or when [bounds]
     * does not overlap the target.
     */
    fun buildChain(
        bounds: ImagePassRect,
        tintColor: Color,
        strength: Float,
        targetContext: ImagePassTargetContext,
    ): ImagePassChain {
        val profile = profileFor(targetContext.effectQuality)
        val clampedStrength = strength.coerceIn(0f, 1f)
        val clampedBounds = bounds.clampTo(
            targetContext.width.toFloat(),
            targetContext.height.toFloat(),
        )
        val executable = canExecute(profile, targetContext, clampedStrength) && !clampedBounds.isEmpty

        return if (executable) {
            createChain(clampedBounds, profile, tintColor, clampedStrength, targetContext)
        } else {
            ImagePassChain.Empty
        }
    }

    private fun canExecute(
        profile: TintProfile,
        targetContext: ImagePassTargetContext,
        clampedStrength: Float,
    ): Boolean =
        profile.enabled &&
            targetContext.snapshotSupported &&
            clampedStrength > 0f &&
            targetContext.width > 0 &&
            targetContext.height > 0

    private fun createChain(
        clampedBounds: ImagePassRect,
        profile: TintProfile,
        tintColor: Color,
        clampedStrength: Float,
        targetContext: ImagePassTargetContext,
    ): ImagePassChain {
        val uvRect = clampedBounds.normalizedIn(targetContext.width, targetContext.height)
        val halfTexelU = 0.5f / targetContext.width.toFloat()
        val halfTexelV = 0.5f / targetContext.height.toFloat()
        val minU = min(uvRect.x + halfTexelU, uvRect.right)
        val minV = min(uvRect.y + halfTexelV, uvRect.bottom)
        val maxU = (uvRect.right - halfTexelU).coerceAtLeast(minU)
        val maxV = (uvRect.bottom - halfTexelV).coerceAtLeast(minV)

        val workWidth = (clampedBounds.width.roundToInt().coerceAtLeast(1) / profile.scaleDivisor).coerceAtLeast(1)
        val workHeight = (clampedBounds.height.roundToInt().coerceAtLeast(1) / profile.scaleDivisor).coerceAtLeast(1)
        val transientTarget = ImagePassTargetSpec(
            id = TRANSIENT_TARGET_ID,
            scaleDivisor = profile.scaleDivisor,
            width = workWidth,
            height = workHeight,
            format = ImagePassTargetFormat.DestinationMatch,
        )

        val linearClampSampler = ImagePassSamplerSpec(
            filter = ImagePassFilterMode.Linear,
            addressMode = ImagePassAddressMode.ClampToEdge,
        )

        val copyStep = createCopyStep(
            workTarget = transientTarget,
            targetContext = targetContext,
            uvRect = uvRect,
            clampedUvBounds = ImagePassRect(minU, minV, maxU - minU, maxV - minV),
            sampler = linearClampSampler,
        )
        val tintStep = createTintStep(
            clampedBounds,
            targetContext,
            tintColor,
            clampedStrength,
            linearClampSampler,
        )

        return ImagePassChain(
            sourceBounds = clampedBounds,
            transientTargets = listOf(transientTarget),
            steps = listOf(copyStep, tintStep),
        )
    }

    private fun createCopyStep(
        workTarget: ImagePassTargetSpec,
        targetContext: ImagePassTargetContext,
        uvRect: ImagePassRect,
        clampedUvBounds: ImagePassRect,
        sampler: ImagePassSamplerSpec,
    ): ImagePassStep = ImagePassStep(
        shader = ImagePassShaderRegistry.require(ImageEffectShaders.COPY_SHADER_ID),
        inputs = listOf(
            ImagePassTextureBinding(
                binding = 1,
                source = ImagePassSource.DestinationSnapshot,
                sampler = sampler,
            ),
        ),
        destination = ImagePassDestination.TransientTarget(TRANSIENT_TARGET_ID),
        uniformData = ImageEffectShaders.packCopyUniforms(
            clampedUvBounds.x,
            clampedUvBounds.y,
            clampedUvBounds.right,
            clampedUvBounds.bottom,
        ),
        quadVertices = buildImagePassQuadVertices(
            rect = ImagePassRect(0f, 0f, workTarget.width.toFloat(), workTarget.height.toFloat()),
            targetWidth = workTarget.width,
            targetHeight = workTarget.height,
            clipSpace = targetContext.clipSpace,
            u0 = uvRect.x,
            v0 = uvRect.y,
            u1 = uvRect.right,
            v1 = uvRect.bottom,
        ),
        quadIndices = ImagePassQuadIndices,
        clearDestination = true,
    )

    private fun createTintStep(
        clampedBounds: ImagePassRect,
        targetContext: ImagePassTargetContext,
        tintColor: Color,
        clampedStrength: Float,
        sampler: ImagePassSamplerSpec,
    ): ImagePassStep = ImagePassStep(
        shader = ImagePassShaderRegistry.require(ImageEffectShaders.TINT_SHADER_ID),
        inputs = listOf(
            ImagePassTextureBinding(
                binding = 1,
                source = ImagePassSource.TransientTarget(TRANSIENT_TARGET_ID),
                sampler = sampler,
            ),
        ),
        destination = ImagePassDestination.ActiveTarget(
            scissorRect = clampedBounds,
            blendEnabled = false,
        ),
        uniformData = ImageEffectShaders.packTintUniforms(
            minU = 0f,
            minV = 0f,
            maxU = 1f,
            maxV = 1f,
            tintColor = tintColor,
            strength = clampedStrength,
        ),
        quadVertices = buildImagePassQuadVertices(
            rect = clampedBounds,
            targetWidth = targetContext.width,
            targetHeight = targetContext.height,
            clipSpace = targetContext.clipSpace,
            u0 = 0f,
            v0 = 0f,
            u1 = 1f,
            v1 = 1f,
        ),
        quadIndices = ImagePassQuadIndices,
    )

    /**
     * Single-draw translucent quad fallback emitted when [EffectQuality.Off] is active or
     * destination snapshots are unavailable.
     */
    fun fallbackPrimitive(
        bounds: ImagePassRect,
        tintColor: Color,
        strength: Float,
    ): UiDrawPrimitive.Quad {
        val clampedStrength = strength.coerceIn(0f, 1f)
        return UiDrawPrimitive.Quad(
            x = bounds.x,
            y = bounds.y,
            w = bounds.width,
            h = bounds.height,
            color = tintColor.copy(a = (tintColor.a * clampedStrength).coerceIn(0f, 1f)),
        )
    }

    /**
     * Creates an [ImagePassPlacement] at [primitiveIndex] for [bounds] with its translucent quad
     * fallback.
     */
    fun placement(
        primitiveIndex: Int,
        bounds: ImagePassRect,
        tintColor: Color,
        strength: Float,
        targetContext: ImagePassTargetContext,
    ): ImagePassPlacement = ImagePassPlacement(
        primitiveIndex = primitiveIndex,
        chain = buildChain(bounds, tintColor, strength, targetContext),
        fallbackPrimitives = if (strength > 0f && !bounds.isEmpty) {
            listOf(fallbackPrimitive(bounds, tintColor, strength))
        } else {
            emptyList()
        },
    )

    /**
     * Deterministic CPU reference for [TintImageEffect]: returns a copy of [source] with pixels
     * inside [bounds] mixed toward [tintColor] by [strength].
     */
    fun applyReference(
        source: TextureAsset,
        bounds: ImagePassRect,
        tintColor: Color,
        strength: Float,
    ): TextureAsset {
        val clampedStrength = strength.coerceIn(0f, 1f)
        val out = source.data.copyOf()
        if (clampedStrength <= 0f || bounds.isEmpty) {
            return TextureAsset(out, source.width, source.height)
        }
        val clamped = bounds.clampTo(source.width.toFloat(), source.height.toFloat())
        val startX = clamped.x.roundToInt().coerceIn(0, source.width)
        val endX = clamped.right.roundToInt().coerceIn(startX, source.width)
        val startY = clamped.y.roundToInt().coerceIn(0, source.height)
        val endY = clamped.bottom.roundToInt().coerceIn(startY, source.height)

        for (y in startY until endY) {
            for (x in startX until endX) {
                val idx = (y * source.width + x) * 4
                val srcR = (out[idx].toInt() and 0xFF) / 255f
                val srcG = (out[idx + 1].toInt() and 0xFF) / 255f
                val srcB = (out[idx + 2].toInt() and 0xFF) / 255f
                val mixedR = srcR + (tintColor.r - srcR) * clampedStrength
                val mixedG = srcG + (tintColor.g - srcG) * clampedStrength
                val mixedB = srcB + (tintColor.b - srcB) * clampedStrength
                out[idx] = (mixedR.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
                out[idx + 1] = (mixedG.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
                out[idx + 2] = (mixedB.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
            }
        }
        return TextureAsset(out, source.width, source.height)
    }
}
