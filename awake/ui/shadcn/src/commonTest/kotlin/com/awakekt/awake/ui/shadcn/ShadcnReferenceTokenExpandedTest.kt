/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.core.color.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every comparable resolved token for every [ShadcnBaseColor], both modes, against
 * [ShadcnReferenceTokens] -- real shadcn/ui OKLCH CSS vars machine-extracted from a pinned upstream
 * commit (`tools/shadcn/fetch_shadcn_reference.sh` + `tools/shadcn/extract_shadcn_tokens.py`, see
 * `docs/reference/shadcn-reference-pipeline.md`).
 *
 * **This check inverted when the palette stopped deriving these tokens and started looking them up.**
 * It used to ask "does our derivation match shadcn", and the answer was no for six of the seven base
 * colours -- shadcn hand-tunes hue *and* chroma per token within one base colour, which a single
 * hue/chroma pair cannot reproduce. Thirteen `knownDrifted` entries locked that wrongness in place
 * so it could not get worse silently.
 *
 * `createPalette` now reads the same table, so the drift is zero by construction and all thirteen
 * entries are gone. What this asserts today is that the shipped table still reaches the palette
 * intact -- that no accent override, alpha multiplier or lookup key quietly changes a value between
 * the extraction and the resolved theme.
 *
 * Tokens skipped, not force-compared against nothing:
 * - `destructive-foreground`: shadcn has no such var at all (its destructive buttons hardcode white
 *   through Tailwind); Awake computes one because there is nothing upstream to look up.
 * - `chart-1`..`chart-5`: extracted, modelled by nothing here.
 * Interaction states are not listed because they no longer exist as tokens: recipes apply alpha at
 * the use site (`primary.withAlpha(0.9f)`), which is what shadcn's `hover:bg-primary/90` does.
 */
class ShadcnReferenceTokenExpandedTest {

    // Same comparison approach as before: sum of abs channel deltas in resolved sRGB space, not
    // raw OKLCH, so it tolerates float rounding through the OKLCH->linear-sRGB->gamma conversion.
    private val tolerance = 0.02f

    @Test
    fun lightTokensMatchReferenceOrLockedDriftForEveryBaseColor() {
        for (baseColor in ShadcnBaseColor.values()) assertBaseColorMode(baseColor, dark = false)
    }

    @Test
    fun darkTokensMatchReferenceOrLockedDriftForEveryBaseColor() {
        for (baseColor in ShadcnBaseColor.values()) assertBaseColorMode(baseColor, dark = true)
    }

    @Test
    fun defaultRadiusMatchesReferenceSpec() {
        // Vega is the only preset with a correctness obligation to the real shadcn reference --
        // see ShadcnStylePresetVerificationTest for the other 7 (Awake-original, no upstream
        // counterpart) presets' own structural/regression coverage.
        val oursDp = shadcnThemeValues().radii.lg.value
        val referenceDp = ShadcnReferenceTokens.RADIUS_REM * 16f
        assertTrue(
            abs(oursDp - referenceDp) < 0.01f,
            "radius drifted from reference: ours=$oursDp reference=$referenceDp",
        )
    }

    private fun assertBaseColorMode(baseColor: ShadcnBaseColor, dark: Boolean) {
        val refKey = baseColor.name.lowercase()
        val mode = if (dark) "dark" else "light"
        val referenceTokens = ShadcnReferenceTokens.BY_BASE_COLOR[refKey]
            ?: error(
                "no ShadcnReferenceTokens entry for base color \"$refKey\" -- " +
                    "tools/shadcn/extract_shadcn_tokens.py's THEME_NAMES and ShadcnBaseColor have drifted apart",
            )
        val reference = if (dark) referenceTokens.dark else referenceTokens.light
        val theme = shadcnThemeValues(baseColor = baseColor, dark = dark)
        val ours: Map<String, Color> = mapOf(
            "background" to theme.background,
            "foreground" to theme.foreground,
            "primary" to theme.primary,
            "primary-foreground" to theme.primaryForeground,
            "secondary" to theme.secondary,
            "secondary-foreground" to theme.secondaryForeground,
            "muted" to theme.muted,
            "muted-foreground" to theme.mutedForeground,
            "accent" to theme.accent,
            "accent-foreground" to theme.accentForeground,
            "destructive" to theme.destructive,
            "border" to theme.border,
            "input" to theme.input,
            "ring" to theme.ring,
            "card" to theme.card,
            "card-foreground" to theme.onCard,
            "popover" to theme.popover,
            "popover-foreground" to theme.onPopover,
            "sidebar" to theme.sidebar,
            "sidebar-foreground" to theme.onSidebar,
            "sidebar-accent" to theme.sidebarAccent,
            "sidebar-accent-foreground" to theme.onSidebarAccent,
            "sidebar-border" to theme.sidebarBorder,
            "sidebar-ring" to theme.sidebarRing,
            "sidebar-primary" to theme.palette.sidebarPrimary,
            "sidebar-primary-foreground" to theme.palette.sidebarPrimaryForeground,
        )

        for ((key, actual) in ours) {
            assertColorClose("$refKey $mode $key", reference.getValue(key).toColor(), actual)
        }
    }

    private fun assertColorClose(label: String, reference: Color, actual: Color, tolerance: Float = this.tolerance) {
        val diff = abs(reference.r - actual.r) + abs(reference.g - actual.g) + abs(reference.b - actual.b)
        assertTrue(diff < tolerance, "$label drifted: reference=$reference actual=$actual diff=$diff")
    }
}
