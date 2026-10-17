/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.runtime.rememberGroup
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.ui.shadcn.ShadcnThemeValues
import com.awakekt.awake.ui.shadcn.theme.ShadcnDensity
import com.awakekt.awake.ui.shadcn.theme.shadcnDensity
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's radio group: `grid gap-3`, each item a `size-4 rounded-full border` with a label beside it.
 *
 * The selected dot is `size-2` centred inside the ring, not a filled ring -- a filled circle reads
 * as a checkbox at this size.
 *
 * The group carries `SelectableGroup` rather than a role of its own: the group is not itself a radio
 * button, and what it contributes is the fact that lets a reader say "option 2 of 3".
 *
 * Returns the index selected after this frame's click.
 */
context(_: Composer)
fun ShadcnRadioGroup(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
): Int = rememberGroup(ShadcnRadioGroupGroup) {
    val theme = shadcnTheme
    val state = remember { RadioGroupState() }
    val next = state.clicked?.also { state.clicked = null } ?: selected
    val density = shadcnDensity
    state.prepare(theme, next, enabled, density)

    Column(
        modifier.semantics { this[SemanticsProperties.SelectableGroup] = true },
        verticalArrangement = Arrangement.spacedBy(density.radioGroupGap()),
    ) {
        options.forEachIndexed { index, option ->
            ShadcnRadioItem(state, index, option)
        }
    }
    next
}

context(_: Composer)
private fun ShadcnRadioItem(
    state: RadioGroupState,
    index: Int,
    option: String,
) {
    val interaction = remember { InteractionSource() }
    val active = index == state.selected
    Row(
        Modifier.clickable(interaction) { if (state.enabled) state.clicked = index },
        horizontalArrangement = Arrangement.spacedByHorizontal(state.density.radioLabelGap()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnRadioControl(state.theme, state.density, index, option, active)
        shadcnDensityText(
            option,
            Modifier
                .semantics {
                    this[SemanticsProperties.Role] = SemanticsRole.Text
                    this[SemanticsProperties.Label] = option
                    this[SemanticsProperties.TestTag] = "parity-radio.$index.label"
                },
            variant = ShadcnTextVariant.Small,
            color = null,
            // `leading-none` on the label, which is what puts it on the ring's centre line
            // rather than a `text-sm` line box taller than the ring itself.
            lineHeight = state.density.radioLabelLeading(),
            weight = null,
            size = state.density.labelSize(),
        )
    }
}

context(_: Composer)
private fun ShadcnRadioControl(
    theme: ShadcnThemeValues,
    density: ShadcnDensity,
    index: Int,
    option: String,
    active: Boolean,
) {
    val radioSize = density.radioSize()
    Box(
        Modifier
            .size(radioSize)
            // Match upstream's actual `border rounded-full`. The old two-disc approximation made
            // the ring depend on the page background and could read square at small sizes.
            .let {
                if (theme.config.dark) {
                    it.background(theme.palette.input.withAlpha(theme.palette.input.a * RADIO_INPUT_ALPHA), theme.radii.full)
                } else {
                    it
                }
            }
            .border(ShadcnRadioBorderWidth, theme.palette.input, RoundedCornerShape(theme.radii.full))
            // On the ring, not on the row: the ring is the control, and a reader that landed on
            // the row would report a box as wide as the label beside it.
            .semantics {
                this[SemanticsProperties.Role] = SemanticsRole.RadioButton
                this[SemanticsProperties.Label] = option
                this[SemanticsProperties.TestTag] = "parity-radio.$index"
                this[SemanticsProperties.Selected] = active
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(radioSize - ShadcnRadioBorderWidth * 2f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active) {
                Box(Modifier.size(density.radioDotSize()).background(theme.palette.primary, theme.radii.full))
            }
        }
    }
}

/** The click that arrived during input dispatch, consumed by the next build. */
private class RadioGroupState {
    var clicked: Int? = null
    lateinit var theme: ShadcnThemeValues
    var density: ShadcnDensity = ShadcnDensity.Default
    var selected: Int = 0
    var enabled: Boolean = true

    fun prepare(nextTheme: ShadcnThemeValues, nextSelected: Int, nextEnabled: Boolean, nextDensity: ShadcnDensity) {
        theme = nextTheme
        selected = nextSelected
        enabled = nextEnabled
        density = nextDensity
    }
}

private const val RADIO_INPUT_ALPHA = 0.3f

/** Keys this component's `remember` slots as one group in its caller's node. */
private object ShadcnRadioGroupGroup
