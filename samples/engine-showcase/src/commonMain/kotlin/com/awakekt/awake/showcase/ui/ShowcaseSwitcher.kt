/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.showcase.ui

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.showcase.EngineShowcase
import com.awakekt.awake.showcase.ShowcaseSelection
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant

private val PANEL_WIDTH: Dp = 200.dp
private val PANEL_INSET: Dp = 12.dp
private val ENTRY_GAP: Dp = 4.dp

/** Tags for a test to address the panel and its entries without matching on label text. */
internal object ShowcaseSwitcherTags {
    const val PANEL = "showcase-switcher"

    fun entry(id: String): String = "showcase-switcher-$id"
}

/**
 * The showcase picker: one button per demonstration, current one highlighted.
 *
 * A list and nothing else. Diagnostics live in [ShowcaseDebugCard] on the other edge, because a
 * checkbox sitting among the entries reads as another entry. Anything more than these two cards —
 * docks, panels, an inspector — is what `samples:studio` is for, and it carries the editor modules
 * to pay for them.
 *
 * A click only *requests* the switch; see [ShowcaseSelection] for why the scene is not torn down
 * from inside the click.
 */
context(_: Composer)
internal fun ShowcaseSwitcher(
    selection: ShowcaseSelection,
    showcases: List<EngineShowcase>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .testTag(ShowcaseSwitcherTags.PANEL)
            .width(PANEL_WIDTH)
            .background(ShowcaseTheme.palette.card)
            .padding(PANEL_INSET),
    ) {
        ShadcnText("Showcases", variant = ShadcnTextVariant.Small)
        showcases.forEach { showcase ->
            ShadcnButton(
                label = showcase.title,
                modifier = Modifier
                    .testTag(ShowcaseSwitcherTags.entry(showcase.id))
                    .fillMaxWidth()
                    .padding(top = ENTRY_GAP),
                variant = if (showcase.id == selection.current) {
                    ShadcnButtonVariant.Default
                } else {
                    ShadcnButtonVariant.Ghost
                },
                onClick = { selection.request(showcase.id) },
            )
        }
    }
}
