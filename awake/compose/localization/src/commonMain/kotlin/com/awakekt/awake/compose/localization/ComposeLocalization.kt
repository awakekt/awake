/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.localization

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocal
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.core.localization.InputLabels
import com.awakekt.awake.core.localization.Strings
import com.awakekt.awake.core.logging.Logger

/**
 * The [Strings] the UI below reads its text from, or null where nothing provides one.
 *
 * Awake Compose composes the whole tree every frame, so text read through [stringResource] shows a
 * new locale on the frame after [Strings.setLocale] returns, with nothing to observe or invalidate.
 */
val LocalStrings: CompositionLocal<Strings?> = compositionLocalOf { null }

/** The [InputLabels] the UI below names keys and buttons with, or null where nothing provides them. */
val LocalInputLabels: CompositionLocal<InputLabels?> = compositionLocalOf { null }

context(composer: Composer)
/**
 * Provides [strings], and [inputLabels] when given, to [content].
 *
 * @param strings The text, usually a played project's `LoadedProject.strings`.
 * @param inputLabels The key and button names, from `InputLabels.load(strings)`.
 * @param content The UI that reads them.
 */
fun ProvideStrings(
    strings: Strings,
    inputLabels: InputLabels? = null,
    content: context(Composer) () -> Unit,
) {
    if (inputLabels == null) {
        CompositionLocalProvider(LocalStrings, strings, content)
    } else {
        CompositionLocalProvider(LocalStrings provides strings, LocalInputLabels provides inputLabels, content = content)
    }
}

context(composer: Composer)
/**
 * The text for [key] in the active locale of [LocalStrings].
 *
 * Like [Strings.text], it never throws: a key no table has shows as the key, logged once by
 * [Strings]. With no [LocalStrings] at all, the key shows too, logged once per key.
 *
 * @param key The key.
 * @return The text.
 */
fun stringResource(key: String): String = LocalStrings.current?.text(key) ?: missingStrings(key)

context(composer: Composer)
/**
 * The text for [key] with [arguments] put into its placeholders: `stringResource("hud.level", "n" to 3)`.
 *
 * @param key The key.
 * @param arguments The placeholders' values.
 * @return The text.
 */
fun stringResource(key: String, vararg arguments: Pair<String, Any?>): String =
    LocalStrings.current?.text(key, *arguments) ?: missingStrings(key)

context(composer: Composer)
/**
 * The text for [key] with [arguments] put into its placeholders.
 *
 * @param key The key.
 * @param arguments The placeholders' values by name.
 * @return The text.
 */
fun stringResource(key: String, arguments: Map<String, Any?>): String =
    LocalStrings.current?.text(key, arguments) ?: missingStrings(key)

context(composer: Composer)
/**
 * The [LocalInputLabels] provided above, for a button prompt or a hotkey label:
 * `inputLabels().chord(KeyChord(Key.S, setOf(KeyModifier.Ctrl)))`.
 *
 * @return The labels.
 * @throws IllegalStateException when nothing provides them.
 */
fun inputLabels(): InputLabels = LocalInputLabels.current ?: error(
    "No InputLabels provided in CompositionLocal. " +
        "Provide them with ProvideStrings(strings, InputLabels.load(strings)) { ... }.",
)

private val log = Logger("Strings")
private val keysWithoutStrings = HashSet<String>()

private fun missingStrings(key: String): String {
    if (keysWithoutStrings.add(key)) log.warn { "No LocalStrings for \"$key\"; showing the key. Provide one with ProvideStrings." }
    return key
}
