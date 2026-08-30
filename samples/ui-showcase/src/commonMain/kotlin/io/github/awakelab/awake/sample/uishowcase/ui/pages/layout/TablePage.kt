/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.layout

import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTableCellAlign
import io.github.awakelab.awake.ui.shadcn.components.shadcnTable
import io.github.awakelab.awake.ui.shadcn.components.shadcnTableBody
import io.github.awakelab.awake.ui.shadcn.components.shadcnTableCaption
import io.github.awakelab.awake.ui.shadcn.components.shadcnTableCell
import io.github.awakelab.awake.ui.shadcn.components.shadcnTableHead
import io.github.awakelab.awake.ui.shadcn.components.shadcnTableHeader
import io.github.awakelab.awake.ui.shadcn.components.shadcnTableRow

private val InvoiceHeaders = listOf("Invoice", "Status", "Method", "Amount")

private val InvoiceRows = listOf(
    listOf("INV001", "Paid", "Credit Card", "\$250.00"),
    listOf("INV002", "Pending", "PayPal", "\$150.00"),
    listOf("INV003", "Unpaid", "Bank Transfer", "\$350.00"),
)

internal val TablePage = ShowcasePage(
    id = "table",
    title = "Table",
    category = ShowcaseCategory.Layout,
    description = "A responsive table component with weighted columns and an optional caption.",
    usageCode = """shadcnTable { shadcnTableHeader { ... }; shadcnTableBody { row { shadcnTableCell("INV001") } } }""",
    referenceExample = "registry/new-york-v4/examples/table-demo.tsx",
    previewHeight = 420,
    notes = listOf("Cells are text-only by contract; richer cells compose a row directly."),
    hero = {
        shadcnTable {
            shadcnTableHeader {
                shadcnTableRow(bordered = true) {
                    InvoiceHeaders.forEachIndexed { index, header ->
                        val align = if (index == InvoiceHeaders.lastIndex) ShadcnTableCellAlign.End else ShadcnTableCellAlign.Start
                        shadcnTableHead(header, align = align)
                    }
                }
            }
            shadcnTableBody {
                InvoiceRows.forEach { values ->
                    row {
                        values.forEachIndexed { index, value ->
                            val align = if (index == values.lastIndex) ShadcnTableCellAlign.End else ShadcnTableCellAlign.Start
                            shadcnTableCell(value, align = align)
                        }
                    }
                }
            }
            shadcnTableCaption("A list of your recent invoices.")
        }
    },
)
