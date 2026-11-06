/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.device

import com.awakekt.awake.engine.window.PrimaryInput
import com.awakekt.awake.engine.window.browserLaunchEnvironment

/**
 * The page's hint: a coarse primary pointer, read through [browserLaunchEnvironment] so the backend
 * asks the same `matchMedia` query the window module does, and `navigator.userAgentData.mobile`.
 */
internal actual fun hostMobileHint(): HostMobileHint = HostMobileHint(
    coarsePointer = browserLaunchEnvironment().primaryInput == PrimaryInput.Touch,
    userAgentMobile = when (userAgentDataMobile()) {
        1 -> true
        0 -> false
        else -> null
    },
)

/** `navigator.userAgentData.mobile` as 1 or 0, or -1 where the browser has no `userAgentData`. */
@JsFun(
    "() => { const d = typeof navigator !== 'undefined' ? navigator.userAgentData : undefined; " +
        "return d != null && typeof d.mobile === 'boolean' ? (d.mobile ? 1 : 0) : -1; }",
)
private external fun userAgentDataMobile(): Int
