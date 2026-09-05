/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.layout

import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnPagination
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText

internal val PaginationPage = ShowcasePage(
    id = "pagination",
    title = "Pagination",
    category = ShowcaseCategory.Layout,
    description = "Pagination controls with page navigation buttons and previous/next actions.",
    usageCode = """
ShadcnPagination(
    currentPage = page,
    totalPages = 5,
    onPageChange = { page = it }
)
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/pagination-demo.tsx",
    previewHeight = 300,
    hero = {
        val pageState = remember { IntState(1) }

        Column {
            ShadcnText("Showing Page ${pageState.value} of 5")
            ShadcnPagination(
                currentPage = pageState.value,
                totalPages = 5,
                onPageChange = { pageState.value = it },
            )
        }
    },
)

private class IntState(var value: Int)
