/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.showcase.ui

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.showcase.EngineShowcase
import io.github.awakelab.awake.showcase.ShowcaseDebugToggles
import io.github.awakelab.awake.showcase.ShowcaseSelection
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCheckbox
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant
import io.github.awakelab.awake.ui.shadcn.shadcnThemeValues
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme

private val PANEL_WIDTH: Dp = 200.dp
private val PANEL_INSET: Dp = 12.dp
private val ENTRY_GAP: Dp = 4.dp

private val ShowcaseTheme = shadcnThemeValues(dark = true)

/** Tags for a test to address the panel and its entries without matching on label text. */
internal object ShowcaseSwitcherTags {
    const val PANEL = "showcase-switcher"
    const val NAV_GRID_TOGGLE = "showcase-switcher-nav-grid"

    fun entry(id: String): String = "showcase-switcher-$id"
}

/**
 * A list of every showcase down the left edge, one button each, current one highlighted.
 *
 * The whole UI this sample has, on purpose. Its job is to stop `engine-showcase` being a harness
 * you have to restart with a different ID to see a different demonstration; anything more — docks,
 * panels, an inspector — is what `samples:studio` is for, and it carries the editor modules to pay
 * for them. This one draws over the scene it is switching and depends on nothing but shadcn.
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
    provideShadcnTheme(ShowcaseTheme) {
        Column(
            modifier = modifier
                .testTag(ShowcaseSwitcherTags.PANEL)
                .width(PANEL_WIDTH)
                .background(ShowcaseTheme.palette.card)
                .padding(PANEL_INSET),
        ) {
            ShadcnText("Showcases", variant = ShadcnTextVariant.Small)
            NavGridToggle()
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
}

/**
 * Switches the navigation overlay on.
 *
 * Off by default, and stated in the panel rather than bound to a function key: the markers are a
 * diagnostic drawn over the scene, and someone seeing them for the first time has no way to tell
 * that from part of the demonstration.
 */
context(_: Composer)
private fun NavGridToggle() {
    Row(
        modifier = Modifier.padding(top = ENTRY_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnCheckbox(
            checked = ShowcaseDebugToggles.showNavGrid,
            modifier = Modifier.testTag(ShowcaseSwitcherTags.NAV_GRID_TOGGLE),
            onCheckedChange = { ShowcaseDebugToggles.showNavGrid = it },
        )
        ShadcnText(
            "Nav grid",
            modifier = Modifier.padding(start = ENTRY_GAP),
            variant = ShadcnTextVariant.Small,
        )
    }
}
