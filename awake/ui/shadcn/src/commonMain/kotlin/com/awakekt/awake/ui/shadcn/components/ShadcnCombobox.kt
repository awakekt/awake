/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.layout.widthIn
import com.awakekt.awake.compose.foundation.rememberScrollState
import com.awakekt.awake.compose.foundation.style.StyleState
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.foundation.verticalScroll
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.Layer
import com.awakekt.awake.compose.ui.layout.LayerKind
import com.awakekt.awake.compose.ui.layout.onSizeChanged
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

data class ShadcnComboboxItem(
    val label: String,
    val id: String = label,
    val enabled: Boolean = true,
)

/**
 * Source-faithful shadcn Combobox: autocomplete input and command palette with an anchored list of suggestions.
 */
context(_: Composer)
fun ShadcnCombobox(
    items: List<ShadcnComboboxItem>,
    selectedIndex: Int?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select framework...",
    searchPlaceholder: String = "Search framework...",
    emptyText: String = "No framework found.",
    id: String = "combobox",
    enabled: Boolean = true,
) {
    val selectedItem = selectedIndex?.let { items.getOrNull(it) }
    val anchor = remember { PopupAnchor() }
    // Written by layout, read by the popup on the next frame -- hence a remembered cell
    // rather than a local, which the next pass would reset to zero.
    val triggerWidth = remember { MeasuredWidth() }
    val density = LocalDensity.current
    val searchState = remember { TextFieldState(selectedItem?.label.orEmpty()) }

    Box(
        modifier.popupAnchor(anchor).onSizeChanged { width, _ -> triggerWidth.px = width }
            .semantics { this[SemanticsProperties.TestTag] = "$id.trigger" },
    ) {
        ShadcnInput(
            state = searchState,
            placeholder = if (expanded) searchPlaceholder else placeholder,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    this[SemanticsProperties.TestTag] = "$id.search"
                },
            onClick = { if (enabled) onExpandedChange(true) },
        )

        if (expanded) {
            val provider = remember(density) {
                AnchoredBelowPositionProvider(anchor, (ComboboxPopupGap.value * density).toInt())
            }
            val popupWidth = (triggerWidth.px / density).coerceAtLeast(POPOVER_MIN_WIDTH.value).dp
            Layer(
                kind = LayerKind.Popup,
                dismissOnOutsideClick = true,
                onDismissRequest = { onExpandedChange(false) },
                positionProvider = provider,
                measurePolicy = BoxMeasurePolicy(),
            ) {
                ShadcnComboboxContent(
                    items = items,
                    selectedIndex = selectedIndex,
                    searchState = searchState,
                    emptyText = emptyText,
                    modifier = Modifier.width(popupWidth),
                    id = id,
                )?.let { selected ->
                    searchState.setText(items.getOrNull(selected)?.label.orEmpty())
                    onItemSelected(selected)
                    onExpandedChange(false)
                }
            }
        }
    }
}

context(_: Composer)
private fun ShadcnComboboxContent(
    items: List<ShadcnComboboxItem>,
    selectedIndex: Int?,
    searchState: TextFieldState,
    emptyText: String,
    modifier: Modifier,
    id: String,
): Int? {
    val theme = shadcnTheme
    val state = remember { ComboboxContentState() }
    val clicked = state.clicked
    state.clicked = null
    val query = searchState.text.trim()

    val filteredItems = if (query.isEmpty()) {
        items.mapIndexed { idx, item -> idx to item }
    } else {
        items.mapIndexedNotNull { idx, item ->
            if (item.label.contains(query, ignoreCase = true)) idx to item else null
        }
    }

    Box(
        modifier
            .widthIn(min = POPOVER_MIN_WIDTH)
            .styleable(StyleState.Default, theme.popoverSurfaceStyle(Tw.Spacing.s1))
            .semantics { this[SemanticsProperties.TestTag] = "$id.content" },
    ) {
        Column {
            // Search field
            if (filteredItems.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Tw.Spacing.s6, horizontal = Tw.Spacing.s4),
                    contentAlignment = Alignment.Center,
                ) {
                    ShadcnText(
                        emptyText,
                        variant = ShadcnTextVariant.Small,
                        color = theme.palette.mutedForeground,
                    )
                }
            } else {
                val scroll = rememberScrollState()
                Box(Modifier.height(180.dp).verticalScroll(scroll)) {
                    Column(Modifier.fillMaxWidth()) {
                        filteredItems.forEach { (originalIndex, item) ->
                            val interaction = remember { InteractionSource() }
                            val isHovered = item.enabled && interaction.isHovered
                            val isSelected = originalIndex == selectedIndex

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .hoverable(interaction, enabled = item.enabled)
                                    .clickable(interaction) {
                                        if (item.enabled) state.clicked = originalIndex
                                    }
                                    .let {
                                        if (isHovered || isSelected) {
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
                                        this[SemanticsProperties.TestTag] =
                                            "$id.item.$originalIndex"
                                        if (!item.enabled) this[SemanticsProperties.Disabled] = true
                                    },
                                horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ShadcnText(
                                    item.label,
                                    variant = ShadcnTextVariant.Small,
                                    color = if (isHovered || isSelected) {
                                        theme.palette.accentForeground
                                    } else {
                                        theme.palette.popoverForeground
                                    },
                                    weight = FontWeight.Normal,
                                )
                                if (isSelected) {
                                    ShadcnIcon(
                                        ShadcnIcons.check,
                                        tint = theme.palette.popoverForeground,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    return clicked
}

private class ComboboxContentState {
    var clicked: Int? = null
}

/** Matches shadcn's current Base UI ComboboxContent `sideOffset={6}`. */
private val ComboboxPopupGap = 6.dp

/** A trigger width measured by layout, so its popup can match it on the following frame. */
private class MeasuredWidth {
    var px: Int = 0
}
