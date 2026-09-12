/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.capture

import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset

/**
 * Owns an offscreen render target for a diagnostic capture sequence. The [render] action keeps
 * this utility independent of a particular pass vocabulary: UI, 3D, and future backends can all
 * render into the supplied target before its RGBA8 pixels are read back.
 */
class FrameCapture(
    private val renderer: Renderer,
    width: Int,
    height: Int,
) : AutoCloseable {
    private val target: RenderTarget

    init {
        require(width > 0 && height > 0) { "Capture size must be positive, was ${width}x$height." }
        target = renderer.createRenderTarget(width, height)
    }

    suspend fun capture(render: (RenderTarget) -> Unit): TextureAsset {
        render(target)
        return renderer.readPixels(target)
    }

    /** Captures one attachment from the same rendered frame for a framebuffer debugger. */
    suspend fun captureAttachment(
        attachment: FramebufferAttachment,
        render: (RenderTarget) -> Unit,
    ): FramebufferAttachmentData {
        render(target)
        return renderer.readFramebufferAttachment(target, attachment)
    }

    override fun close() = target.destroy()
}
