/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.studio.ui.dialogs

import com.awakekt.awake.compose.di.rememberResolveOrNull
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.heightIn
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.rememberScrollState
import com.awakekt.awake.compose.foundation.verticalScroll
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.studio.ui.theme.StudioThemeState
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.ShadcnAccent
import com.awakekt.awake.ui.shadcn.ShadcnBaseColor
import com.awakekt.awake.ui.shadcn.ShadcnStylePreset
import com.awakekt.awake.ui.shadcn.components.ShadcnBadge
import com.awakekt.awake.ui.shadcn.components.ShadcnBadgeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnCard
import com.awakekt.awake.ui.shadcn.components.ShadcnDialog
import com.awakekt.awake.ui.shadcn.components.ShadcnItem
import com.awakekt.awake.ui.shadcn.components.ShadcnSwitch
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

/**
 * Settings dialog for configuring Awake Studio preferences, themes, and styles.
 */
context(_: Composer)
internal fun StudioSettingsDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    themeState: StudioThemeState = rememberResolveOrNull<StudioThemeState>() ?: StudioThemeState(),
    modifier: Modifier = Modifier,
    width: Dp = 580.dp,
) {
    val scrollState = rememberScrollState()

    ShadcnDialog(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        width = width,
        id = "studio-settings-dialog",
    ) {
        header {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShadcnText("Settings", variant = ShadcnTextVariant.H3)
                        ShadcnBadge("THEME", variant = ShadcnBadgeVariant.Secondary)
                    }
                    shadcnMuted("Customize editor appearance, color schemes, and design presets.")
                }
                ShadcnButton(
                    label = "X",
                    variant = ShadcnButtonVariant.Ghost,
                    size = ShadcnButtonSizeVariant.Sm,
                    onClick = onDismissRequest,
                )
            }
        }
        content {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s4),
            ) {
                AppearanceCard(themeState)
                PresetCard(themeState)
                BaseColorCard(themeState)
                AccentCard(themeState)
                PreviewCard()
            }
        }
        footer {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShadcnButton("Done", onClick = onDismissRequest)
            }
        }
    }
}

context(_: Composer)
private fun AppearanceCard(themeState: StudioThemeState) {
    ShadcnCard(modifier = Modifier.fillMaxWidth()) {
        header {
            ShadcnText("Appearance", variant = ShadcnTextVariant.H4)
        }
        content {
            ShadcnItem(
                title = "Dark Mode",
                description = if (themeState.isDark) "Dark background and contrast surfaces enabled." else "Light background and clean surfaces enabled.",
                trailing = {
                    ShadcnSwitch(
                        checked = themeState.isDark,
                        onCheckedChange = { themeState.isDark = it },
                    )
                },
            )
        }
    }
}

context(_: Composer)
private fun PresetCard(themeState: StudioThemeState) {
    ShadcnCard(modifier = Modifier.fillMaxWidth()) {
        header {
            ShadcnText("Style Preset", variant = ShadcnTextVariant.H4)
            shadcnMuted("Select component border radius and density curves.")
        }
        content {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val presets = listOf(
                    ShadcnStylePreset.Vega,
                    ShadcnStylePreset.Nova,
                    ShadcnStylePreset.Maia,
                    ShadcnStylePreset.Lyra,
                    ShadcnStylePreset.Mira,
                    ShadcnStylePreset.Luma,
                )
                presets.forEach { preset ->
                    val isSelected = themeState.preset == preset
                    ShadcnButton(
                        label = preset.label,
                        variant = if (isSelected) ShadcnButtonVariant.Default else ShadcnButtonVariant.Outline,
                        size = ShadcnButtonSizeVariant.Sm,
                        onClick = { themeState.preset = preset },
                    )
                }
            }
        }
    }
}

context(_: Composer)
private fun BaseColorCard(themeState: StudioThemeState) {
    ShadcnCard(modifier = Modifier.fillMaxWidth()) {
        header {
            ShadcnText("Base Tint", variant = ShadcnTextVariant.H4)
            shadcnMuted("Base greyscale temperature for cards and borders.")
        }
        content {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val baseColors = listOf(
                    ShadcnBaseColor.Neutral,
                    ShadcnBaseColor.Zinc,
                    ShadcnBaseColor.Stone,
                    ShadcnBaseColor.Mauve,
                    ShadcnBaseColor.Olive,
                    ShadcnBaseColor.Mist,
                )
                baseColors.forEach { color ->
                    val isSelected = themeState.baseColor == color
                    ShadcnButton(
                        label = color.label,
                        variant = if (isSelected) ShadcnButtonVariant.Default else ShadcnButtonVariant.Outline,
                        size = ShadcnButtonSizeVariant.Sm,
                        onClick = { themeState.baseColor = color },
                    )
                }
            }
        }
    }
}

context(_: Composer)
private fun AccentCard(themeState: StudioThemeState) {
    ShadcnCard(modifier = Modifier.fillMaxWidth()) {
        header {
            ShadcnText("Accent Color", variant = ShadcnTextVariant.H4)
            shadcnMuted("Primary accent for selection rings and highlights.")
        }
        content {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val accents = listOf(
                    ShadcnAccent.Base,
                    ShadcnAccent.Blue,
                    ShadcnAccent.Emerald,
                    ShadcnAccent.Violet,
                    ShadcnAccent.Orange,
                    ShadcnAccent.Rose,
                    ShadcnAccent.Amber,
                )
                accents.forEach { accent ->
                    val isSelected = themeState.accent == accent
                    ShadcnButton(
                        label = accent.label,
                        variant = if (isSelected) ShadcnButtonVariant.Default else ShadcnButtonVariant.Outline,
                        size = ShadcnButtonSizeVariant.Sm,
                        onClick = { themeState.accent = accent },
                    )
                }
            }
        }
    }
}

context(_: Composer)
private fun PreviewCard() {
    ShadcnCard(modifier = Modifier.fillMaxWidth()) {
        header {
            ShadcnText("Live Component Preview", variant = ShadcnTextVariant.H4)
        }
        content {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShadcnButton("Primary", variant = ShadcnButtonVariant.Default, size = ShadcnButtonSizeVariant.Sm)
                ShadcnButton("Secondary", variant = ShadcnButtonVariant.Secondary, size = ShadcnButtonSizeVariant.Sm)
                ShadcnButton("Outline", variant = ShadcnButtonVariant.Outline, size = ShadcnButtonSizeVariant.Sm)
                ShadcnBadge("Active", variant = ShadcnBadgeVariant.Default)
                ShadcnBadge("Badge", variant = ShadcnBadgeVariant.Outline)
            }
        }
    }
}
