// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui.foundation

/** Mirrors Compose Foundation's `ToggleableState`, the tri-state a checkbox reports. */
public enum class UiToggleableState {
    Off,
    On,
    Indeterminate,
}

/**
 * The value half of Compose Foundation's `Modifier.toggleable`.
 *
 * Compose fuses claiming the interaction and reporting the new value into one modifier. Awake
 * splits them because a widget claims its slot before it can be hit-tested: [interact] does the
 * claiming and gates on `enabled`, and this decides what the value becomes. Keeping it a pure
 * function of the interaction is what makes it shareable -- `checkbox`, `switch` and `toggle`
 * each wrote their own copy of this line, and they had already drifted (`toggle` re-checked
 * `enabled`, which [interact] has always gated for it).
 */
public fun UiInteraction.toggled(value: Boolean): Boolean = if (clicked) !value else value

/**
 * The value half of Compose Foundation's `Modifier.triStateToggleable`.
 *
 * A click from [UiToggleableState.Indeterminate] resolves to [UiToggleableState.On] rather than
 * cycling through all three, matching Compose's own transition: a partially-checked parent
 * checks everything rather than clearing it.
 */
public fun UiInteraction.toggled(state: UiToggleableState): UiToggleableState = when {
    !clicked -> state
    state == UiToggleableState.On -> UiToggleableState.Off
    else -> UiToggleableState.On
}

/**
 * The value half of Compose Foundation's `Modifier.selectable`.
 *
 * Selection is idempotent, unlike toggling: clicking an already-selected radio or menu row
 * leaves it selected. Deselection is the enclosing group's decision, not the row's.
 */
public fun UiInteraction.selected(selected: Boolean): Boolean = selected || clicked
