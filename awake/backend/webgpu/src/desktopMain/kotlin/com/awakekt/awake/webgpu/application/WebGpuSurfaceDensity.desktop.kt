/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.application

/**
 * Unscaled.
 *
 * Desktop WebGPU has the same problem on a retina display, but the fix is not the same one line:
 * the ratio comes from the GLFW window, which this backend never sees -- `create` is handed an
 * already-built `WGPUContext`. Left honest rather than guessed, since a wrong constant here would
 * be worse than the 1x it already renders at.
 */
actual fun webGpuSurfaceDensity(): Float = 1f
