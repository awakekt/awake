/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.material3

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocal
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.core.color.Color

/**
 * The Material 3 color roles used by the initial Material component surface.
 *
 * @property primary The primary brand accent color.
 * @property onPrimary Color for content atop [primary] surfaces.
 * @property background Page and screen background color.
 * @property onBackground Color for content atop [background] surfaces.
 * @property surface Container and card surface color.
 * @property onSurface Color for text and icons atop [surface] containers.
 */
data class Material3ColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
)

/** Material's baseline light color scheme. Applications may provide their own scheme. */
val LightMaterial3ColorScheme = Material3ColorScheme(
    primary = Color.fromHex(0x6750A4),
    onPrimary = Color.White,
    background = Color.fromHex(0xFFFBFE),
    onBackground = Color.fromHex(0x1C1B1F),
    surface = Color.fromHex(0xFFFBFE),
    onSurface = Color.fromHex(0x1C1B1F),
)

/**
 * CompositionLocal providing the ambient [Material3ColorScheme] to the current UI subtree.
 */
val LocalMaterial3ColorScheme: CompositionLocal<Material3ColorScheme> =
    compositionLocalOf { LightMaterial3ColorScheme }

context(_: Composer)
/** The nearest [Material3ColorScheme], defaulting to [LightMaterial3ColorScheme]. */
val material3ColorScheme: Material3ColorScheme get() = LocalMaterial3ColorScheme.current

context(_: Composer)
/**
 * Provides [colorScheme] to Material 3 components in [content].
 *
 * @param colorScheme The active Material 3 color palette to bind into ambient scope.
 * @param content The composable content subtree inheriting this theme.
 */
fun provideMaterial3Theme(
    colorScheme: Material3ColorScheme = LightMaterial3ColorScheme,
    content: context(Composer) () -> Unit,
) {
    CompositionLocalProvider(
        LocalMaterial3ColorScheme provides colorScheme,
        content = content,
    )
}
