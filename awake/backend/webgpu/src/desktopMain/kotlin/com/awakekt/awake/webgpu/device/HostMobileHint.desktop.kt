/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.device

/** None: the desktop target runs wgpu-native, with no browser to ask, so the adapter alone decides. */
internal actual fun hostMobileHint(): HostMobileHint = HostMobileHint.None
