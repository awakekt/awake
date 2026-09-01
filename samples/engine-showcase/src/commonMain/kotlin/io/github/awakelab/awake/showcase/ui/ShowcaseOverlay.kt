/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.showcase.ui

import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.fillMaxSize
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.showcase.EngineShowcase
import io.github.awakelab.awake.showcase.ShowcaseSelection
import io.github.awakelab.awake.ui.shadcn.shadcnThemeValues
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme

internal val ShowcaseTheme = shadcnThemeValues(dark = true)

/**
 * The sample's chrome: what to run on the left, what to draw over it on the right.
 *
 * Two cards rather than one list with a checkbox on top, because they answer different questions.
 * The left is "which demonstration", which every showcase has; the right is "what should this
 * demonstration draw for me", which is per-showcase diagnostic scaffolding and has no business
 * sitting inside the picker — a debug toggle wedged into a menu reads as one of the menu entries.
 *
 * Opposite edges for the same reason: the eye should not have to decide which of two adjacent
 * controls it is looking at.
 */
context(_: Composer)
internal fun ShowcaseOverlay(
    selection: ShowcaseSelection,
    showcases: List<EngineShowcase>,
    modifier: Modifier = Modifier,
) {
    provideShadcnTheme(ShowcaseTheme) {
        Row(modifier.fillMaxSize()) {
            ShowcaseSwitcher(selection, showcases)
            Spacer(Modifier.weight(1f))
            ShowcaseDebugCard()
        }
    }
}
