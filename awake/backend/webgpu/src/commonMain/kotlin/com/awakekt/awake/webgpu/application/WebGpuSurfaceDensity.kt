/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.application

/**
 * Physical pixels per device-independent unit for the WebGPU surface.
 *
 * The swapchain is sized in physical pixels, so without this the UI lays out as though one dp were
 * one physical pixel and every panel, control and glyph comes out at half size on a 2x display.
 * Vulkan derives the same ratio from its window's logical extent; WebGPU has no shared notion of a
 * window -- the browser has `devicePixelRatio` and a native surface has nothing to ask -- so it is
 * per-platform.
 */
expect fun webGpuSurfaceDensity(): Float
