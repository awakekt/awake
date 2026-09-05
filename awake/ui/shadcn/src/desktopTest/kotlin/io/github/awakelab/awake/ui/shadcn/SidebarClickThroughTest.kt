/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.testing.composeTestSession
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSidebarMenuItem
import io.github.awakelab.awake.ui.shadcn.components.Sidebar
import io.github.awakelab.awake.ui.shadcn.components.SidebarContent
import io.github.awakelab.awake.ui.shadcn.components.SidebarProvider
import io.github.awakelab.awake.ui.shadcn.components.SidebarRail
import io.github.awakelab.awake.ui.shadcn.components.rememberSidebarState
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class SidebarClickThroughTest {
    /**
     * `SidebarRail` used to swallow every click on the sidebar, including this unrelated nav item,
     * because `offset()` only shifts a node's *content* origin, not its own reported bounds -- and
     * `LayoutNode.contains()`'s permissive hit-test envelope unioned the two into a region spanning
     * the whole sidebar. `SidebarProviderRootMeasurePolicy` now places the rail directly instead.
     */
    @Test
    fun clickingARealSidebarMenuItemFiresOnClick() {
        var clicks = 0
        val theme = ShadcnThemeValues(ShadcnTheme)
        val width = 300
        val height = 200
        val session = composeTestSession(width, height) {
            provideShadcnTheme(theme) {
                val state = rememberSidebarState(width = 280.dp)
                SidebarProvider(state) {
                    Sidebar {
                        SidebarContent {
                            ShadcnSidebarMenuItem(
                                "Card",
                                modifier = Modifier.testTag("card-item"),
                                onClick = { clicks++ },
                            )
                        }
                        SidebarRail()
                    }
                }
            }
        }
        session.frame()
        session.click("card-item")
        assertEquals(
            1,
            clicks,
            "clicking a real shadcnSidebarMenuItem inside Sidebar/SidebarContent did not fire onClick",
        )
    }
}
