/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.passes.SceneUpscale
import com.awakekt.awake.render.renderer.RenderScale
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.texture.OffscreenRenderTarget

/**
 * The scaled scene target behind `Renderer.renderScale`: the 3D scene renders into it, then
 * [upscale] copies it over the presented surface before the UI draws at full resolution.
 *
 * Held only while the scale is below 1. At full scale [resolve] releases it and returns `null`,
 * so the frame takes the unscaled path and allocates nothing.
 */
internal class SceneScaleTarget(
    private val graphicsDevice: GraphicsDevice,
    private val sampleCount: () -> Int,
) {
    private var target: OffscreenRenderTarget? = null
    private var chain: ImagePassChain = ImagePassChain.Empty
    private var chainOutputWidth = 0
    private var chainOutputHeight = 0

    /**
     * The target the scene renders into this frame over an [outputWidth] by [outputHeight] surface
     * at [scale], or `null` when the scaled size is the surface's own. Rebuilt when the size moves.
     */
    fun resolve(outputWidth: Int, outputHeight: Int, scale: Float): OffscreenRenderTarget? {
        val width = RenderScale.sceneExtent(outputWidth, scale)
        val height = RenderScale.sceneExtent(outputHeight, scale)
        val current = target
        if (current != null && current.width == width && current.height == height) return current
        release()
        return if (width == outputWidth && height == outputHeight) {
            null
        } else {
            OffscreenRenderTarget(graphicsDevice, width, height, sampleCount = sampleCount()).also { target = it }
        }
    }

    /** [input] with its viewport, measured on the output surface, moved onto [scene]. */
    fun sceneInput(input: GpuPassInput, scene: OffscreenRenderTarget, outputWidth: Int, outputHeight: Int): GpuPassInput {
        val viewport = input.viewport ?: return input
        return input.copy(
            viewport = RenderScale.sceneViewport(viewport, outputWidth, outputHeight, scene.width, scene.height),
        )
    }

    /** The bilinear copy of [scene] over the output surface, kept while neither size moves. */
    fun upscale(scene: OffscreenRenderTarget, outputWidth: Int, outputHeight: Int): ImagePassChain {
        if (chain.isEmpty || outputWidth != chainOutputWidth || outputHeight != chainOutputHeight) {
            chain = SceneUpscale.chain(scene, outputWidth, outputHeight)
            chainOutputWidth = outputWidth
            chainOutputHeight = outputHeight
        }
        return chain
    }

    /** Frees the target; the next [resolve] below full scale builds a new one. */
    fun release() {
        target?.destroy()
        target = null
        chain = ImagePassChain.Empty
    }
}

/**
 * Where a scene pass draws: [resolved]'s colour, through [multisampleColor] when the pass is
 * multisampled, with [depth] sized like it. [resolved] is also the post chain's parent target.
 */
internal class SceneAttachment(
    val multisampleColor: io.ygdrasil.webgpu.GPUTextureView?,
    val depth: io.ygdrasil.webgpu.GPUTextureView,
    val resolved: com.awakekt.awake.webgpu.pipeline.WebGpuImagePassParentTarget,
)

/** This target as a scene pass's attachment, its colour also the post chain's parent. */
internal fun OffscreenRenderTarget.attachment(): SceneAttachment = SceneAttachment(
    multisampleColor = sceneColorView.takeIf { sceneResolveView != null },
    depth = depthView,
    resolved = com.awakekt.awake.webgpu.pipeline.WebGpuImagePassParentTarget(
        colorTexture = colorTexture,
        colorView = colorView,
        colorFormat = colorFormat,
        width = width,
        height = height,
        isOffscreen = true,
        sampleable = true,
    ),
)
