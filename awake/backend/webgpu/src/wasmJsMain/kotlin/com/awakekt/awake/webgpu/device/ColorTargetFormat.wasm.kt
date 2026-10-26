/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.device

import io.ygdrasil.webgpu.WGPUContext

/**
 * Unchanged: the canvas is configured with `getPreferredCanvasFormat()` before this context
 * reaches the renderer, and that is always `bgra8unorm` or `rgba8unorm`, never an sRGB format.
 */
internal actual fun WGPUContext.withDisplayEncodedColorFormat(): WGPUContext = this
