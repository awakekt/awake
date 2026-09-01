/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.sample.composeshowcase.ui

import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember

context(_: Composer)
fun ShowcaseApp() {
    val nav = remember { ShowcaseNavState() }
    ShowcaseShell(nav)
}
