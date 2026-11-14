/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline.bundles

import io.ygdrasil.webgpu.GPURenderBundle
import io.ygdrasil.webgpu.RenderBundle
import io.ygdrasil.webgpu.WGPULowLevelApi
import io.ygdrasil.wgpu.wgpuRenderBundleRelease

@OptIn(WGPULowLevelApi::class)
internal actual fun GPURenderBundle.release() = wgpuRenderBundleRelease((this as RenderBundle).handler)
