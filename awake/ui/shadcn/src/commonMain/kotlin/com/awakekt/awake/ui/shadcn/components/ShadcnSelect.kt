/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.ScrollState
import com.awakekt.awake.compose.foundation.animation.animateFloat
import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.focusable
import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.layout.widthIn
import com.awakekt.awake.compose.foundation.style.StyleState
import com.awakekt.awake.compose.foundation.style.rememberStyleState
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.foundation.verticalScroll
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clipToBounds
import com.awakekt.awake.compose.ui.input.key.KeyEvent
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.input.key.onKeyEvent
import com.awakekt.awake.compose.ui.layout.Layer
import com.awakekt.awake.compose.ui.layout.LayerKind
import com.awakekt.awake.compose.ui.layout.LayerPosition
import com.awakekt.awake.compose.ui.layout.LayerPositionProvider
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/** An option in [ShadcnSelect]. */
data class ShadcnSelectItem(
    val label: String,
    val enabled: Boolean = true,
)

/** A controlled shadcn Select. The caller owns both its selected index and open state. */
context(_: Composer)
fun ShadcnSelect(
    items: List<ShadcnSelectItem>,
    selectedIndex: Int?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onItemSelected: (Int) -> Unit,
    id: String,
    modifier: Modifier = Modifier,
    placeholder: String = "Select...",
    enabled: Boolean = true,
) {
    val anchor = remember { PopupAnchor() }
    val keyboard = remember { SelectKeyboardState() }
    val density = LocalDensity.current
    Box(
        modifier.popupAnchor(anchor),
    ) {
        ShadcnSelectTrigger(
            value = selectedIndex?.let(items::getOrNull)?.label,
            placeholder = placeholder,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                keyboard.highlightedIndex = selectedIndex
                onExpandedChange(!expanded)
            },
            onKeyEvent = { event ->
                handleSelectKey(
                    event = event,
                    items = items,
                    selectedIndex = selectedIndex,
                    expanded = expanded,
                    state = keyboard,
                    onExpandedChange = onExpandedChange,
                    onItemSelected = onItemSelected,
                )
            },
        )
        if (expanded) {
            Layer(
                kind = LayerKind.Popup,
                dismissOnOutsideClick = true,
                onDismissRequest = { onExpandedChange(false) },
                positionProvider = remember(density) {
                    SelectPositionProvider(
                        anchor,
                        (SelectPopupGap.value * density).toInt(),
                        selectedIndex,
                        (ShadcnSelectSurfaceInset.value * density).toInt(),
                    )
                },
                measurePolicy = BoxMeasurePolicy(),
            ) {
                val triggerWidth =
                    (anchor.width / density).coerceAtLeast(POPOVER_MIN_WIDTH.value).dp
                ShadcnSelectContent(
                    items = items,
                    selectedIndex = selectedIndex,
                    highlightedIndex = keyboard.highlightedIndex,
                    modifier = Modifier.width(triggerWidth),
                    id = id,
                )?.let { selected ->
                    onItemSelected(selected)
                    keyboard.highlightedIndex = null
                    onExpandedChange(false)
                }
            }
        }
    }
}

/** shadcn's select trigger: `h-9 rounded-md border border-input bg-transparent px-3 py-2 text-sm`. */
context(_: Composer)
fun ShadcnSelectTrigger(
    value: String?,
    modifier: Modifier = Modifier,
    placeholder: String = "Select...",
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    onKeyEvent: (KeyEvent) -> Boolean = { false },
) {
    val theme = shadcnTheme
    val interaction = remember { InteractionSource() }
    val styleState = rememberStyleState(interaction, enabled = enabled)
    val focusRingAlpha = animateFloat(if (styleState.isFocused) 1f else 0f)
    val box = remember(theme) { theme.selectTriggerStyle() }

    Row(
        modifier
            .height(ShadcnSelectHeight)
            .hoverable(interaction, enabled = enabled)
            .clickable(interaction) { if (enabled) onClick() }
            .focusable(enabled = enabled, interactionSource = interaction)
            .onKeyEvent { if (enabled) onKeyEvent(it) else false }
            .focusRing(
                alpha = focusRingAlpha,
                width = FieldFocusRingWidth,
                color = theme.palette.ring.withAlpha(0.5f),
                cornerRadius = theme.radii.md,
            )
            .styleable(styleState, box),
        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnText(
            value ?: placeholder,
            variant = ShadcnTextVariant.Small,
            color = if (value == null) theme.palette.mutedForeground else theme.palette.foreground,
            weight = FontWeight.Normal,
        )
        ShadcnIcon(ShadcnIcons.chevronDown, tint = theme.palette.mutedForeground)
    }
}

