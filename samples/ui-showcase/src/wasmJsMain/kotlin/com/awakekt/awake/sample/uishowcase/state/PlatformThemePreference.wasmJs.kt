/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.state

import kotlinx.browser.window

internal actual fun platformPrefersDarkTheme(): Boolean =
    window.matchMedia("(prefers-color-scheme: dark)").matches
