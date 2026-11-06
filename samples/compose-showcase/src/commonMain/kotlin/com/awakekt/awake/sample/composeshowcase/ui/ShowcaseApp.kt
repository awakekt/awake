/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.composeshowcase.ui

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember

context(composer: Composer)
fun ShowcaseApp() {
    val nav = remember { ShowcaseNavState() }
    ShowcaseShell(nav)
}
