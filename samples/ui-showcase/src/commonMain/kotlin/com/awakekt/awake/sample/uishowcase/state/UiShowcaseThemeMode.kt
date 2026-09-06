/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.state

internal enum class UiShowcaseThemeMode(val label: String) {
    Auto("Auto"),
    Light("Light"),
    Dark("Dark"),
}

internal expect fun platformPrefersDarkTheme(): Boolean
