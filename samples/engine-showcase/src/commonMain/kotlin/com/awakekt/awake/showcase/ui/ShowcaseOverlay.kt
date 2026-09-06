/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.showcase.ui

import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.showcase.EngineShowcase
import com.awakekt.awake.showcase.ShowcaseSelection
import com.awakekt.awake.ui.shadcn.shadcnThemeValues
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme

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
