/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.engine.compose

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocal
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.engine.platform.error.AppCoroutineScope

/**
 * The app's [AppCoroutineScope], for UI that starts work which should stop with the app: the
 * parent of a remembered store, most often.
 *
 * ```kotlin
 * val hud = rememberStore(LocalAppCoroutineScope.current) { HudState() }
 * ```
 *
 * The app's Compose host provides it, so the UI of `composeAppModule` and of a scene's `content { }`
 * can read it, when the app registers the scope as a service:
 *
 * ```kotlin
 * service(AppCoroutineScope::class, AppCoroutineScope(serviceLookup()))
 * ```
 *
 * With no scope registered, reading it fails and says so, rather than handing out a scope that
 * nothing cancels.
 */
val LocalAppCoroutineScope: CompositionLocal<AppCoroutineScope> = compositionLocalOf {
    error(
        "No AppCoroutineScope provided. Register one as a service, " +
            "service(AppCoroutineScope::class, AppCoroutineScope(serviceLookup())), " +
            "and the app's Compose host provides it to the UI.",
    )
}

context(composer: Composer)
/**
 * Provides [scope] as [LocalAppCoroutineScope] to [content], or runs [content] as it is when [scope]
 * is null. For a host that composes app UI: pass it the app's registered scope, if there is one.
 *
 * @param scope The app's coroutine scope, or null when the app registered none.
 * @param content The app UI that may read [LocalAppCoroutineScope].
 */
fun ProvideAppCoroutineScope(
    scope: AppCoroutineScope?,
    content: context(Composer) () -> Unit,
) {
    if (scope == null) {
        content(composer)
    } else {
        CompositionLocalProvider(LocalAppCoroutineScope, scope, content)
    }
}
