/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.BoxMeasurePolicy
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.foundation.text.TextFieldState
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.draw.alpha
import io.github.awakelab.awake.compose.ui.layout.Layer
import io.github.awakelab.awake.compose.ui.layout.LayerKind
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw

/**
 * Individual item entry inside a [ShadcnCommand] search list.
 *
 * @param title Command item title.
 * @param description Optional description or subtitle text.
 * @param shortcut Optional keyboard shortcut badge label (e.g. "Ctrl+P").
 * @param category Category group name (e.g. "Suggestions", "Settings").
 */
data class ShadcnCommandItem(
    val title: String,
    val description: String? = null,
    val shortcut: String? = null,
    val category: String = "General",
)

/**
 * `ShadcnCommand`: A command palette menu for live searching and triggering application actions.
 *
 * **Tailwind Reference**: `w-full max-w-[480px] rounded-md border bg-popover p-4 text-popover-foreground shadow-md`.
 *
 * Use cases:
 * - `Cmd+K` / `Ctrl+K` global command search dialog.
 * - In-app navigation palettes & quick actions.
 *
 * **Example Usage**:
 * ```kotlin
 * ShadcnCommand(
 *     items = listOf(
 *         ShadcnCommandItem("Calendar", shortcut = "Ctrl+C", category = "Suggestions"),
 *         ShadcnCommandItem("Profile Settings", shortcut = "Ctrl+P", category = "Settings"),
 *     ),
 *     onItemSelected = { handleAction(it) }
 * )
 * ```
 *
 * @param items List of [ShadcnCommandItem] entries to filter and display.
 * @param onItemSelected Callback triggered when a user selects an item.
 * @param modifier Custom layout modifier applied to the palette card.
 * @param placeholder Search input placeholder text.
 * @param width Card width (`480.dp` by default).
 *
 * Keywords: command, command palette, cmd+k, search dialog, search menu, action palette.
 */
context(_: Composer)
fun ShadcnCommand(
    items: List<ShadcnCommandItem>,
    onItemSelected: (ShadcnCommandItem) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Type a command or search...",
    width: Dp = 480.dp,
) {
    val searchState = remember { TextFieldState("") }
    val query = searchState.text.lowercase()
    val filteredItems = if (query.isEmpty()) {
        items
    } else {
        items.filter {
            it.title.lowercase().contains(query) ||
                (it.description != null && it.description.lowercase().contains(query)) ||
                it.category.lowercase().contains(query)
        }
    }
    val grouped = filteredItems.groupBy { it.category }

    ShadcnCard(modifier = modifier.width(width)) {
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3)) {
            ShadcnInput(state = searchState, placeholder = placeholder)
            ShadcnSeparator()

            if (grouped.isEmpty()) {
                shadcnMuted("No results found.")
            } else {
                grouped.forEach { (category, categoryItems) ->
                    shadcnMuted(category)
                    categoryItems.forEach { item ->
                        ShadcnItem(
                            title = item.title,
                            description = item.description,
                            modifier = Modifier.clickable { onItemSelected(item) },
                            trailing = {
                                if (item.shortcut != null) {
                                    ShadcnBadge(label = item.shortcut)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Controlled command palette overlay dialog.
 *
 * @param visible Controls whether the command dialog overlay is open.
 * @param onVisibleChange Callback to toggle visibility.
 * @param items List of [ShadcnCommandItem] entries to filter and display.
 * @param onItemSelected Callback triggered when a user selects an item.
 * @param modifier Custom layout modifier.
 */
context(_: Composer)
fun ShadcnCommandDialog(
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    items: List<ShadcnCommandItem>,
    onItemSelected: (ShadcnCommandItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = rememberOverlayAlpha(visible)
    if (isPresent(visible, alpha)) {
        Layer(
            kind = LayerKind.Popup,
            dismissOnOutsideClick = true,
            onDismissRequest = { onVisibleChange(false) },
            measurePolicy = BoxMeasurePolicy(),
        ) {
            ShadcnCommand(
                items = items,
                onItemSelected = {
                    onItemSelected(it)
                    onVisibleChange(false)
                },
                modifier = modifier.alpha(alpha),
            )
        }
    }
}