/** Select content uses a leading fixed indicator slot, unlike a dropdown-menu item. */
context(_: Composer)
private fun ShadcnSelectContent(
    items: List<ShadcnSelectItem>,
    selectedIndex: Int?,
    highlightedIndex: Int?,
    modifier: Modifier,
    id: String,
): Int? {
    val theme = shadcnTheme
    val state = remember { SelectContentState() }
    val clicked = state.clicked
    state.clicked = null
    val selected = selectedIndex?.takeIf { it in items.indices }
    val overflow = items.size * ShadcnSelectItemHeight.value > ShadcnSelectBaseViewportHeight.value
    // The selected item is initially aligned to the top of the visible list. Keep the state before
    // deriving the controls so each frame reflects the actual scroll edge after a button click.
    val scroll = remember(selected, items.size) {
        ScrollState((selected ?: 0) * ShadcnSelectItemHeight.value.toInt())
    }
    val showScrollUp = overflow && scroll.canScrollBackward
    // The controls follow the real scroll range, not the selected index. During the first layout
    // maxValue is not known yet, so use the item-aligned target as a temporary prediction.
    val selectedHasFinalViewport = selected != null && items.size - selected <= 5
    val showScrollDown = overflow && if (selectedHasFinalViewport) {
        false
    } else if (scroll.maxValue == 0) {
        selected == null || items.size - selected > 5
    } else {
        scroll.canScrollForward
    }
    val scrollControlHeight =
        (if (showScrollUp) ShadcnSelectScrollButtonHeight else 0.dp) +
            (if (showScrollDown) ShadcnSelectScrollButtonHeight else 0.dp)
    val needsExpandedEndViewport = showScrollUp && !showScrollDown &&
        selected != null && items.size - selected >= 5
    val popupHeight = if (needsExpandedEndViewport) {
        ShadcnSelectOpenHeight + ShadcnSelectScrollButtonHeight + 6.dp
    } else {
        ShadcnSelectOpenHeight
    }
    val viewportHeight = ShadcnSelectContentHeight +
        (popupHeight - ShadcnSelectOpenHeight) - scrollControlHeight
    Box(
        modifier
            .widthIn(min = POPOVER_MIN_WIDTH)
            .height(popupHeight)
            .styleable(StyleState.Default, theme.popoverSurfaceStyle(Tw.Spacing.s1))
            .semantics { this[SemanticsProperties.TestTag] = "$id.content" },
    ) {
        Column {
            // Radix's default item-aligned content scrolls options before the selected one above
            // the viewport. The initial offset is the preceding item; the viewport remains fully
            // scrollable, so keyboard or wheel input can still reach every option.
            if (showScrollUp) {
                val interaction = remember { InteractionSource() }
                Row(
                    Modifier
                        .height(ShadcnSelectScrollButtonHeight)
                        .fillMaxWidth()
                        .clickable(interaction) { scroll.scrollBy(-ShadcnSelectItemHeight.value.toInt()) }
                        .semantics { this[SemanticsProperties.TestTag] = "$id.scroll-up" },
                    horizontalArrangement = Arrangement.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShadcnIcon(ShadcnIcons.chevronUp, size = ShadcnSelectScrollIconSize)
                }
            }
            // The initial item-aligned offset must be present before the first popup layout. The
            // scroll state's max is only known after measurement, so constructing it with the
            // target avoids a one-frame unscrolled flash and does not depend on a later recomposition.
            Box(Modifier.height(viewportHeight).clipToBounds()) {
                Column(Modifier.verticalScroll(scroll)) {
                    items.forEachIndexed { index, item ->
                        val interaction = remember { InteractionSource() }
                        val selectedItem = index == selected
                        val highlighted = item.enabled &&
                            (interaction.isHovered || index == highlightedIndex || selectedItem)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .hoverable(interaction, enabled = item.enabled)
                                .clickable(interaction) { if (item.enabled) state.clicked = index }
                                .let {
                                    if (highlighted) {
                                        it.background(theme.palette.accent, theme.radii.sm)
                                    } else {
                                        it
                                    }
                                }
                                .padding(
                                    start = Tw.Spacing.s2,
                                    top = Tw.Spacing.s1_5,
                                    end = Tw.Spacing.s2,
                                    bottom = Tw.Spacing.s1_5,
                                )
                                .semantics {
                                    this[SemanticsProperties.Role] = SemanticsRole.Button
                                    this[SemanticsProperties.Label] = item.label
                                    this[SemanticsProperties.TestTag] = "$id.item.$index"
                                    if (!item.enabled) this[SemanticsProperties.Disabled] = true
                                },
                            horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ShadcnText(
                                item.label,
                                variant = ShadcnTextVariant.Small,
                                color = when {
                                    highlighted -> theme.palette.accentForeground
                                    item.enabled -> theme.palette.popoverForeground
                                    else -> theme.palette.popoverForeground.withAlpha(DISABLED_ALPHA)
                                },
                                weight = FontWeight.Normal,
                            )
                            if (selectedItem) {
                                ShadcnIcon(
                                    ShadcnIcons.check,
                                    size = ShadcnSelectIndicatorSize,
                                    tint = theme.palette.accentForeground,
                                )
                            } else {
                                Spacer(Modifier.width(ShadcnSelectIndicatorSlotSize))
                            }
                        }
                    }
                }
            }
            if (showScrollDown) {
                val interaction = remember { InteractionSource() }
                Row(
                    Modifier
                        .height(ShadcnSelectScrollButtonHeight)
                        .fillMaxWidth()
                        .clickable(interaction) { scroll.scrollBy(ShadcnSelectItemHeight.value.toInt()) }
                        .semantics { this[SemanticsProperties.TestTag] = "$id.scroll-down" },
                    horizontalArrangement = Arrangement.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShadcnIcon(ShadcnIcons.chevronDown, size = ShadcnSelectScrollIconSize)
                }
            }
        }
    }
    return clicked
}

