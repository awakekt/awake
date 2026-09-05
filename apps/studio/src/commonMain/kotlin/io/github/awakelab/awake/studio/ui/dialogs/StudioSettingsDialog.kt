/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.studio.ui.dialogs

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.heightIn
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.rememberScrollState
import io.github.awakelab.awake.compose.foundation.verticalScroll
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.studio.ui.theme.StudioThemeState
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.ShadcnAccent
import io.github.awakelab.awake.ui.shadcn.ShadcnBaseColor
import io.github.awakelab.awake.ui.shadcn.ShadcnStylePreset
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadge
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadgeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCard
import io.github.awakelab.awake.ui.shadcn.components.ShadcnDialog
import io.github.awakelab.awake.ui.shadcn.components.ShadcnItem
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSwitch
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted

/**
 * Settings dialog for configuring Awake Studio preferences, themes, and styles.
 */
context(_: Composer)
internal fun StudioSettingsDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    themeState: StudioThemeState,
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
