/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.uishowcase.ui.pages.gettingstarted

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.state.UiShowcaseRuntimeState
import com.awakekt.awake.sample.uishowcase.state.UiShowcaseThemeMode
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseSectionTitle
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseTextLines
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.ShadcnAccent
import com.awakekt.awake.ui.shadcn.ShadcnBaseColor
import com.awakekt.awake.ui.shadcn.ShadcnStylePreset
import com.awakekt.awake.ui.shadcn.components.ShadcnBadge
import com.awakekt.awake.ui.shadcn.components.ShadcnBadgeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnCard
import com.awakekt.awake.ui.shadcn.components.ShadcnFieldLabel
import com.awakekt.awake.ui.shadcn.components.ShadcnSlider
import com.awakekt.awake.ui.shadcn.components.ShadcnSwitch
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.shadcnMuted
import com.awakekt.awake.ui.shadcn.components.shadcnSurface

context(_: Composer)
internal fun ShowcaseOverviewPreview() {
    ShadcnBadge("SHOWCASE", variant = ShadcnBadgeVariant.Secondary)
    ShadcnText("Dedicated sample route")
    shadcnMuted("This page shell exists so the design system is judged as a product surface, not just as loose demo widgets.")
    Spacer(Modifier.height(8.dp))
    ShowcaseTextLines(
        listOf(
            "Stable chrome on top, grouped navigation on the left, one detail page in the content pane.",
            "The starter sample stays a starter sample; docs and polish move here.",
            "This is now the right home for future design-system tutorials and regression proofs.",
        ),
    )
}

context(_: Composer)
internal fun ShowcaseReferenceComparisonPreview() {
    Row(horizontalArrangement = Arrangement.spacedByHorizontal(12.dp)) {
        ShadcnCard(Modifier.width(280.dp).height(180.dp)) {
            Column(Modifier.padding(horizontal = Tw.Spacing.s6)) {
                ShowcaseSectionTitle("Reference cues")
                Spacer(Modifier.height(8.dp))
                shadcnMuted("Compact controls and restrained surfaces.")
            }
        }
        ShadcnCard(Modifier.width(280.dp).height(180.dp)) {
            Column(Modifier.padding(horizontal = Tw.Spacing.s6)) {
                ShowcaseSectionTitle("Awake")
                Spacer(Modifier.height(8.dp))
                shadcnMuted("The same structure rendered through the public compose boundary.")
            }
        }
    }
}

/**
 * Proves the theme factory re-skins the whole component library live.
 *
 * The four upstream selects (style/base/mode/accent) cycle through their options on click rather
 * than opening a popup: `ShadcnSelectTrigger` on the compose engine is the trigger box only, with no
 * options list wired up yet. Cycling is the same substitute Studio's own viewport toolbar uses for
 * its camera-mode button, for the same reason -- a real picker is future work, and a page whose
 * whole point is proving live re-theming should not sit blocked on it.
 */
context(_: Composer)
internal fun ShowcaseControlsPreview(state: UiShowcaseRuntimeState) {
    shadcnMuted("This page proves that the Awake theme factory can re-skin the entire component library live.")
    Spacer(Modifier.height(16.dp))

    Row(horizontalArrangement = Arrangement.spacedByHorizontal(24.dp)) {
        shadcnSurface(Modifier.width(320.dp)) {
            ShowcaseSectionTitle("Theme Settings")
            shadcnMuted("Configure the look and feel.")
            Spacer(Modifier.height(12.dp))

            ThemeCycleRow(
                "Style",
                ShowcaseStyleOptions[state.showcaseStylePresetIndex],
            ) {
                state.showcaseStylePresetIndex =
                    (state.showcaseStylePresetIndex + 1) % ShowcaseStyleOptions.size
            }
            ThemeCycleRow(
                "Base",
                ShowcaseBaseColorOptions[state.showcaseBaseColorIndex],
            ) {
                state.showcaseBaseColorIndex =
                    (state.showcaseBaseColorIndex + 1) % ShowcaseBaseColorOptions.size
            }
            ThemeCycleRow(
                "Mode",
                ShowcaseThemeModeOptions[state.showcaseThemeModeIndex],
            ) {
                state.showcaseThemeModeIndex =
                    (state.showcaseThemeModeIndex + 1) % ShowcaseThemeModeOptions.size
            }
            ThemeCycleRow(
                "Accent",
                ShowcaseAccentOptions[state.showcaseAccentIndex],
            ) {
                state.showcaseAccentIndex =
                    (state.showcaseAccentIndex + 1) % ShowcaseAccentOptions.size
            }

            Spacer(Modifier.height(8.dp))
            shadcnMuted("Mode auto-resolves to ${if (state.showcaseResolvedDarkMode()) "dark" else "light"} on this platform.")
            Spacer(Modifier.height(12.dp))
            ThemeSwitchRow("Live animation", state.showcaseLiveBadge) {
                state.showcaseLiveBadge = it
            }
            ThemeSwitchRow(
                "Danger treatment",
                state.showcaseDangerMode,
            ) { state.showcaseDangerMode = it }
        }

        ShowcaseColumnPreview(state)
    }
}

