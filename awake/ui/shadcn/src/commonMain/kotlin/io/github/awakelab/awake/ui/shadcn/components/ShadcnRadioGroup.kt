/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.semantics.SemanticsProperties
import io.github.awakelab.awake.compose.ui.semantics.SemanticsRole
import io.github.awakelab.awake.compose.ui.semantics.semantics
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

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
): Int {
    val theme = shadcnTheme
    val state = remember { RadioGroupState() }
    val next = state.clicked?.also { state.clicked = null } ?: selected

    Column(
        modifier.semantics { this[SemanticsProperties.SelectableGroup] = true },
        verticalArrangement = Arrangement.spacedBy(ShadcnRadioGroupGap),
    ) {
        options.forEachIndexed { index, option ->
            val interaction = remember { InteractionSource() }
            val active = index == next
            Row(
                Modifier.clickable(interaction) { if (enabled) state.clicked = index },
                horizontalArrangement = Arrangement.spacedByHorizontal(ShadcnRadioLabelGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(ShadcnRadioSize)
                        // A filled circle with a background-filled one inside it, not
                        // `Modifier.border`: that draws four edge rects, and four straight edges
                        // cannot make a ring -- each takes the full radius and a 1px edge clamps it
                        // to 0.5, which is also what the style oracle read instead of `rounded-full`.
                        .background(theme.palette.input, theme.radii.full)
                        // On the ring, not on the row: the ring is the control, and a reader that
                        // landed on the row would report a box as wide as the label beside it.
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
                        Modifier
                            .size(ShadcnRadioSize - ShadcnRadioBorderWidth * 2f)
                            .background(theme.palette.background, theme.radii.full),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (active) {
                            Box(Modifier.size(ShadcnRadioDotSize).background(theme.palette.primary, theme.radii.full))
                        }
                    }
                }
                ShadcnText(
                    option,
                    Modifier
                        .semantics {
                            this[SemanticsProperties.Role] = SemanticsRole.Text
                            this[SemanticsProperties.Label] = option
                            this[SemanticsProperties.TestTag] = "parity-radio.$index.label"
                        },
                    variant = ShadcnTextVariant.Small,
                    // `leading-none` on the label, which is what puts it on the ring's centre line
                    // rather than a `text-sm` line box taller than the ring itself.
                    lineHeight = ShadcnRadioLabelLeading,
                )
            }
        }
    }
    return next
}

/** The click that arrived during input dispatch, consumed by the next build. */
private class RadioGroupState {
    var clicked: Int? = null
}
