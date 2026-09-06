/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.IntrinsicSize
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.layout.widthIn
import com.awakekt.awake.compose.foundation.style.Style
import com.awakekt.awake.compose.foundation.style.StyleState
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.focus.focusTarget
import com.awakekt.awake.compose.ui.input.key.KeyEvent
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.input.key.onKeyEvent
import com.awakekt.awake.compose.ui.layout.Layer
import com.awakekt.awake.compose.ui.layout.LayerKind
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/** Public Shadcn menu entries. */
sealed interface ShadcnMenuEntry

/**
 * `min-w-[8rem]` over *content* width.
 *
 * The rows use `fillMaxWidth`, which reads the incoming maximum, so without a width pinned first
 * every row filled whatever space the menu was handed and the surface shrink-wrapped to that --
 * three short labels came out as wide as the window. With the intrinsic query outside the bound it
 * returns the widest row raised to the minimum, and `fillMaxWidth` then means what it reads like:
 * fill this menu.
 */
private fun Modifier.menuSurface(style: Style): Modifier =
    width(IntrinsicSize.Max)
        .widthIn(min = POPOVER_MIN_WIDTH)
        .styleable(StyleState.Default, style)

data class ShadcnMenuItem(
    val label: String,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
) : ShadcnMenuEntry

data object ShadcnMenuSeparator : ShadcnMenuEntry

/**
 * shadcn's dropdown menu content: `min-w-[8rem] rounded-md border bg-popover p-1`.
 *
 * **The highlight is `focus:`, not `hover:`.** Radix moves focus to the item under the pointer, so
 * the two coincide with a mouse and diverge with a keyboard -- arrowing down highlights without any
 * pointer involved. Modelled on hover here because the overlay layer owns focus movement and this
 * recipe should not invent a second answer; the visual result is identical under a mouse, and the
 * keyboard case arrives with roving focus.
 *
 * A destructive item is `text-destructive` with `focus:bg-destructive/10` -- a tinted highlight, not
 * the accent one, so the row stays red while highlighted instead of flipping to the normal colour.
 *
 * Takes the existing [ShadcnMenuEntry] hierarchy rather than a flat list of its own. That type
 * already models a separator, which a menu genuinely has and a `List<String>` cannot express -- and
 * duplicating it would have meant two `ShadcnMenuItem`s in one package, which is what the compiler
 * caught. It gained a `destructive` flag, defaulted, so every existing caller is unaffected.
 *
 * Returns the index clicked this frame, or null. Separators are not clickable and are skipped, so
 * the index counts entries, not items -- the caller passed the list and can index it directly.
 */
context(_: Composer)
fun ShadcnDropdownMenu(
    entries: List<ShadcnMenuEntry>,
    modifier: Modifier = Modifier,
    id: String? = null,
    highlightedIndex: Int? = null,
): Int? {
    val theme = shadcnTheme
    val style = remember(theme) { theme.popoverSurfaceStyle(Tw.Spacing.s1) }
    val state = remember { MenuState() }
    val clicked = state.clicked
    state.clicked = null

    Box(modifier.menuSurface(style)) {
        Column {
            entries.forEachIndexed { index, entry ->
                if (entry is ShadcnMenuSeparator) {
                    ShadcnSeparator(Modifier.padding(vertical = Tw.Spacing.s1))
                    return@forEachIndexed
                }
                val item = entry as ShadcnMenuItem
                val interaction = remember { InteractionSource() }
                val highlighted =
                    item.enabled && (interaction.isHovered || index == highlightedIndex)
                Box(
                    Modifier
                        .fillMaxWidth()
                        // Pointer links after padding only see the text's inner box. The menu row
                        // is the target, so capture hover/click before the visual inset.
                        .hoverable(interaction, enabled = item.enabled)
                        .clickable(interaction) { if (item.enabled) state.clicked = index }
                        .let {
                            if (!highlighted) {
                                it
                            } else {
                                it.background(
                                    if (item.destructive) {
                                        theme.palette.destructive.withAlpha(
                                            DESTRUCTIVE_HIGHLIGHT_ALPHA,
                                        )
                                    } else {
                                        theme.palette.accent
                                    },
                                    theme.radii.sm,
                                )
                            }
                        }
                        .padding(horizontal = Tw.Spacing.s2, vertical = Tw.Spacing.s1_5)
                        // An item is a node a screen reader can land on and a test can address.
                        // Without this the row has no semantics at all, so the menu reports as one
                        // opaque box -- which is also why the parity harness could not see the items.
                        .semantics {
                            this[SemanticsProperties.Role] = SemanticsRole.Button
                            this[SemanticsProperties.Label] = item.label
                            if (id != null) this[SemanticsProperties.TestTag] = "$id.item.$index"
                            if (!item.enabled) this[SemanticsProperties.Disabled] = true
                        },
                ) {
                    ShadcnText(
                        item.label,
                        variant = ShadcnTextVariant.Small,
                        color = when {
                            item.destructive -> theme.palette.destructive
                            highlighted -> theme.palette.accentForeground
                            else -> theme.palette.popoverForeground
                        }.withAlpha(if (item.enabled) 1f else DISABLED_ALPHA),
                    )
                }
            }
        }
    }
    return clicked
}

