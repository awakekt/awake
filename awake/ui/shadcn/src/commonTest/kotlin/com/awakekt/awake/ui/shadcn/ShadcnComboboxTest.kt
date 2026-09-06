/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.testing.composeTestSession
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.ui.shadcn.components.ShadcnCombobox
import com.awakekt.awake.ui.shadcn.components.ShadcnComboboxItem
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadcnComboboxTest {
    private val theme = shadcnThemeValues()

    @Test
    fun comboboxOpensSearchablePopupAndSelectsItem() {
        var expanded = false
        var selected: Int? = null
        val items = listOf("Next.js", "SvelteKit", "Nuxt.js", "Remix", "Astro").map(::ShadcnComboboxItem)

        val session = composeTestSession(width = 320, height = 360) {
            provideShadcnTheme(theme) {
                ShadcnCombobox(
                    items = items,
                    selectedIndex = selected,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onItemSelected = { selected = it },
                    id = "combobox",
                    modifier = Modifier.width(200.dp),
                )
            }
        }

        session.frame()
        session.click("combobox.trigger")
        val openFrame = session.frame()

        // Popup and search field are visible
        assertTrue(openFrame.flatSemantics().any { it.testTag == "combobox.content" }, "Combobox content popup is open")
        assertTrue(openFrame.flatSemantics().any { it.testTag == "combobox.search" }, "Search field is visible")

        // Click item 1 ("SvelteKit")
        session.click("combobox.item.1")
        val closedFrame = session.frame()

        assertEquals(1, selected, "Item 1 was selected")
        assertTrue(closedFrame.flatSemantics().none { it.testTag == "combobox.content" }, "Popup closed on selection")
    }

    @Test
    fun comboboxDismissesOnOutsidePress() {
        var expanded = false
        val items = listOf("Alpha", "Beta").map(::ShadcnComboboxItem)

        val session = composeTestSession(width = 300, height = 300) {
            provideShadcnTheme(theme) {
                ShadcnCombobox(
                    items = items,
                    selectedIndex = null,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onItemSelected = {},
                    id = "combobox",
                )
            }
        }

        session.frame()
        session.click("combobox.trigger")
        val openFrame = session.frame()
        assertTrue(openFrame.flatSemantics().any { it.testTag == "combobox.content" })

        // Click outside
        session.frame(FrameInput(300, 300, pointerX = 280, pointerY = 280, pointerDown = true))
        val outsideClosed = session.frame(FrameInput(300, 300, pointerX = 280, pointerY = 280))

        assertTrue(outsideClosed.flatSemantics().none { it.testTag == "combobox.content" }, "Popup dismissed on outside press")
    }
}
