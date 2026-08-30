/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.testing.composeFrame
import io.github.awakelab.awake.compose.testing.composeTestSession
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.input.key.KeyEvent
import io.github.awakelab.awake.compose.ui.input.key.KeyEventType
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.input.Key
import io.github.awakelab.awake.core.graphics2d.DrawCommand
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenuItem
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenuSeparator
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSelect
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSelectItem
import io.github.awakelab.awake.ui.shadcn.components.shadcnDropdownMenu
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.shadcnPopover
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSelectTrigger
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadcnOverlaysTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    // -- the shared surface ---------------------------------------------------------------------

    @Test
    fun allThreeFloatingSurfacesUsePopoverNotCard() {
        // Upstream spells them identically: `bg-popover text-popover-foreground`. Reaching for
        // `card` gives a menu that matches the page's panels instead of floating above them.
        val popover = composeFrame(400, 200) {
            provideShadcnTheme(theme) { shadcnPopover { ShadcnText("Body") } }
        }.primitivesOf<DrawCommand.RoundedQuad>().first()

        val menu = composeFrame(400, 200) {
            provideShadcnTheme(theme) { shadcnDropdownMenu(listOf(ShadcnMenuItem("One"))) }
        }.primitivesOf<DrawCommand.RoundedQuad>().first()

        assertEquals(theme.palette.popover, popover.color)
        assertEquals(theme.palette.popover, menu.color)
    }

    @Test
    fun theContentInsetFoldsInTheBorderWidth() {
        // CSS is border-box: a `p-1` surface with a `border` insets content by 5px, not 4. Getting
        // this wrong put the old menu's items at x=4 in a 160px surface where the reference says
        // x=5 -- one pixel a side, invisible by eye, caught only by the parity report.
        val frame = composeFrame(400, 200) {
            provideShadcnTheme(theme) { shadcnDropdownMenu(listOf(ShadcnMenuItem("One"))) }
        }
        val surface = frame.primitivesOf<DrawCommand.RoundedQuad>().first()
        val firstGlyph = frame.primitivesOf<DrawCommand.Glyph>().first()

        val inset = firstGlyph.x - surface.x
        val expected = Tw.Spacing.s1.value + 1f + Tw.Spacing.s2.value
        assertTrue(
            inset >= expected - 1.5f,
            "content inset $inset is under p-1 + border + px-2 ($expected)",
        )
    }

    @Test
    fun aPopoverIsAFixedWidthNotAMinimum() {
        // `w-72`. A popover is 288 whatever is in it, which is what keeps a row of them aligned.
        fun width(text: String) = composeFrame(500, 200) {
            provideShadcnTheme(theme) { shadcnPopover { ShadcnText(text) } }
        }.primitivesOf<DrawCommand.RoundedQuad>().first().w

        assertEquals(288f, width("x"), 0.5f, "a short popover shrank below w-72")
        assertEquals(width("x"), width("a much longer body of text"), 0.5f, "it sized to content")
    }

    // -- dropdown menu --------------------------------------------------------------------------

    @Test
    fun aDestructiveItemIsRedWithoutARedRow() {
        // `text-destructive` at rest -- the tinted highlight is `focus:bg-destructive/10`, so an
        // unfocused destructive row is red type on the normal surface, not a red band.
        val frame = composeFrame(400, 200) {
            provideShadcnTheme(theme) {
                shadcnDropdownMenu(listOf(ShadcnMenuItem("Delete", destructive = true)))
            }
        }
        val label = frame.primitivesOf<DrawCommand.Glyph>().first()
        val fills = frame.primitivesOf<DrawCommand.RoundedQuad>()

        assertEquals(theme.palette.destructive.r, label.color.r, 0.001f, "the item is not red")
        // The exact colour the highlight would be, not "something reddish": matching on the red
        // channel alone also matches the surface, which is what the first version of this did.
        val highlight = theme.palette.destructive.withAlpha(0.1f)
        assertTrue(
            fills.none { it.color == highlight },
            "an unfocused destructive item painted its tinted highlight",
        )
    }

    @Test
    fun aDisabledItemIsDimmedNotRemoved() {
        val glyphs = composeFrame(400, 200) {
            provideShadcnTheme(theme) {
                shadcnDropdownMenu(listOf(ShadcnMenuItem("Nope", enabled = false)))
            }
        }.primitivesOf<DrawCommand.Glyph>()

        assertTrue(glyphs.isNotEmpty(), "a disabled item vanished")
        assertTrue(glyphs.first().color.a < 1f, "a disabled item was not dimmed")
    }

    @Test
    fun aSeparatorIsDrawnAndIsNotAnItem() {
        // The reason the ported recipe took the existing sealed hierarchy instead of a flat list: a
        // menu genuinely has separators, and a List<String> cannot say so.
        val frame = composeFrame(400, 200) {
            provideShadcnTheme(theme) {
                shadcnDropdownMenu(
                    listOf(ShadcnMenuItem("One"), ShadcnMenuSeparator, ShadcnMenuItem("Two")),
                )
            }
        }

        assertTrue(
            frame.primitivesOf<DrawCommand.Quad>().any { it.color == theme.palette.border },
            "the separator drew no rule",
        )
    }

    @Test
    fun theMenuHonoursItsMinimumWidth() {
        // `min-w-[8rem]` -- a one-word menu is still 128 wide, so a set of them lines up.
        val surface = composeFrame(400, 200) {
            provideShadcnTheme(theme) { shadcnDropdownMenu(listOf(ShadcnMenuItem("Hi"))) }
        }.primitivesOf<DrawCommand.RoundedQuad>().first()

        assertTrue(surface.w >= 128f - 0.5f, "the menu collapsed to ${surface.w}, under min-w-[8rem]")
    }

    @Test
    fun controlledMenuOpensBelowItsTriggerThenClosesAfterSelection() {
        var expanded = false
        var selected: Int? = null
        val session = composeTestSession(width = 300, height = 200) {
            provideShadcnTheme(theme) {
                shadcnDropdownMenu(
                    entries = listOf(ShadcnMenuItem("One"), ShadcnMenuItem("Two")),
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onItemSelected = { selected = it },
                    id = "menu",
                ) { onClick ->
                    ShadcnButton("Open", modifier = Modifier.testTag("menu.trigger"), onClick = onClick)
                }
            }
        }

        session.frame()
        val openFrame = session.click("menu.trigger")
        val firstItem = openFrame.onNodeWithTag("menu.item.0").getBoundsInRoot()

        assertTrue(firstItem.top >= 40, "menu opened over its trigger: $firstItem")
        session.click("menu.item.1")
        val closedFrame = session.frame()

        assertEquals(1, selected)
        assertTrue(
            closedFrame.flatSemantics().none { it.testTag == "menu.item.0" },
            "the selected menu remained in the layer",
        )
    }

    @Test
    fun controlledMenuDismissesOnOutsidePressAndEscape() {
        var expanded = false
        val session = composeTestSession(width = 300, height = 200) {
            provideShadcnTheme(theme) {
                shadcnDropdownMenu(
                    entries = listOf(ShadcnMenuItem("One")),
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onItemSelected = {},
                    id = "menu",
                ) { onClick ->
                    ShadcnButton("Open", modifier = Modifier.testTag("menu.trigger"), onClick = onClick)
                }
            }
        }

        session.frame()
        session.click("menu.trigger")
        session.frame(FrameInput(300, 200, pointerX = 250, pointerY = 150, pointerDown = true))
        val outsideClosed = session.frame(FrameInput(300, 200, pointerX = 250, pointerY = 150))

        assertTrue(outsideClosed.flatSemantics().none { it.testTag == "menu.item.0" })
        session.click("menu.trigger")
        session.frame(
            FrameInput(300, 200, keyEvents = listOf(KeyEvent(Key.Escape, KeyEventType.Down))),
        )
        val escapeClosed = session.frame()

        assertTrue(escapeClosed.flatSemantics().none { it.testTag == "menu.item.0" })
    }

    @Test
    fun controlledMenuSelectsTheRovingKeyboardEntry() {
        var expanded = false
        var selected: Int? = null
        val session = composeTestSession(width = 300, height = 200) {
            provideShadcnTheme(theme) {
                shadcnDropdownMenu(
                    entries = listOf(ShadcnMenuItem("One"), ShadcnMenuItem("Two")),
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onItemSelected = { selected = it },
                ) { onClick ->
                    ShadcnButton("Open", modifier = Modifier.testTag("menu.trigger"), onClick = onClick)
                }
            }
        }

        session.frame()
        session.click("menu.trigger")
        session.frame(FrameInput(300, 200, keyEvents = listOf(KeyEvent(Key.ArrowDown, KeyEventType.Down))))
        session.frame()
        session.frame(FrameInput(300, 200, keyEvents = listOf(KeyEvent(Key.ArrowDown, KeyEventType.Down))))
        session.frame()
        session.frame(FrameInput(300, 200, keyEvents = listOf(KeyEvent(Key.Enter, KeyEventType.Down))))
        val closed = session.frame()

        assertEquals(1, selected)
        assertTrue(closed.flatSemantics().none { it.label == "One" || it.label == "Two" })
    }

    // -- select ---------------------------------------------------------------------------------

    @Test
    fun aPlaceholderIsMutedAndAChosenValueIsNot() {
        // `data-[placeholder]` is a real state. Collapsing it removes the only cue that nothing has
        // been chosen.
        fun labelColour(value: String?) = composeFrame(300, 80) {
            provideShadcnTheme(theme) { ShadcnSelectTrigger(value) }
        }.primitivesOf<DrawCommand.Glyph>().first().color

        assertEquals(theme.palette.mutedForeground, labelColour(null), "the placeholder is not muted")
        assertEquals(theme.palette.foreground, labelColour("Apple"), "a chosen value is muted")
    }

    @Test
    fun theChevronStaysMutedEvenWithAValue() {
        // It is chrome, not content, so it stays quiet while the value reads normally.
        val frame = composeFrame(300, 80) {
            provideShadcnTheme(theme) { ShadcnSelectTrigger("Apple") }
        }

        // Both the chevron and the trigger's border tessellate, so this asserts the chevron's
        // colour is present and the content colour is not, rather than naming the whole set.
        val painted = frame.meshColors()
        assertTrue(theme.palette.mutedForeground in painted, "the chevron is not muted")
        assertTrue(theme.palette.foreground !in painted, "the chevron took the foreground colour")
    }

    @Test
    fun theSelectTriggerIsThirtySixTallLikeAnInput() {
        val quad = composeFrame(300, 80) {
            provideShadcnTheme(theme) { ShadcnSelectTrigger("Apple") }
        }.primitivesOf<DrawCommand.RoundedQuad>().first()

        assertEquals(36f, quad.h, 0.5f, "the trigger is not h-9")
    }

    @Test
    fun clickingTheBlankRightSideOfASelectTriggerInvokesItsHandler() {
        var clicks = 0
        val session = composeTestSession(width = 300, height = 80) {
            provideShadcnTheme(theme) {
                ShadcnSelectTrigger(
                    "Apple",
                    modifier = Modifier.width(168.dp).testTag("select"),
                    onClick = { clicks++ },
                )
            }
        }

        val initial = session.frame()
        val bounds = initial.onNodeWithTag("select").getBoundsInRoot()
        val x = bounds.right - 2
        val y = bounds.top + bounds.height / 2
        session.frame(FrameInput(300, 80, pointerX = x, pointerY = y, pointerDown = true))
        session.frame(FrameInput(300, 80, pointerX = x, pointerY = y))

        assertEquals(1, clicks, "the select trigger ignored a click in its right-side field padding")
    }

    @Test
    fun controlledSelectOpensItsScrollableOptionsAndCommitsTheClickedItem() {
        var expanded = false
        var selected = 1
        val session = composeTestSession(width = 300, height = 260) {
            provideShadcnTheme(theme) {
                Column {
                    Spacer(Modifier.height(80.dp))
                    ShadcnSelect(
                        items = listOf("Apple", "Banana", "Blueberry", "Grapes", "Pineapple").map(::ShadcnSelectItem),
                        selectedIndex = selected,
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        onItemSelected = { selected = it },
                        id = "select",
                        modifier = Modifier.width(172.dp).testTag("select.trigger"),
                    )
                }
            }
        }

        session.frame()
        session.click("select.trigger")
        val open = session.frame()
        val trigger = open.onNodeWithTag("select.trigger").getBoundsInRoot()
        val selectedItem = open.onNodeWithTag("select.item.1").getBoundsInRoot()
        assertTrue(open.flatSemantics().any { it.testTag == "select.item.0" }, "the prior option was not retained in the scrollable Select viewport")
        assertTrue(open.flatSemantics().any { it.testTag == "select.item.2" }, "the later option was not retained in the Select viewport")
        assertTrue(
            selectedItem.top < trigger.bottom && selectedItem.bottom > trigger.top,
            "the selected option was not item-aligned with the trigger: trigger=$trigger item=$selectedItem",
        )

        session.click("select.item.2")
        val closed = session.frame()
        assertEquals(2, selected)
        assertTrue(closed.flatSemantics().none { it.testTag == "select.content" }, "Select content remained after selection")
    }

    @Test
    fun selectKeyboardNavigationSkipsDisabledItemsAndSupportsEscape() {
        var expanded = false
        var selected: Int? = null
        val session = composeTestSession(width = 300, height = 260) {
            provideShadcnTheme(theme) {
                ShadcnSelect(
                    items = listOf(
                        ShadcnSelectItem("Apple"),
                        ShadcnSelectItem("Banana", enabled = false),
                        ShadcnSelectItem("Cherry"),
                    ),
                    selectedIndex = selected,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onItemSelected = { selected = it },
                    id = "keyboard-select",
                    modifier = Modifier.width(172.dp).testTag("keyboard-select.trigger"),
                )
            }
        }

        session.frame()
        session.click("keyboard-select.trigger")
        session.pressKey(Key.ArrowDown)
        session.pressKey(Key.Enter)
        assertEquals(0, selected, "ArrowDown did not wrap to the first enabled option")
        assertTrue(!expanded, "Enter did not close the Select")

        session.pressKey(Key.Enter)
        assertTrue(expanded, "Enter did not reopen the Select")
        session.pressKey(Key.Escape)
        assertTrue(!expanded, "Escape did not dismiss the Select")
    }

    @Test
    fun overflowingSelectExposesBothScrollControls() {
        var expanded = true
        val session = composeTestSession(width = 300, height = 260) {
            provideShadcnTheme(theme) {
                ShadcnSelect(
                    items = (0..8).map { ShadcnSelectItem("Option $it") },
                    selectedIndex = 3,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onItemSelected = {},
                    id = "scroll-select",
                    modifier = Modifier.width(172.dp).testTag("scroll-select.trigger"),
                )
            }
        }

        val open = session.frame()
        assertTrue(open.flatSemantics().any { it.testTag == "scroll-select.scroll-up" })
        assertTrue(open.flatSemantics().any { it.testTag == "scroll-select.scroll-down" })

        val before = open.onNodeWithTag("scroll-select.item.0").getBoundsInRoot()
        session.click("scroll-select.scroll-down")
        val afterDown = session.frame().onNodeWithTag("scroll-select.item.0").getBoundsInRoot()
        assertTrue(afterDown.top < before.top, "the scroll-down control did not move the options")

        session.click("scroll-select.scroll-up")
        val afterUp = session.frame().onNodeWithTag("scroll-select.item.0").getBoundsInRoot()
        assertTrue(afterUp.top > afterDown.top, "the scroll-up control did not move the options back")
    }
}
