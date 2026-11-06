/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.composeshowcase.ui

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope

/**
 * The showcase's root UI.
 *
 * @param appScope The app's coroutine scope, which the pages' remembered stores run under.
 */
context(composer: Composer)
fun ShowcaseApp(appScope: CoroutineScope) {
    val nav = remember { ShowcaseNavState() }
    CompositionLocalProvider(LocalShowcaseAppScope, appScope) {
        ShowcaseShell(nav)
    }
}
