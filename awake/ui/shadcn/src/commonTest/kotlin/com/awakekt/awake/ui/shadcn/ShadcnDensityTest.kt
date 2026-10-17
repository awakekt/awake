/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.testing.ComposeComponentFrame
import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.compose.testing.composeTestSession
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonGroup
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnCheckbox
import com.awakekt.awake.ui.shadcn.components.ShadcnCombobox
import com.awakekt.awake.ui.shadcn.components.ShadcnComboboxItem
import com.awakekt.awake.ui.shadcn.components.ShadcnDropdownMenu
import com.awakekt.awake.ui.shadcn.components.ShadcnFieldLabel
import com.awakekt.awake.ui.shadcn.components.ShadcnInput
import com.awakekt.awake.ui.shadcn.components.ShadcnInputGroup
import com.awakekt.awake.ui.shadcn.components.ShadcnItem
import com.awakekt.awake.ui.shadcn.components.ShadcnLabel
import com.awakekt.awake.ui.shadcn.components.ShadcnMenuItem
import com.awakekt.awake.ui.shadcn.components.ShadcnPopover
import com.awakekt.awake.ui.shadcn.components.ShadcnRadioGroup
import com.awakekt.awake.ui.shadcn.components.ShadcnSelect
import com.awakekt.awake.ui.shadcn.components.ShadcnSelectItem
import com.awakekt.awake.ui.shadcn.components.ShadcnSelectTrigger
import com.awakekt.awake.ui.shadcn.components.ShadcnSwitch
import com.awakekt.awake.ui.shadcn.components.ShadcnTable
import com.awakekt.awake.ui.shadcn.components.ShadcnTableBody
import com.awakekt.awake.ui.shadcn.components.ShadcnTableCell
import com.awakekt.awake.ui.shadcn.components.ShadcnTableHead
import com.awakekt.awake.ui.shadcn.components.ShadcnTableHeader
import com.awakekt.awake.ui.shadcn.components.ShadcnTableRow
import com.awakekt.awake.ui.shadcn.components.ShadcnTabs
import com.awakekt.awake.ui.shadcn.components.ShadcnTabsSize
import com.awakekt.awake.ui.shadcn.components.ShadcnTabsVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextarea
import com.awakekt.awake.ui.shadcn.components.ShadcnToggle
import com.awakekt.awake.ui.shadcn.components.ShadcnToggleGroup
import com.awakekt.awake.ui.shadcn.components.ShadcnToggleGroupSize
import com.awakekt.awake.ui.shadcn.components.shadcnContextMenu
import com.awakekt.awake.ui.shadcn.components.shadcnField
import com.awakekt.awake.ui.shadcn.components.shadcnFieldDescription
import com.awakekt.awake.ui.shadcn.components.shadcnFieldGroup
import com.awakekt.awake.ui.shadcn.components.shadcnFieldSet
import com.awakekt.awake.ui.shadcn.theme.LocalShadcnDensity
import com.awakekt.awake.ui.shadcn.theme.ShadcnDensity
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `LocalShadcnDensity`: for each family of components, `Compact` is smaller than `Default`, and a
 * size passed explicitly wins over either.
 *
 * Sizes are read off the laid-out tree through a test tag on the component itself, so each number is
 * what a caller would get, not a constant the test shares with the recipe.
 */
class ShadcnDensityTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    private fun frame(
        density: ShadcnDensity?,
        width: Int = 400,
        height: Int = 400,
        content: context(Composer) () -> Unit,
    ): ComposeComponentFrame = composeFrame(width, height) {
        provideShadcnTheme(theme) { withDensity(density, content) }
    }

    /** [content] at [density], or with nothing in scope when it is null. */
    context(_: Composer)
    private fun withDensity(density: ShadcnDensity?, content: context(Composer) () -> Unit) {
        if (density == null) {
            content()
        } else {
            CompositionLocalProvider(LocalShadcnDensity provides density) { content() }
        }
    }

    private fun ComposeComponentFrame.height(tag: String): Int =
        onNodeWithTag(tag).getBoundsInRoot().height

    private fun ComposeComponentFrame.width(tag: String): Int =
        onNodeWithTag(tag).getBoundsInRoot().width

    private fun ComposeComponentFrame.top(tag: String): Int = onNodeWithTag(tag).getBoundsInRoot().top

    /** Asserts [tag] is shorter at `Compact` than `Default`, and that the unprovided density is `Default`. */
    private fun assertCompactIsShorter(
        tag: String,
        defaultHeight: Int,
        compactHeight: Int,
        content: context(Composer) () -> Unit,
    ) {
        val notProvided = frame(null, content = content).height(tag)
        val default = frame(ShadcnDensity.Default, content = content).height(tag)
        val compact = frame(ShadcnDensity.Compact, content = content).height(tag)

        assertEquals(defaultHeight, default, "$tag at Default")
        assertEquals(default, notProvided, "$tag with no density in scope must be its Default size")
        assertEquals(compactHeight, compact, "$tag at Compact")
        assertTrue(compact < default, "$tag is not smaller at Compact ($compact vs $default)")
    }

    // -- the local ------------------------------------------------------------------------------

    @Test
    fun theDensityDefaultsToDefaultWhenNothingProvidesOne() {
        var seen: ShadcnDensity? = null
        composeFrame(100, 100) {
            provideShadcnTheme(theme) { seen = LocalShadcnDensity.current }
        }
        assertEquals(ShadcnDensity.Default, seen)
    }

    @Test
    fun aNestedDensityWinsInsideItsOwnContent() {
        val tagOuter = "outer"
        val tagInner = "inner"
        val frame = frame(ShadcnDensity.Compact) {
            Column {
                ShadcnButton("Outer", Modifier.testTag(tagOuter))
                CompositionLocalProvider(LocalShadcnDensity provides ShadcnDensity.Default) {
                    ShadcnButton("Inner", Modifier.testTag(tagInner))
                }
            }
        }

        assertEquals(32, frame.height(tagOuter))
        assertEquals(36, frame.height(tagInner))
    }

    // -- buttons --------------------------------------------------------------------------------

    @Test
    fun aButtonWithNoSizeIsSmallerAtCompact() =
        assertCompactIsShorter("button", defaultHeight = 36, compactHeight = 32) {
            ShadcnButton("Save", Modifier.testTag("button"))
        }

    @Test
    fun aButtonHoldingContentIsSmallerAtCompact() =
        assertCompactIsShorter("button", defaultHeight = 36, compactHeight = 32) {
            ShadcnButton(Modifier.testTag("button")) { ShadcnText("Save") }
        }

    @Test
    fun aCompactButtonKeepsItsLabelSizeAndTightensNothingElse() {
        val default = frame(ShadcnDensity.Default) { ShadcnButton("Save", Modifier.testTag("b")) }
        val compact = frame(ShadcnDensity.Compact) { ShadcnButton("Save", Modifier.testTag("b")) }

        // `Sm` is `px-3` against `Default`'s `px-4`: two steps of 4dp on a label that did not change.
        assertEquals(default.width("b") - 8, compact.width("b"))
    }

    @Test
    fun anExplicitButtonSizeWinsAtEveryDensity() {
        ShadcnButtonSizeVariant.entries.forEach { size ->
            ShadcnDensity.entries.forEach { density ->
                val frame = frame(density) { ShadcnButton("Save", Modifier.testTag("b"), size = size) }
                assertEquals(size.height.value.toInt(), frame.height("b"), "$size at $density")
            }
        }
    }

    @Test
    fun anExplicitDefaultSizeIsNotMistakenForNoSize() {
        val frame = frame(ShadcnDensity.Compact) {
            ShadcnButton("Save", Modifier.testTag("b"), size = ShadcnButtonSizeVariant.Default)
        }

        assertEquals(36, frame.height("b"))
    }

    @Test
    fun anIconButtonKeepsTheIconSizeItIsGiven() {
        val frame = frame(ShadcnDensity.Compact) {
            Column {
                ShadcnButton(Modifier.testTag("icon"), size = ShadcnButtonSizeVariant.Icon) { ShadcnText("i") }
                ShadcnButton(Modifier.testTag("iconSm"), size = ShadcnButtonSizeVariant.IconSm) { ShadcnText("i") }
            }
        }

        assertEquals(36, frame.height("icon"))
        assertEquals(36, frame.width("icon"))
        assertEquals(32, frame.height("iconSm"))
        assertEquals(32, frame.width("iconSm"))
    }

    @Test
    fun aButtonGroupMemberFollowsTheDensityUnlessItIsSized() {
        // Members of a group stretch to the tallest, so each is measured in a group of its own.
        fun auto(density: ShadcnDensity?) = frame(density) {
            ShadcnButtonGroup { button("Save", modifier = Modifier.testTag("auto")) }
        }.height("auto")

        fun large(density: ShadcnDensity?) = frame(density) {
            ShadcnButtonGroup { button("Save", modifier = Modifier.testTag("lg"), size = ShadcnButtonSizeVariant.Lg) }
        }.height("lg")

        assertEquals(36, auto(ShadcnDensity.Default))
        assertEquals(36, auto(null))
        assertEquals(32, auto(ShadcnDensity.Compact))
        assertEquals(40, large(ShadcnDensity.Default))
        assertEquals(40, large(ShadcnDensity.Compact))
    }

    // -- text fields ----------------------------------------------------------------------------

    @Test
    fun anInputIsTwentyEightTallAtCompact() =
        assertCompactIsShorter("input", defaultHeight = 36, compactHeight = 28) {
            ShadcnInput(TextFieldState("Name"), Modifier.testTag("input"))
        }

    @Test
    fun aCallerSizedInputKeepsItsHeight() {
        val frame = frame(ShadcnDensity.Compact) {
            ShadcnInput(TextFieldState("Name"), Modifier.testTag("input").height(44.dp))
        }

        assertEquals(44, frame.height("input"))
    }

    @Test
    fun anInputGroupIsTwentyEightTallAtCompact() =
        assertCompactIsShorter("group", defaultHeight = 36, compactHeight = 28) {
            ShadcnInputGroup(TextFieldState("Name"), Modifier.testTag("group"))
        }

    @Test
    fun aTextareaStartsOneLineShorterAtCompact() =
        assertCompactIsShorter("textarea", defaultHeight = 64, compactHeight = 48) {
            ShadcnTextarea(TextFieldState(""), Modifier.testTag("textarea"))
        }

    @Test
    fun aCompactInputKeepsItsTextSize() {
        fun glyphHeight(density: ShadcnDensity) = frame(density) { ShadcnInput(TextFieldState("Name")) }
            .primitivesOf<DrawCommand.Glyph>().first().h

        assertEquals(glyphHeight(ShadcnDensity.Default), glyphHeight(ShadcnDensity.Compact))
    }

    // -- select and combobox --------------------------------------------------------------------

    @Test
    fun aSelectTriggerIsTwentyEightTallAtCompact() =
        assertCompactIsShorter("trigger", defaultHeight = 36, compactHeight = 28) {
            ShadcnSelectTrigger("Apple", Modifier.testTag("trigger"))
        }

    @Test
    fun aCallerSizedSelectTriggerKeepsItsHeight() {
        val frame = frame(ShadcnDensity.Compact) {
            ShadcnSelectTrigger("Apple", Modifier.testTag("trigger").height(44.dp))
        }

        assertEquals(44, frame.height("trigger"))
    }

    private fun openSelect(density: ShadcnDensity?): ComposeComponentFrame {
        val session = composeTestSession(width = 300, height = 300) {
            provideShadcnTheme(theme) {
                withDensity(density) {
                    ShadcnSelect(
                        items = listOf("Apple", "Banana", "Cherry").map(::ShadcnSelectItem),
                        selectedIndex = null,
                        expanded = true,
                        onExpandedChange = {},
                        onItemSelected = {},
                        id = "select",
                    )
                }
            }
        }
        return session.frame()
    }

    @Test
    fun aSelectRowIsTwentyEightTallAtCompact() {
        val default = openSelect(ShadcnDensity.Default).height("select.item.0")
        val compact = openSelect(ShadcnDensity.Compact).height("select.item.0")

        assertEquals(32, default)
        assertEquals(28, compact)
        assertEquals(default, openSelect(null).height("select.item.0"))
    }

    private fun openCombobox(density: ShadcnDensity?): ComposeComponentFrame {
        val session = composeTestSession(width = 300, height = 300) {
            provideShadcnTheme(theme) {
                withDensity(density) {
                    ShadcnCombobox(
                        items = listOf("Next.js", "Remix", "Astro").map(::ShadcnComboboxItem),
                        selectedIndex = null,
                        expanded = true,
                        onExpandedChange = {},
                        onItemSelected = {},
                        id = "combobox",
                    )
                }
            }
        }
        return session.frame()
    }

    @Test
    fun aComboboxFieldAndItsRowsAreSmallerAtCompact() {
        val default = openCombobox(ShadcnDensity.Default)
        val compact = openCombobox(ShadcnDensity.Compact)

        assertEquals(36, default.height("combobox.trigger"))
        assertEquals(28, compact.height("combobox.trigger"))
        assertEquals(32, default.height("combobox.item.0"))
        assertEquals(28, compact.height("combobox.item.0"))
    }

    // -- checkbox, switch, radio ----------------------------------------------------------------

    @Test
    fun aCheckboxIsFourteenAtCompact() =
        assertCompactIsShorter("checkbox", defaultHeight = 16, compactHeight = 14) {
            ShadcnCheckbox(checked = true, modifier = Modifier.testTag("checkbox"))
        }

    @Test
    fun aSwitchIsSmallerAtCompact() {
        fun switch(density: ShadcnDensity?) = frame(density) { ShadcnSwitch(true, Modifier.testTag("switch")) }

        val default = switch(ShadcnDensity.Default)
        val compact = switch(ShadcnDensity.Compact)

        assertEquals(32, default.width("switch"))
        assertEquals(28, compact.width("switch"))
        assertTrue(compact.height("switch") < default.height("switch"))
        assertEquals(default.height("switch"), switch(null).height("switch"))
    }

    @Test
    fun aRadioRingAndItsGroupGapAreSmallerAtCompact() {
        fun radios(density: ShadcnDensity?) = frame(density) { ShadcnRadioGroup(listOf("One", "Two"), selected = 0) }

        val default = radios(ShadcnDensity.Default)
        val compact = radios(ShadcnDensity.Compact)
        val gap = { f: ComposeComponentFrame ->
            f.top("parity-radio.1") - (f.top("parity-radio.0") + f.height("parity-radio.0"))
        }

        assertEquals(16, default.height("parity-radio.0"))
        assertEquals(14, compact.height("parity-radio.0"))
        assertEquals(12, gap(default))
        assertEquals(8, gap(compact))
        assertEquals(default.height("parity-radio.0"), radios(null).height("parity-radio.0"))
    }

    // -- toggle and toggle group ----------------------------------------------------------------

    @Test
    fun aToggleIsThirtyTwoAtCompact() =
        assertCompactIsShorter("toggle", defaultHeight = 40, compactHeight = 32) {
            ShadcnToggle("Bold", checked = false, modifier = Modifier.testTag("toggle"))
        }

    @Test
    fun aToggleGroupItemIsSmallerAtCompactUnlessItIsSized() {
        fun group(density: ShadcnDensity?, size: ShadcnToggleGroupSize?) = frame(density) {
            ShadcnToggleGroup(selected = null as String?, onSelectedChange = {}, size = size) {
                item("a", "Left", tag = "item")
            }
        }

        assertEquals(36, group(ShadcnDensity.Default, null).height("item"))
        assertEquals(36, group(null, null).height("item"))
        assertEquals(32, group(ShadcnDensity.Compact, null).height("item"))
        assertEquals(40, group(ShadcnDensity.Compact, ShadcnToggleGroupSize.Lg).height("item"))
        assertEquals(36, group(ShadcnDensity.Compact, ShadcnToggleGroupSize.Default).height("item"))
    }

    // -- tabs -----------------------------------------------------------------------------------

    private fun tabs(density: ShadcnDensity?, variant: ShadcnTabsVariant, size: ShadcnTabsSize?) = frame(density) {
        ShadcnTabs("one", onSelectedChange = {}, modifier = Modifier.testTag("tabs"), variant = variant, size = size) {
            tab("one", "One", tag = "tab")
            tab("two", "Two")
        }
    }

    @Test
    fun everyTabsVariantIsShorterAtCompactUnlessItIsSized() {
        ShadcnTabsVariant.entries.forEach { variant ->
            val default = tabs(ShadcnDensity.Default, variant, null).height("tabs")
            val compact = tabs(ShadcnDensity.Compact, variant, null).height("tabs")

            assertEquals(36, default, "$variant at Default")
            assertEquals(28, compact, "$variant at Compact")
            assertEquals(default, tabs(null, variant, null).height("tabs"), "$variant with no density")
            assertEquals(
                36,
                tabs(ShadcnDensity.Compact, variant, ShadcnTabsSize.Default).height("tabs"),
                "$variant: an explicit Default size lost to the density",
            )
        }
    }

    @Test
    fun aCompactTabTriggerStillHoldsItsLabel() {
        val pill = tabs(ShadcnDensity.Compact, ShadcnTabsVariant.Pill, null)

        assertTrue(pill.height("tab") >= 16 + 2, "a compact pill trigger is shorter than its text-xs line")
    }

    // -- menus ----------------------------------------------------------------------------------

    private fun menu(density: ShadcnDensity?) = frame(density) {
        ShadcnDropdownMenu(listOf("Profile", "Billing").map(::ShadcnMenuItem), id = "menu")
    }

    @Test
    fun aMenuRowIsTwentyEightTallAtCompact() {
        assertEquals(32, menu(ShadcnDensity.Default).height("menu.item.0"))
        assertEquals(32, menu(null).height("menu.item.0"))
        assertEquals(28, menu(ShadcnDensity.Compact).height("menu.item.0"))
    }

    @Test
    fun aContextMenuRowIsTwentyEightTallAtCompact() {
        fun open(density: ShadcnDensity): ComposeComponentFrame {
            val session = composeTestSession(300, 220) {
                provideShadcnTheme(theme) {
                    CompositionLocalProvider(LocalShadcnDensity provides density) {
                        shadcnContextMenu(
                            entries = listOf("Back", "Reload").map(::ShadcnMenuItem),
                            onItemSelected = {},
                            id = "context",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            session.frame()
            session.frame(FrameInput(300, 220, pointerX = 40, pointerY = 30, secondaryPointerPressed = true))
            repeat(SETTLE_FRAMES) { session.frame() }
            return session.frame()
        }

        assertEquals(32, open(ShadcnDensity.Default).height("context.item.0"))
        assertEquals(28, open(ShadcnDensity.Compact).height("context.item.0"))
    }

    // -- list rows and the command palette ------------------------------------------------------

    @Test
    fun aListRowIsShorterAtCompact() {
        fun row(density: ShadcnDensity?) = frame(density) {
            ShadcnItem(title = "Two-factor", description = "Add a layer.", modifier = Modifier.testTag("row"))
        }

        val default = row(ShadcnDensity.Default).height("row")
        val compact = row(ShadcnDensity.Compact).height("row")

        assertTrue(compact < default, "$compact vs $default")
        assertEquals(default, row(null).height("row"))
    }

    // -- tables ---------------------------------------------------------------------------------

    private fun table(density: ShadcnDensity?) = frame(density) {
        ShadcnTable {
            ShadcnTableHeader {
                ShadcnTableRow(Modifier.testTag("head-row")) { ShadcnTableHead("Invoice") }
            }
            ShadcnTableBody {
                row { ShadcnTableCell("INV001", Modifier.testTag("cell")) }
            }
        }
    }

    @Test
    fun aTableHeadAndCellAreShorterAtCompact() {
        assertEquals(40, table(ShadcnDensity.Default).height("head-row"))
        assertEquals(40, table(null).height("head-row"))
        assertEquals(32, table(ShadcnDensity.Compact).height("head-row"))

        assertEquals(36, table(ShadcnDensity.Default).height("cell"))
        assertEquals(32, table(ShadcnDensity.Compact).height("cell"))
    }

    // -- labels, descriptions and field spacing -------------------------------------------------

    @Test
    fun aLabelAndAFieldDescriptionAreTextXsAtCompact() {
        fun heights(density: ShadcnDensity?) = frame(density) {
            Column {
                ShadcnLabel("Name", Modifier.testTag("label"))
                ShadcnFieldLabel("Name", Modifier.testTag("field-label"))
                shadcnFieldDescription("Helps people find you.", Modifier.testTag("description"))
            }
        }

        val default = heights(ShadcnDensity.Default)
        val compact = heights(ShadcnDensity.Compact)

        assertEquals(20, default.height("label"))
        assertEquals(16, compact.height("label"))
        assertTrue(compact.height("field-label") < default.height("field-label"))
        assertTrue(compact.height("description") < default.height("description"))
        assertEquals(default.height("label"), heights(null).height("label"))
    }

    @Test
    fun aCompactLabelIsSmallerTypeNotJustASmallerBox() {
        fun glyphHeight(density: ShadcnDensity) = frame(density) { ShadcnLabel("Name") }
            .primitivesOf<DrawCommand.Glyph>().first().h

        assertTrue(glyphHeight(ShadcnDensity.Compact) < glyphHeight(ShadcnDensity.Default))
    }

    @Test
    fun fieldGapsTightenAtCompact() {
        fun stack(density: ShadcnDensity?) = frame(density) {
            shadcnFieldSet(Modifier.testTag("set")) {
                shadcnFieldGroup {
                    shadcnField {
                        ShadcnText("a", Modifier.testTag("a"))
                        ShadcnText("b", Modifier.testTag("b"))
                    }
                    shadcnField { ShadcnText("c", Modifier.testTag("c")) }
                }
            }
        }

        fun fieldGap(f: ComposeComponentFrame) = f.top("b") - (f.top("a") + f.height("a"))
        fun groupGap(f: ComposeComponentFrame) = f.top("c") - (f.top("b") + f.height("b"))

        assertEquals(12, fieldGap(stack(ShadcnDensity.Default)))
        assertEquals(8, fieldGap(stack(ShadcnDensity.Compact)))
        assertEquals(28, groupGap(stack(ShadcnDensity.Default)))
        assertEquals(20, groupGap(stack(ShadcnDensity.Compact)))
        assertEquals(12, fieldGap(stack(null)))
    }

    @Test
    fun aPopoverPadsItsContentLessAtCompact() {
        fun inset(density: ShadcnDensity?) = frame(density) {
            ShadcnPopover(Modifier.testTag("popover")) { ShadcnText("Body", Modifier.testTag("body")) }
        }.let { it.top("body") - it.top("popover") }

        // `p-4` plus the 1px border, and `p-3` plus it.
        assertEquals(17, inset(ShadcnDensity.Default))
        assertEquals(13, inset(ShadcnDensity.Compact))
        assertEquals(17, inset(null))
    }

    // -- Default is exactly what there was before -----------------------------------------------

    @Test
    fun providingDefaultDrawsExactlyWhatProvidingNothingDraws() {
        val content: context(Composer)
        () -> Unit = {
            Column {
                ShadcnButton("Save")
                ShadcnButton("Sm", size = ShadcnButtonSizeVariant.Sm)
                ShadcnInput(TextFieldState("Name"))
                ShadcnTextarea(TextFieldState("Notes"))
                ShadcnSelectTrigger("Apple")
                ShadcnCheckbox(checked = true)
                ShadcnSwitch(checked = true)
                ShadcnRadioGroup(listOf("One", "Two"), selected = 1)
                ShadcnToggle("Bold", checked = true)
                ShadcnLabel("Label")
                ShadcnItem(title = "Row", description = "Detail")
                ShadcnDropdownMenu(listOf("Profile", "Billing").map(::ShadcnMenuItem), id = "menu")
            }
        }

        assertEquals(
            frame(null, content = content).primitives,
            frame(ShadcnDensity.Default, content = content).primitives,
        )
    }

    @Test
    fun compactDrawsSomethingDifferentFromDefault() {
        val content: context(Composer)
        () -> Unit = {
            Column {
                ShadcnButton("Save")
                ShadcnInput(TextFieldState("Name"))
            }
        }

        assertTrue(frame(ShadcnDensity.Default, content = content).primitives != frame(ShadcnDensity.Compact, content = content).primitives)
    }

    private companion object {
        /** Long enough for a popup opened by a flag to reach full opacity. */
        const val SETTLE_FRAMES = 20
    }
}
