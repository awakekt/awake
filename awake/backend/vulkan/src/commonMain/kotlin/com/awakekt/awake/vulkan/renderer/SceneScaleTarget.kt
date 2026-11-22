/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.passes.SceneUpscale
import com.awakekt.awake.render.renderer.RenderScale
import com.awakekt.awake.render.renderer.SceneUpscaling
import com.awakekt.awake.vulkan.texture.OffscreenRenderTarget

/**
 * The scaled 3D scene target for [Renderer.renderScale] on Vulkan, allocated only while the scale
 * is below 1.
 */
internal class SceneScaleTarget(
    private val createTarget: (width: Int, height: Int) -> OffscreenRenderTarget,
) {
    private var target: OffscreenRenderTarget? = null
    private var chain: ImagePassChain = ImagePassChain.Empty
    private var chainOutputWidth = 0
    private var chainOutputHeight = 0
    private var chainUpscaling: SceneUpscaling = SceneUpscaling.Off

    /**
     * The target the scene renders into this frame over an [outputWidth] by [outputHeight] surface
     * at [scale], or `null` when the scene renders straight into the swapchain framebuffer.
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
            createTarget(width, height).also { target = it }
        }
    }

    /** [input] with its viewport, measured on the output surface, moved onto [scene]. */
    fun sceneInput(
        input: GpuPassInput,
        scene: OffscreenRenderTarget,
        outputWidth: Int,
        outputHeight: Int,
    ): GpuPassInput {
        val viewport = input.viewport ?: return input
        return input.copy(
            viewport = RenderScale.sceneViewport(viewport, outputWidth, outputHeight, scene.width, scene.height),
        )
    }

    /** The scale-up pass of [scene] over the output surface, kept while size and [upscaling] stay fixed. */
    fun upscale(
        scene: OffscreenRenderTarget,
        outputWidth: Int,
        outputHeight: Int,
        upscaling: SceneUpscaling = SceneUpscaling.Off,
    ): ImagePassChain {
        if (!sameChain(outputWidth, outputHeight, upscaling)) {
            chain = SceneUpscale.chain(scene, outputWidth, outputHeight, upscaling)
            chainOutputWidth = outputWidth
            chainOutputHeight = outputHeight
            chainUpscaling = upscaling
        }
        return chain
    }

    private fun sameChain(outputWidth: Int, outputHeight: Int, upscaling: SceneUpscaling): Boolean {
        val sameSize = outputWidth == chainOutputWidth && outputHeight == chainOutputHeight
        return !chain.isEmpty && sameSize && upscaling == chainUpscaling
    }

    fun release() {
        target?.destroy()
        target = null
        chain = ImagePassChain.Empty
        chainOutputWidth = 0
        chainOutputHeight = 0
    }
}
