/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.render.texture.RenderTarget

/**
 * Hardware-facing ownership seam for a compiled GPU packet.
 *
 * Scene extraction and feature policy stay above this interface; a backend only executes the
 * generic packet and owns its native recording details.
 */
interface GpuPassExecutor {
    /**
     * Executes the submitted GPU draw pass into the active swapchain backbuffer.
     *
     * @param input Recorded packet of draw batches, uniforms, and subpass descriptors.
     */
    fun draw(input: GpuPassInput)

    /**
     * Executes the submitted GPU draw pass synchronously into an offscreen render target.
     *
     * @param target Target texture framebuffer receiving render outputs.
     * @param input Recorded packet of draw batches, uniforms, and subpass descriptors.
     */
    fun renderToTexture(target: RenderTarget, input: GpuPassInput)

    /** [renderToTexture] without waiting for the GPU; see `Renderer.submitToTexture`. */
    fun submitToTexture(target: RenderTarget, input: GpuPassInput) = renderToTexture(target, input)
}
