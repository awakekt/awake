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
    fun draw(input: GpuPassInput)
    fun renderToTexture(target: RenderTarget, input: GpuPassInput)
}
