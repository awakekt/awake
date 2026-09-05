/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.hoverable
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.foundation.style.Style
import io.github.awakelab.awake.compose.foundation.style.disabled
import io.github.awakelab.awake.compose.foundation.style.hovered
import io.github.awakelab.awake.compose.foundation.style.rememberStyleState
import io.github.awakelab.awake.compose.foundation.style.styleable
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.semantics.SemanticsProperties
import io.github.awakelab.awake.compose.ui.semantics.SemanticsRole
import io.github.awakelab.awake.compose.ui.semantics.semantics
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.ShadcnThemeValues
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's toggle: `inline-flex items-center justify-center rounded-md px-2 data-[state=on]:bg-accent`.
 *
 * `px-2` and no vertical pad -- the label is centred by the box's own height, not by padding. The
 * ui-core recipe had 12/10 borrowed from the button bundle, which reported a 16px content box where
 * the reference says 24, and that was this component's only parity failure.
 *
 * Returns the state after the click that arrived this frame, so a caller can drive it from its own
 * state without a callback.
 */
context(_: Composer)
fun ShadcnToggle(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * Supplied when a caller needs to drive the visual state -- a state-matrix preview renders the
     * same control hovered, pressed and focused without a pointer ever being there.
     *
     * Compose's own seam: its components take an `interactionSource` for exactly this. Nullable
     * rather than a `remember {}` default because a default argument that allocates a slot would
     * shift the slot table depending on whether a caller passed one.
     */
    interactionSource: InteractionSource? = null,
): Boolean {
    val theme = shadcnTheme
    val owned = remember { InteractionSource() }
    val interaction = interactionSource ?: owned
    val styleState = rememberStyleState(interaction, enabled = enabled)
    val state = remember { ToggleState() }
    val next = if (state.clicked) !checked else checked
    state.clicked = false

    val style = remember(theme, next) { theme.toggleStyle(next) }
    Box(
        modifier
            .size(ToggleSize)
            .hoverable(interaction, enabled = enabled)
            .clickable(interaction) { if (enabled) state.clicked = true }
            .styleable(styleState, style)
            .semantics {
                this[SemanticsProperties.Role] = SemanticsRole.Button
                this[SemanticsProperties.Label] = label
                this[SemanticsProperties.Selected] = next
                if (!enabled) this[SemanticsProperties.Disabled] = true
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnText(
            label,
            variant = ShadcnTextVariant.Small,
            color = if (next) theme.palette.accentForeground else theme.palette.foreground,
        )
    }
    return next
}

private fun ShadcnThemeValues.toggleStyle(checked: Boolean): Style = Style {
    cornerRadius(radii.md)
    // `px-2`, no vertical pad.
    contentPadding(horizontal = Tw.Spacing.s2, vertical = 0.dp)
    if (checked) background(palette.accent)
    hovered(Style { background(if (checked) palette.accent else palette.muted) })
    disabled(Style { alpha(DISABLED_ALPHA) })
}

/** The click that arrived during input dispatch, consumed by the next build. */
private class ToggleState {
    var clicked: Boolean = false
}

/** The parity capture's `size-10`; upstream's default toggle is `h-9`, sized by the caller. */
private val ToggleSize: Dp = 40.dp

/** `disabled:opacity-50`. */
private const val DISABLED_ALPHA = 0.5f
