/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.config

import com.awakekt.awake.engine.platform.dsl.AppWindowBackend

data class WindowConfig(
    val title: String,
    val width: Int,
    val height: Int,
    val backend: AppWindowBackend,
    /** How finished frames reach the display; a request, not a guarantee. See [PresentMode]. */
    val presentMode: PresentMode = PresentMode.Auto,
)
