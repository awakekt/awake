/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.testing

import com.awakekt.awake.render.renderer.Renderer

/**
 * A renderer standing on a real device with no window, plus whatever must be destroyed with it.
 *
 * Backends differ in what they allocate around a `Renderer` -- Vulkan owns a render pass, a
 * descriptor-set layout and a transfer context; WebGPU owns a device and the surface it needed to
 * find an adapter -- and none of that is something a caller should have to know. [close] is the
 * whole of that difference, so a drawing can be handed to either backend unchanged.
 */
interface HeadlessRenderSession : AutoCloseable {
    val renderer: Renderer
}
