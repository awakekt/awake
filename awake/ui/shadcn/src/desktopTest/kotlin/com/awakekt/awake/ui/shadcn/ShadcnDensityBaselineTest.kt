/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.testing.assertMatchesBaseline
import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnCheckbox
import com.awakekt.awake.ui.shadcn.components.ShadcnDropdownMenu
import com.awakekt.awake.ui.shadcn.components.ShadcnFieldLegendVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnIcon
import com.awakekt.awake.ui.shadcn.components.ShadcnIcons
import com.awakekt.awake.ui.shadcn.components.ShadcnInput
import com.awakekt.awake.ui.shadcn.components.ShadcnLabel
import com.awakekt.awake.ui.shadcn.components.ShadcnMenuEntry
import com.awakekt.awake.ui.shadcn.components.ShadcnMenuItem
import com.awakekt.awake.ui.shadcn.components.ShadcnMenuSeparator
import com.awakekt.awake.ui.shadcn.components.ShadcnRadioGroup
import com.awakekt.awake.ui.shadcn.components.ShadcnSelectTrigger
import com.awakekt.awake.ui.shadcn.components.ShadcnSeparator
import com.awakekt.awake.ui.shadcn.components.ShadcnSeparatorOrientation
import com.awakekt.awake.ui.shadcn.components.ShadcnSwitch
import com.awakekt.awake.ui.shadcn.components.ShadcnTextarea
import com.awakekt.awake.ui.shadcn.components.ShadcnToggleGroup
import com.awakekt.awake.ui.shadcn.components.ShadcnToggleGroupVariant
import com.awakekt.awake.ui.shadcn.components.shadcnField
import com.awakekt.awake.ui.shadcn.components.shadcnFieldDescription
import com.awakekt.awake.ui.shadcn.components.shadcnFieldGroup
import com.awakekt.awake.ui.shadcn.components.shadcnFieldLegend
import com.awakekt.awake.ui.shadcn.components.shadcnFieldSet
import com.awakekt.awake.ui.shadcn.theme.LocalShadcnDensity
import com.awakekt.awake.ui.shadcn.theme.ShadcnDensity
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test

/**
 * A form, a menu and a toolbar under `LocalShadcnDensity`.
 *
 * The `Compact` baselines are what the density was added for. The form is also pinned at `Default`
 * from the same fixture, so a reviewer can lay the two beside each other and the diff between them
 * is the density and nothing else.
 */
class ShadcnDensityBaselineTest {

    @Test
    fun formAtDefault() = baseline("form-default", FORM_WIDTH, DEFAULT_FORM_HEIGHT, ShadcnDensity.Default) { form() }

    @Test
    fun formAtCompact() = baseline("form-compact", FORM_WIDTH, COMPACT_FORM_HEIGHT, ShadcnDensity.Compact) { form() }

    /** The same three items as the `Default` menu baseline, so the two compare row for row. */
    @Test
    fun menuAtCompact() = baseline("dropdown-menu-compact", 360, 160, ShadcnDensity.Compact) {
        ShadcnDropdownMenu(
            entries = listOf("Profile", "Billing", "Settings").map(::ShadcnMenuItem),
            modifier = Modifier.padding(all = 12.dp),
        )
    }

    /** A menu with the rest of what rows carry: a shortcut, a separator, a disabled and a destructive item. */
    @Test
    fun fullMenuAtCompact() = baseline("dropdown-menu-compact-full", 360, 220, ShadcnDensity.Compact) {
        val entries: List<ShadcnMenuEntry> = listOf(
            ShadcnMenuItem("Rename", shortcut = "F2"),
            ShadcnMenuItem("Duplicate", shortcut = "Ctrl+D"),
            ShadcnMenuItem("Move to...", enabled = false),
            ShadcnMenuSeparator,
            ShadcnMenuItem("Delete", destructive = true, shortcut = "Del"),
        )
        ShadcnDropdownMenu(entries, modifier = Modifier.padding(all = 12.dp))
    }

    @Test
    fun toolbarAtCompact() = baseline("toggle-group-compact-toolbar", TOOLBAR_WIDTH, TOOLBAR_HEIGHT, ShadcnDensity.Compact) {
        toolbar()
    }

    @Test
    fun toolbarAtDefault() = baseline("toggle-group-default-toolbar", TOOLBAR_WIDTH, TOOLBAR_HEIGHT, ShadcnDensity.Default) {
        toolbar()
    }

    context(_: Composer)
    private fun form() {
        val name = TextFieldState("Harbor Town")
        val notes = TextFieldState("")
        shadcnFieldSet(Modifier.padding(all = 16.dp)) {
            shadcnFieldLegend("Scene settings", variant = ShadcnFieldLegendVariant.Label)
            shadcnFieldGroup {
                shadcnField {
                    ShadcnLabel("Name")
                    ShadcnInput(name)
                    shadcnFieldDescription("Shown in the scene list.")
                }
                shadcnField {
                    ShadcnLabel("Environment")
                    ShadcnSelectTrigger("Daylight")
                }
                shadcnField {
                    ShadcnLabel("Notes")
                    ShadcnTextarea(notes)
                }
                shadcnField {
                    ShadcnLabel("Quality")
                    ShadcnRadioGroup(listOf("Draft", "Final"), selected = 0)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedByHorizontal(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShadcnCheckbox(checked = true)
                    ShadcnLabel("Cast shadows")
                    ShadcnSwitch(checked = true)
                    ShadcnLabel("Fog")
                }
                Row(horizontalArrangement = Arrangement.spacedByHorizontal(8.dp)) {
                    ShadcnButton("Cancel", variant = ShadcnButtonVariant.Outline)
                    ShadcnButton("Apply")
                }
            }
        }
    }

    context(_: Composer)
    private fun toolbar() {
        Row(
            Modifier.padding(all = 12.dp),
            horizontalArrangement = Arrangement.spacedByHorizontal(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnButton("Save", variant = ShadcnButtonVariant.Outline, leadingIcon = ShadcnIcons.save)
            ShadcnToggleGroup(
                selected = "move",
                onSelectedChange = {},
                variant = ShadcnToggleGroupVariant.Outline,
            ) {
                item("move", "Move", icon = ShadcnIcons.galleryVerticalEnd)
                item("rotate", "Rotate", icon = ShadcnIcons.camera)
                item("scale", "Scale", icon = ShadcnIcons.chevronsUpDown)
            }
            ShadcnSeparator(Modifier.padding(vertical = 4.dp), orientation = ShadcnSeparatorOrientation.Vertical)
            ShadcnSelectTrigger("Local", Modifier.width(96.dp))
            ShadcnButton(variant = ShadcnButtonVariant.Ghost, size = ShadcnButtonSizeVariant.IconSm) {
                ShadcnIcon(ShadcnIcons.camera)
            }
        }
    }

    private fun baseline(
        name: String,
        width: Int,
        height: Int,
        density: ShadcnDensity,
        content: context(Composer) () -> Unit,
    ) {
        composeFrame(width, height) {
            provideShadcnTheme(theme) {
                CompositionLocalProvider(LocalShadcnDensity provides density) {
                    Column { content() }
                }
            }
        }.assertMatchesBaseline(
            name,
            width = width,
            height = height,
            background = theme.palette.background,
            font = UiFonts.default(),
        )
    }

    private val theme = ShadcnThemeValues(ShadcnTheme)

    private companion object {
        const val FORM_WIDTH = 360
        const val DEFAULT_FORM_HEIGHT = 680
        const val COMPACT_FORM_HEIGHT = 520
        const val TOOLBAR_WIDTH = 480
        const val TOOLBAR_HEIGHT = 64
    }
}