context(_: Composer)
private fun ThemeCycleRow(label: String, value: String, onCycle: () -> Unit) {
    Row(
        Modifier.width(320.dp),
        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnFieldLabel(label, Modifier.width(72.dp))
        ShadcnButton(
            value,
            Modifier.width(180.dp),
            variant = ShadcnButtonVariant.Outline,
            onClick = onCycle,
        )
    }
}

context(_: Composer)
private fun ThemeSwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.width(320.dp),
        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnFieldLabel(label, Modifier.width(132.dp))
        ShadcnSwitch(checked, onCheckedChange = onChecked)
    }
}

context(_: Composer)
private fun ShowcaseColumnPreview(state: UiShowcaseRuntimeState) {
    // Keep the two-column structure from the reference page: the preview has a fixed width,
    // while the settings panel owns the controls and their state.
    Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ShadcnBadge("LIVE PREVIEW", variant = ShadcnBadgeVariant.Secondary)
        shadcnSurface(Modifier.width(420.dp)) { ShowcasePreviewCard(state) }
        ShowcaseRadiusRow(state)
    }
}

context(_: Composer)
private fun ShowcasePreviewCard(state: UiShowcaseRuntimeState) {
    Row(
        Modifier.width(420.dp),
        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnBadge(
            if (state.showcaseLiveBadge) "LIVE" else "PAUSED",
            variant = if (state.showcaseLiveBadge) ShadcnBadgeVariant.Default else ShadcnBadgeVariant.Outline,
        )
        if (state.showcaseDangerMode) {
            ShadcnBadge("DANGER", variant = ShadcnBadgeVariant.Destructive)
        }
    }
    Spacer(Modifier.height(8.dp))
    ShadcnText("Showcase Preview Card")
    shadcnMuted(
        if (state.showcaseDangerMode) {
            "DANGER MODE: Thematic variant proof for destructive/alert states."
        } else {
            "LIVE PROOF: Animation state proof using conditional canvas shimmer."
        },
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedByHorizontal(10.dp)) {
        ShadcnButton(
            "Inspect",
            Modifier.width(100.dp),
            variant = ShadcnButtonVariant.Default,
            onClick = { state.showcasePrimaryClicks += 1 },
        )
        ShadcnButton(
            if (state.showcaseDangerMode) "Rollback" else "Publish",
            Modifier.width(100.dp),
            variant = if (state.showcaseDangerMode) ShadcnButtonVariant.Destructive else ShadcnButtonVariant.Outline,
        )
    }
    Spacer(Modifier.height(8.dp))
    shadcnMuted("Interaction proof: ${state.showcasePrimaryClicks} clicks")
}

context(_: Composer)
private fun ShowcaseRadiusRow(state: UiShowcaseRuntimeState) {
    shadcnSurface(Modifier.width(420.dp)) {
        Row(
            Modifier.width(420.dp),
            horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnFieldLabel("Corner Radius", Modifier.width(120.dp))
            ShadcnSlider(
                value = state.showcaseSurfaceRadius,
                modifier = Modifier.width(240.dp),
                min = 0f,
                max = 32f,
                onValueChange = { state.showcaseSurfaceRadius = it },
            )
        }
    }
}

internal val ShowcaseStyleOptions = ShadcnStylePreset.entries.map { it.label }
internal val ShowcaseBaseColorOptions = ShadcnBaseColor.entries.map { it.label }
internal val ShowcaseAccentOptions = ShadcnAccent.entries.map { it.label }
internal val ShowcaseThemeModeOptions = UiShowcaseThemeMode.entries.map { it.label }
