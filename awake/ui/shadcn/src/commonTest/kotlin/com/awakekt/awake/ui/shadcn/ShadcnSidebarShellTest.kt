/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.testing.composeTestSession
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.ui.shadcn.components.Sidebar
import com.awakekt.awake.ui.shadcn.components.SidebarContent
import com.awakekt.awake.ui.shadcn.components.SidebarInset
import com.awakekt.awake.ui.shadcn.components.SidebarProvider
import com.awakekt.awake.ui.shadcn.components.SidebarRail
import com.awakekt.awake.ui.shadcn.components.SidebarState
import com.awakekt.awake.ui.shadcn.components.rememberSidebarState
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadcnSidebarShellTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    @Test
    fun railOverlaysThePanelBoundaryWithoutChangingEitherSiblingWidth() {
        lateinit var state: SidebarState
        val session = composeTestSession(width = 600, height = 300) {
            provideShadcnTheme(theme) {
                state = rememberSidebarState(width = 200.dp)
                SidebarProvider(state, Modifier.fillMaxSize()) {
                    Sidebar(Modifier.testTag("sidebar")) {
                        SidebarContent {
                            Spacer(Modifier.height(600.dp))
                        }
                        SidebarRail(Modifier.testTag("sidebar.rail"))
                    }
                    SidebarInset(Modifier.testTag("sidebar.inset")) {
                        Spacer(Modifier.fillMaxSize())
                    }
                }
            }
        }

        val frame = session.frame()
        assertEquals(200, frame.onNodeWithTag("sidebar").getBoundsInRoot().width)
        assertEquals(400, frame.onNodeWithTag("sidebar.inset").getBoundsInRoot().width)

        val rail = frame.root.descendants()
            .single { it.width == 16 && it.height == 300 && it.contentAbsoluteX == 192 }
        assertEquals(192, rail.contentAbsoluteX)
        assertEquals(0, rail.contentAbsoluteY)

        val hovered = session.frame(FrameInput(600, 300, pointerX = 200, pointerY = 150, pointerDown = true))
        val divider = hovered.primitivesOf<DrawCommand.Quad>()
            .single { it.x == 199f && it.y == 0f && it.w == 2f && it.h == 300f }
        assertEquals(theme.palette.sidebarBorder, divider.color)

        session.frame(FrameInput(600, 300, pointerX = 200, pointerY = 150))
        assertTrue(state.isOpen, "the rail toggled a collapse mode that does not yet render icon-only content")
    }

    private fun LayoutNode.descendants(): List<LayoutNode> = buildList {
        fun visit(node: LayoutNode) {
            add(node)
            node.children.asList().forEach(::visit)
            node.layers.asList().forEach(::visit)
        }
        visit(this@descendants)
    }
}
