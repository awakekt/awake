/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.material3

import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.CompositionLocal
import io.github.awakelab.awake.compose.runtime.CompositionLocalProvider
import io.github.awakelab.awake.compose.runtime.compositionLocalOf
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.runtime.provides
import io.github.awakelab.awake.core.color.Color

/** The Material 3 color roles used by the initial Material component surface. */
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

val LocalMaterial3ColorScheme: CompositionLocal<Material3ColorScheme> =
    compositionLocalOf { LightMaterial3ColorScheme }

/** The nearest [Material3ColorScheme], defaulting to [LightMaterial3ColorScheme]. */
context(_: Composer)
val material3ColorScheme: Material3ColorScheme get() = LocalMaterial3ColorScheme.current

/** Provides [colorScheme] to Material 3 components in [content]. */
context(_: Composer)
fun provideMaterial3Theme(
    colorScheme: Material3ColorScheme = LightMaterial3ColorScheme,
    content: context(Composer) () -> Unit,
) {
    CompositionLocalProvider(
        LocalMaterial3ColorScheme provides colorScheme,
        content = content,
    )
}
