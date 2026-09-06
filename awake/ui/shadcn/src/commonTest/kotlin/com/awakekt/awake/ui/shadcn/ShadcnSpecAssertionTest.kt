/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.core.math2d.dp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.components.ShadcnAvatarSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnRadioMetrics
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The spec table: every shadcn-derived constant we can reference, asserted against the real
 * Tailwind class it implements.
 */
class ShadcnSpecAssertionTest {

    private data class SpecRow(
        val component: String,
        val role: String,
        val tailwindClass: String,
        val expected: Dp,
        val actual: Dp,
    )

    /** Vega is the only preset mapped to real shadcn -- the other seven are Awake-original
     * density variants with no upstream spec, so asserting them against Tailwind is meaningless
     * (see ShadcnStylePreset's own doc comment). */
    private val vega = shadcnThemeValues(preset = ShadcnStylePreset.Vega)
    private val metrics = ShadcnStylePreset.Vega.metrics

    private fun rows(): List<SpecRow> = listOf(
        // --- Button: `h-8` / `h-9` / `h-10` / `size-9` (button.tsx, buttonVariants.size) ---
        SpecRow("Button", "sm height", "h-8", Tw.Spacing.s8, Dp(ShadcnButtonSizeVariant.Sm.height.value)),
        SpecRow("Button", "default height", "h-9", Tw.Spacing.s9, Dp(ShadcnButtonSizeVariant.Default.height.value)),
        SpecRow("Button", "lg height", "h-10", Tw.Spacing.s10, Dp(ShadcnButtonSizeVariant.Lg.height.value)),
        SpecRow("Button", "icon size", "size-9", Tw.Spacing.s9, Dp(ShadcnButtonSizeVariant.Icon.height.value)),
        SpecRow("Button", "xs height", "h-6", Tw.Spacing.s6, Dp(ShadcnButtonSizeVariant.Xs.height.value)),
        SpecRow("Button", "xs padding-x", "px-2", Tw.Spacing.s2, Dp(ShadcnButtonSizeVariant.Xs.paddingX.value)),
        SpecRow("Button", "sm padding-x", "px-3", Tw.Spacing.s3, Dp(ShadcnButtonSizeVariant.Sm.paddingX.value)),
        SpecRow("Button", "default padding-x", "px-4", Tw.Spacing.s4, Dp(ShadcnButtonSizeVariant.Default.paddingX.value)),
        SpecRow("Button", "lg padding-x", "px-6", Tw.Spacing.s6, Dp(ShadcnButtonSizeVariant.Lg.paddingX.value)),

        // --- Avatar: `size-6` / `size-8` / `size-10` (avatar.tsx) ---
        SpecRow("Avatar", "sm box", "size-6", Tw.Spacing.s6, Dp(ShadcnAvatarSizeVariant.Sm.size.value)),
        SpecRow("Avatar", "default box", "size-8", Tw.Spacing.s8, Dp(ShadcnAvatarSizeVariant.Default.size.value)),
        SpecRow("Avatar", "lg box", "size-10", Tw.Spacing.s10, Dp(ShadcnAvatarSizeVariant.Lg.size.value)),

        // --- Surface insets (ShadcnMetrics, Vega) ---
        SpecRow("Card/Dialog", "panel padding", "p-6", Tw.Spacing.s6, metrics.panelPadding),
        SpecRow("Popover", "panel padding", "p-4", Tw.Spacing.s4, metrics.surfacePadding),
        SpecRow("Input/Select", "trigger padding-x", "px-3", Tw.Spacing.s3, metrics.fieldPaddingX),
        SpecRow("SelectTrigger", "padding-y", "py-2", Tw.Spacing.s2, metrics.fieldPaddingY),
        SpecRow("Input", "padding-y", "py-1", Tw.Spacing.s1, metrics.inputPaddingY),
        SpecRow("Badge", "padding-x", "px-2", Tw.Spacing.s2, metrics.badgePaddingX),
        SpecRow("Badge", "padding-y", "py-0.5", Tw.Spacing.s0_5, metrics.badgePaddingY),

        // --- RadioGroup: `size-4`, indicator `size-2`, authored row `gap-2`, root `gap-3`
        SpecRow("RadioGroupItem", "ring size", "size-4", Tw.Spacing.s4, ShadcnRadioMetrics.itemSize),
        SpecRow("RadioGroupItem", "selected dot size", "size-2", Tw.Spacing.s2, ShadcnRadioMetrics.indicatorSize),
        SpecRow("RadioGroup row", "label gap", "gap-2", Tw.Spacing.s2, ShadcnRadioMetrics.labelGap),
        SpecRow("RadioGroup", "row gap", "gap-3", Tw.Spacing.s3, ShadcnRadioMetrics.groupGap),

        // --- Radius ladder ---
        SpecRow("Theme", "radius lg (--radius)", "0.625rem", 10f.dp, Dp(vega.radii.lg.value)),
    )

    @Test
    fun everyReferenceableConstantMatchesItsRealTailwindClass() {
        val failures = rows().filter { abs(it.expected.value - it.actual.value) >= 0.001f }
        assertTrue(
            failures.isEmpty(),
            buildString {
                appendLine("${failures.size} constant(s) do not match their real shadcn/ui class:")
                failures.forEach {
                    appendLine(
                        "  ${it.component} / ${it.role}: `${it.tailwindClass}` is " +
                            "${it.expected.value}dp, but we use ${it.actual.value}dp",
                    )
                }
                appendLine("Verify against third_party/shadcn-ui-ref before changing either side.")
            },
        )
    }

    /** Guards the table itself: a row whose expected value is silently zero (a typo'd `Tw`
     * constant, say) would pass the assertion above against another zero. */
    @Test
    fun specTableIsNotVacuous() {
        val rows = rows()
        assertTrue(rows.size >= 15, "spec table shrank unexpectedly: ${rows.size} rows")
        assertTrue(
            rows.none { it.expected.value <= 0f },
            "row(s) with a non-positive expected value: " +
                rows.filter { it.expected.value <= 0f }.map { "${it.component}/${it.role}" },
        )
    }
}
