/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline.bundles

import io.ygdrasil.webgpu.GPURenderBundle

/** Frees the bundle's native object on desktop, where wgpu4k's bundle has no close; the browser collects its own. */
internal expect fun GPURenderBundle.release()