private class SelectContentState {
    var clicked: Int? = null
}

private const val DISABLED_ALPHA = 0.5f

private class SelectKeyboardState {
    var highlightedIndex: Int? = null
}

private fun handleSelectKey(
    event: KeyEvent,
    items: List<ShadcnSelectItem>,
    selectedIndex: Int?,
    expanded: Boolean,
    state: SelectKeyboardState,
    onExpandedChange: (Boolean) -> Unit,
    onItemSelected: (Int) -> Unit,
): Boolean {
    if (event.type != KeyEventType.Down) return false
    val current = state.highlightedIndex ?: selectedIndex
    when (event.key) {
        Key.ArrowDown -> {
            state.highlightedIndex = items.nextEnabledIndex(current, forward = true)
            onExpandedChange(true)
        }

        Key.ArrowUp -> {
            state.highlightedIndex = items.nextEnabledIndex(current, forward = false)
            onExpandedChange(true)
        }

        Key.Home -> if (expanded) state.highlightedIndex = items.firstEnabledIndex()
        Key.End -> if (expanded) state.highlightedIndex = items.lastEnabledIndex()
        Key.Enter, Key.Space -> {
            if (!expanded) {
                state.highlightedIndex = selectedIndex
                onExpandedChange(true)
            } else {
                state.highlightedIndex?.let {
                    onItemSelected(it)
                    state.highlightedIndex = null
                    onExpandedChange(false)
                }
            }
        }

        Key.Escape -> if (expanded) {
            state.highlightedIndex = null
            onExpandedChange(false)
        }

        else -> return false
    }
    return true
}

private fun List<ShadcnSelectItem>.firstEnabledIndex(): Int? =
    indexOfFirst { it.enabled }.takeIf { it >= 0 }

private fun List<ShadcnSelectItem>.lastEnabledIndex(): Int? =
    indexOfLast { it.enabled }.takeIf { it >= 0 }

private fun List<ShadcnSelectItem>.nextEnabledIndex(current: Int?, forward: Boolean): Int? {
    if (isEmpty()) return null
    var index = current ?: if (forward) -1 else 0
    repeat(size) {
        index = if (forward) (index + 1) % size else (index - 1 + size) % size
        if (this[index].enabled) return index
    }
    return null
}

private class SelectPositionProvider(
    private val anchor: PopupAnchor,
    private val gap: Int,
    private val selectedIndex: Int?,
    private val contentInset: Int,
) : LayerPositionProvider {
    override fun position(
        parentX: Int,
        parentY: Int,
        layerWidth: Int,
        layerHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ): LayerPosition {
        val x = anchor.x.coerceIn(0, (viewportWidth - layerWidth).coerceAtLeast(0))
        val below = anchor.y + anchor.height + gap
        val selected = selectedIndex?.takeIf { it >= 0 }
        // Item-aligned Select keeps the selected row centered on the trigger. The content adds
        // a 23dp scroll affordance before the list when a later item is selected, so account for
        // that child offset rather than opening a duplicate trigger below the field.
        val selectedRowOffset = if (selected != null && selected > 0) {
            contentInset + ShadcnSelectScrollButtonHeight.value + ShadcnSelectItemHeight.value / 2f
        } else {
            contentInset + ShadcnSelectItemHeight.value / 2f
        }
        val itemAligned = anchor.y + anchor.height / 2f - selectedRowOffset - gap
        val y = if (selected == null) {
            if (below + layerHeight <= viewportHeight) {
                below
            } else {
                (anchor.y - gap - layerHeight).coerceAtLeast(
                    0,
                )
            }
        } else {
            itemAligned.toInt().coerceIn(0, (viewportHeight - layerHeight).coerceAtLeast(0))
        }
        return LayerPosition(x - parentX, y - parentY)
    }
}
