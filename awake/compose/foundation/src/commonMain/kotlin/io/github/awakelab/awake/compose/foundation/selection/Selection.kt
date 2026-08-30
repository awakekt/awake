/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation.selection

import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.semantics.SemanticsProperties
import io.github.awakelab.awake.compose.ui.semantics.SemanticsRole
import io.github.awakelab.awake.compose.ui.semantics.semantics

/**
 * A control that flips a boolean when clicked — a checkbox, a switch, a toggle button.
 *
 * The value is passed **in** and the change reported **out**; nothing is held here. That is
 * Compose's shape and it is also the only shape that works, because a modifier instance does not
 * survive a frame.
 *
 * Reports its state to semantics as well as calling back, so a test can ask "is this checked"
 * without reaching into the caller's own state.
 */
fun Modifier.toggleable(
    value: Boolean,
    enabled: Boolean = true,
    role: SemanticsRole = SemanticsRole.Checkbox,
    interactionSource: InteractionSource? = null,
    onValueChange: (Boolean) -> Unit,
): Modifier = this
    .semantics(mergeDescendants = true) {
        this[SemanticsProperties.Role] = role
        this[SemanticsProperties.Selected] = value
        if (!enabled) this[SemanticsProperties.Disabled] = true
    }
    .let { if (enabled) it.clickable(interactionSource) { onValueChange(!value) } else it }

/**
 * One option among several — a radio button, a tab, a selectable list row.
 *
 * Separate from [toggleable] for the reason Compose keeps them separate: selecting an already
 * selected option is a no-op, not a deselect, so the callback takes no value. A radio group that
 * let its selected button clear itself would have no selection at all, which no radio group allows.
 */
fun Modifier.selectable(
    selected: Boolean,
    enabled: Boolean = true,
    role: SemanticsRole = SemanticsRole.RadioButton,
    interactionSource: InteractionSource? = null,
    onClick: () -> Unit,
): Modifier = this
    .semantics(mergeDescendants = true) {
        this[SemanticsProperties.Role] = role
        this[SemanticsProperties.Selected] = selected
        if (!enabled) this[SemanticsProperties.Disabled] = true
    }
    .let { if (enabled) it.clickable(interactionSource, onClick) else it }

/**
 * Marks a container whose children are mutually exclusive options.
 *
 * Semantics only — it changes nothing about layout or input. It exists so a screen reader announces
 * "option 2 of 5" rather than five unrelated buttons, which is information the tree has and no
 * child can state on its own.
 */
fun Modifier.selectableGroup(): Modifier = this.semantics {
    this[SemanticsProperties.SelectableGroup] = true
}
