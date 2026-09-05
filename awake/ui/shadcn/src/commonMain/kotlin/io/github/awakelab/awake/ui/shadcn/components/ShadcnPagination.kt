/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.tailwind.Tw

/**
 * `ShadcnPagination`: Page navigation control bar with numbered page buttons and Previous/Next actions.
 *
 * **Tailwind Reference**: `flex items-center gap-1`.
 *
 * Use cases:
 * - Table pagination and list paging controls.
 * - Search results page navigation.
 *
 * **Example Usage**:
 * ```kotlin
 * ShadcnPagination(
 *     currentPage = currentPage,
 *     totalPages = 5,
 *     onPageChange = { newPage -> currentPage = newPage }
 * )
 * ```
 *
 * @param currentPage Currently active page number (1-indexed).
 * @param totalPages Total number of available pages.
 * @param onPageChange Callback triggered when a user clicks a page button or Next/Previous action.
 * @param modifier Custom layout modifier applied to the pagination row.
 *
 * Keywords: pagination, page numbers, page navigation, previous, next, page bar.
 */
context(_: Composer)
fun ShadcnPagination(
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnButton(
            label = "Previous",
            variant = ShadcnButtonVariant.Ghost,
            size = ShadcnButtonSizeVariant.Sm,
            enabled = currentPage > 1,
            onClick = { if (currentPage > 1) onPageChange(currentPage - 1) },
        )

        for (page in 1..totalPages) {
            val isCurrent = page == currentPage
            ShadcnButton(
                label = page.toString(),
                variant = if (isCurrent) ShadcnButtonVariant.Outline else ShadcnButtonVariant.Ghost,
                size = ShadcnButtonSizeVariant.Sm,
                onClick = { onPageChange(page) },
            )
        }

        ShadcnButton(
            label = "Next",
            variant = ShadcnButtonVariant.Ghost,
            size = ShadcnButtonSizeVariant.Sm,
            enabled = currentPage < totalPages,
            onClick = { if (currentPage < totalPages) onPageChange(currentPage + 1) },
        )
    }
}
