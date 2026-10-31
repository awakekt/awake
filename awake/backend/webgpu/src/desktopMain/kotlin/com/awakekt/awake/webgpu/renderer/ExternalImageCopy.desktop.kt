/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTexture

// wgpu-native has no platform image to copy: every image reaches the GPU through readRgba8.
internal actual fun GPUQueue.canCopyExternalImage(image: Any?): Boolean = false

internal actual fun GPUQueue.copyExternalImage(image: Any, texture: GPUTexture, width: Int, height: Int): Unit =
    error("wgpu-native cannot copy an external image.")
