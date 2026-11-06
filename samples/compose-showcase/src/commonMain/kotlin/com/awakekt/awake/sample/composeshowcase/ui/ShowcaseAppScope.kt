/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.composeshowcase.ui

import com.awakekt.awake.compose.runtime.CompositionLocal
import com.awakekt.awake.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CoroutineScope

/**
 * The app's coroutine scope, the parent the showcase's remembered stores run under. [ShowcaseApp]
 * provides the `AppCoroutineScope` the app registered, so a store's uncaught failure reaches the
 * app's error handling and disposing the app cancels its work.
 *
 * A composition local rather than a parameter: the pages are values in the catalog, and only the
 * ones that remember a store read it.
 */
internal val LocalShowcaseAppScope: CompositionLocal<CoroutineScope> = compositionLocalOf {
    error("No app scope provided: ShowcaseApp provides the app's AppCoroutineScope.")
}
