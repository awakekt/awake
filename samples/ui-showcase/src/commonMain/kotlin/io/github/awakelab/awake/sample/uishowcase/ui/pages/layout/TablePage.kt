/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.layout

import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTable
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTableCellAlign
import io.github.awakelab.awake.ui.shadcn.components.cell
import io.github.awakelab.awake.ui.shadcn.components.head

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
    usageCode = """
ShadcnTable {
    header {
        row {
            head("Invoice")
            head("Amount", align = ShadcnTableCellAlign.End)
        }
    }
    body {
        row {
            cell("INV001")
            cell("$250.00", align = ShadcnTableCellAlign.End)
        }
    }
    caption("A list of your recent invoices.")
}
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/table-demo.tsx",
    previewHeight = 420,
    notes = listOf("Cells are text-only by contract; richer cells compose a row directly."),
    hero = {
        ShadcnTable {
            header {
                row(bordered = true) {
                    InvoiceHeaders.forEachIndexed { index, title ->
                        val align =
                            if (index == InvoiceHeaders.lastIndex) ShadcnTableCellAlign.End else ShadcnTableCellAlign.Start
                        head(title, align = align)
                    }
                }
            }
            body {
                InvoiceRows.forEach { values ->
                    row {
                        values.forEachIndexed { index, value ->
                            val align =
                                if (index == values.lastIndex) ShadcnTableCellAlign.End else ShadcnTableCellAlign.Start
                            cell(value, align = align)
                        }
                    }
                }
            }
            caption("A list of your recent invoices.")
        }
    },
)
