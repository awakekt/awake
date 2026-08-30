/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.fillMaxSize
import io.github.awakelab.awake.compose.testing.ComposeTestSession
import io.github.awakelab.awake.compose.testing.composeTestSession
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import io.github.awakelab.awake.compose.ui.semantics.SemanticsNode
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.core.input.Key
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenuItem
import io.github.awakelab.awake.ui.shadcn.components.shadcnContextMenu
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A right-click opens the menu where it was clicked, and nothing else notices right-clicks.
 *
 * That second half is the risk in adding a press type: every existing handler matches on `Press`,
 * so if a secondary press were a flag on it rather than its own type, every button in the app would
 * have quietly become right-clickable too.
 */
class ShadcnContextMenuTest {

    @Test
    fun aRightClickOpensTheMenuWhereItWasClicked() {
        val world = menuWorld()
        world.session.frame()
        assertEquals(null, menuNode(world.session), "the menu was open before any right-click")

        world.session.frame(secondaryPressAt(PRESS_X, PRESS_Y))
        val menu = menuNode(world.session)
        assertTrue(menu != null, "a right-click did not open the menu")
        println("PROBE press=($PRESS_X,$PRESS_Y) menu=(${menu.x},${menu.y}) ${menu.width}x${menu.height}")

        assertEquals(PRESS_X, menu.x, "the menu did not open at the pointer's x")
        assertEquals(PRESS_Y, menu.y, "the menu did not open at the pointer's y")
    }

    @Test
    fun aRightClickDoesNotActuateAnOrdinaryControl() {
        var clicks = 0
        val session = composeTestSession(VIEWPORT, VIEWPORT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                Box(Modifier.fillMaxSize().clickable { clicks++ }.testTag("button"))
            }
        }
        session.frame()
        session.frame(secondaryPressAt(PRESS_X, PRESS_Y))

        assertEquals(0, clicks, "a right-click actuated a clickable")
    }

    @Test
    fun pressingOutsideClosesIt() {
        val world = menuWorld()
        world.session.frame()
        world.session.frame(secondaryPressAt(PRESS_X, PRESS_Y))
        assertTrue(menuNode(world.session) != null)

        world.session.clickAt(VIEWPORT - EDGE, VIEWPORT - EDGE)
        assertTrue(leavesWithinTheFade(world.session), "an outside press did not close the context menu")
    }

    @Test
    fun escapeClosesIt() {
        val world = menuWorld()
        world.session.frame()
        world.session.frame(secondaryPressAt(PRESS_X, PRESS_Y))
        world.session.pressKey(Key.Escape)

        assertTrue(leavesWithinTheFade(world.session), "Escape did not close the context menu")
    }

    @Test
    fun choosingAnItemReportsItAndCloses() {
        val world = menuWorld()
        world.session.frame()
        world.session.frame(secondaryPressAt(PRESS_X, PRESS_Y))
        world.session.click("$MENU.item.1")

        assertEquals(1, world.selected, "the chosen item was not reported")
        assertTrue(leavesWithinTheFade(world.session), "choosing an item left the menu open")
    }

    private class MenuWorld {
        var selected: Int? = null
        lateinit var session: ComposeTestSession
    }

    private fun menuWorld(): MenuWorld {
        val world = MenuWorld()
        world.session = composeTestSession(VIEWPORT, VIEWPORT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                shadcnContextMenu(
                    entries = listOf("Back", "Reload", "Save as").map(::ShadcnMenuItem),
                    onItemSelected = { world.selected = it },
                    modifier = Modifier.fillMaxSize(),
                    // `id` tags the items; the surface is tagged through its own modifier.
                    menuModifier = Modifier.testTag(MENU),
                    id = MENU,
                )
            }
        }
        return world
    }

    private fun secondaryPressAt(x: Int, y: Int) =
        FrameInput(VIEWPORT, VIEWPORT, pointerX = x, pointerY = y, secondaryPointerPressed = true)

    /** A dismissed overlay is held on screen while it fades, so "closed" means gone soon, not now. */
    private fun leavesWithinTheFade(session: ComposeTestSession): Boolean {
        repeat(FADE_FRAMES) { if (menuNode(session) == null) return true }
        return false
    }

    private fun menuNode(session: ComposeTestSession): SemanticsNode? =
        session.frame().semantics.firstNotNullOfOrNull { it.find(MENU) }

    private fun SemanticsNode.find(tag: String): SemanticsNode? =
        if (testTag == tag) this else children.firstNotNullOfOrNull { it.find(tag) }

    private companion object {
        const val VIEWPORT = 400
        const val EDGE = 4
        const val PRESS_X = 60
        const val PRESS_Y = 80
        const val MENU = "context"
        const val FADE_FRAMES = 20
    }
}
