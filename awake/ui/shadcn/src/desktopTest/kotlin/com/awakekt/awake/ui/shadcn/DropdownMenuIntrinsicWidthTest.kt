/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.ui.shadcn.components.ShadcnDropdownMenu
import com.awakekt.awake.ui.shadcn.components.ShadcnMenuItem
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A menu is as wide as its widest item, not as wide as the space it was handed.
 *
 * `fillMaxWidth` on the rows reads the *incoming maximum*, so every row filled whatever the parent
 * offered and the surface shrink-wrapped to that -- a menu of three short labels came out the full
 * width of the window. Upstream is `min-w-[8rem]` over content width.
 */
class DropdownMenuIntrinsicWidthTest {

    @Test
    fun theMenuIsAsWideAsItsWidestItemRatherThanItsParent() {
        val narrow = menuWidth(listOf("Profile", "Billing", "Settings"))
        val wide = menuWidth(listOf("Profile", "Billing and subscription settings", "Settings"))
        println("PROBE narrow=$narrow wide=$wide viewport=$VIEWPORT")

        assertTrue(narrow < VIEWPORT, "a menu of short labels filled the viewport: $narrow")
        assertTrue(wide > narrow, "a longer label must widen the menu: $wide vs $narrow")
        assertTrue(wide < VIEWPORT, "even a long label should not reach the viewport edge: $wide")
    }

    @Test
    fun theMenuNeverNarrowsBelowThePopoverMinimum() {
        // `min-w-[8rem]`, 128dp at density 1.
        assertEquals(
            MIN_WIDTH,
            menuWidth(listOf("A", "B")),
            "short labels should stop at the minimum",
        )
    }

    @Test
    fun everyRowFillsTheMenuSoTheHighlightIsNotRagged() {
        val frame = frameFor(listOf("Profile", "Billing and subscription settings"))
        val menu = frame.onNodeWithTag("menu").getBoundsInRoot()
        val short = frame.onNodeWithTag("menu.item.0").getBoundsInRoot()
        val long = frame.onNodeWithTag("menu.item.1").getBoundsInRoot()
        println("PROBE menu=${menu.width} short=${short.width} long=${long.width}")

        assertEquals(
            short.width,
            long.width,
            "rows must all be the same width or the highlight steps",
        )
        assertTrue(short.width <= menu.width, "a row cannot be wider than the menu holding it")
    }

    private fun menuWidth(labels: List<String>): Int =
        frameFor(labels).onNodeWithTag("menu").getBoundsInRoot().width

    private fun frameFor(labels: List<String>) = composeFrame(VIEWPORT, VIEWPORT) {
        provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
            ShadcnDropdownMenu(
                entries = labels.map(::ShadcnMenuItem),
                modifier = Modifier.testTag("menu"),
                id = "menu",
            )
        }
    }

    private companion object {
        const val VIEWPORT = 400
        const val MIN_WIDTH = 128
    }
}