/**
 * A controlled dropdown menu anchored below [trigger].
 *
 * The popup is a layer, so it neither affects the trigger's layout nor is clipped by the top bar.
 * The caller owns [expanded]; selecting an item, pressing outside, pressing Escape, or pressing the
 * trigger again requests a close.
 */
context(_: Composer)
fun ShadcnDropdownMenu(
    entries: List<ShadcnMenuEntry>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    menuModifier: Modifier = Modifier,
    id: String? = null,
    trigger: context(Composer) (onClick: () -> Unit) -> Unit,
) {
    val anchor = remember { PopupAnchor() }
    val keyboard = remember { MenuKeyboardState() }
    val density = LocalDensity.current
    Box(
        modifier
            .focusTarget()
            .onKeyEvent { event ->
                handleMenuKey(event, entries, expanded, keyboard, onExpandedChange, onItemSelected)
            }
            .popupAnchor(anchor),
    ) {
        trigger {
            if (expanded) keyboard.highlightedIndex = null
            onExpandedChange(!expanded)
        }
        if (expanded) {
            Layer(
                kind = LayerKind.Popup,
                dismissOnOutsideClick = true,
                onDismissRequest = { onExpandedChange(false) },
                positionProvider = remember(density) {
                    AnchoredBelowPositionProvider(anchor, (DROPDOWN_GAP.value * density).toInt())
                },
                measurePolicy = BoxMeasurePolicy(),
            ) {
                ShadcnDropdownMenu(
                    entries,
                    modifier = menuModifier,
                    id = id,
                    highlightedIndex = keyboard.highlightedIndex,
                )?.let { selected ->
                    onItemSelected(selected)
                    keyboard.highlightedIndex = null
                    onExpandedChange(false)
                }
            }
        }
    }
}

/** The click that arrived during input dispatch, consumed by the next build. */
private class MenuState {
    var clicked: Int? = null
}

/** The enabled entry reached by roving keyboard navigation, or null until Arrow/Home/End is used. */
private class MenuKeyboardState {
    var highlightedIndex: Int? = null
}

private fun handleMenuKey(
    event: KeyEvent,
    entries: List<ShadcnMenuEntry>,
    expanded: Boolean,
    state: MenuKeyboardState,
    onExpandedChange: (Boolean) -> Unit,
    onItemSelected: (Int) -> Unit,
): Boolean {
    if (event.type != KeyEventType.Down) return false
    val current = state.highlightedIndex
    when (event.key) {
        Key.ArrowDown -> {
            state.highlightedIndex = entries.nextEnabledIndex(current, forward = true)
            if (!expanded) onExpandedChange(true)
        }

        Key.ArrowUp -> {
            state.highlightedIndex = entries.nextEnabledIndex(current, forward = false)
            if (!expanded) onExpandedChange(true)
        }

        Key.Home -> if (expanded) state.highlightedIndex = entries.firstEnabledIndex()
        Key.End -> if (expanded) state.highlightedIndex = entries.lastEnabledIndex()
        Key.Enter, Key.Space -> {
            if (!expanded) {
                onExpandedChange(true)
            } else {
                current?.let { index ->
                    onItemSelected(index)
                    state.highlightedIndex = null
                    onExpandedChange(false)
                }
            }
        }

        else -> return false
    }
    return true
}

private fun List<ShadcnMenuEntry>.firstEnabledIndex(): Int? =
    indexOfFirst { it is ShadcnMenuItem && it.enabled }.takeIf { it >= 0 }

private fun List<ShadcnMenuEntry>.lastEnabledIndex(): Int? =
    indexOfLast { it is ShadcnMenuItem && it.enabled }.takeIf { it >= 0 }

private fun List<ShadcnMenuEntry>.nextEnabledIndex(current: Int?, forward: Boolean): Int? {
    if (isEmpty()) return null
    var index = current ?: if (forward) -1 else 0
    repeat(size) {
        index = if (forward) (index + 1) % size else (index - 1 + size) % size
        if (this[index] is ShadcnMenuItem && (this[index] as ShadcnMenuItem).enabled) return index
    }
    return null
}

/** Trigger bounds from the latest completed layout pass, in viewport coordinates. */

/** `sideOffset={4}` on shadcn's DropdownMenuContent. */
private val DROPDOWN_GAP: Dp = 4.dp

/** `data-[variant=destructive]:focus:bg-destructive/10`. */
private const val DESTRUCTIVE_HIGHLIGHT_ALPHA = 0.1f

/** `data-[disabled]:opacity-50`. */
private const val DISABLED_ALPHA = 0.5f
