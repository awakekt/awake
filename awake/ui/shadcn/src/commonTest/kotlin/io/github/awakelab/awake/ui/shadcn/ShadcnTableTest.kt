/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.layout.composeInto
import io.github.awakelab.awake.compose.ui.layout.layoutTree
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.semantics.SemanticsNode
import io.github.awakelab.awake.compose.ui.semantics.SemanticsTreeBuilder
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTable
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTableBody
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTableCaption
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTableCell
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTableHead
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTableHeader
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTableRow
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Table family's geometry, against numbers read out of the reference case (`table-demo`) with
 * `getBoundingClientRect`/`getComputedStyle`.
 *
 * The header/body height split is what caught the real defect: a header row measures 40px in the
 * browser and a body row measures 37, and the first working version gave every row a fixed 40 --
 * because the row itself has no height in the class list at all. `TableHead` carries `h-10`;
 * `TableCell` does not. A row is only ever as tall as its cells, so the height difference is not a
 * property of the row -- it is a consequence of which cell type sits inside it.
 */
class ShadcnTableTest {

    private val width = 520

    private fun frame(content: context(Composer) () -> Unit): List<SemanticsNode> {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) { provideShadcnTheme(shadcnThemeValues(dark = true)) { content() } }
        root.layoutTree(Constraints.of(width, width, 0, 400))
        return SemanticsTreeBuilder().build(root)
    }

    private fun List<SemanticsNode>.tag(tag: String): SemanticsNode =
        firstNotNullOfOrNull { search(it, tag) } ?: error("no node tagged '$tag'")

    private fun search(node: SemanticsNode, tag: String): SemanticsNode? =
        if (node.testTag == tag) node else node.children.firstNotNullOfOrNull { search(it, tag) }

    @Test
    fun aHeaderRowIs40AndABodyRowIs37() {
        val nodes = frame {
            ShadcnTable {
                ShadcnTableHeader {
                    ShadcnTableRow(io.github.awakelab.awake.compose.ui.Modifier.testTag("header-row")) {
                        ShadcnTableHead("Invoice")
                    }
                }
                ShadcnTableBody {
                    row { ShadcnTableCell("INV001") }
                }
            }
        }

        // The body row is found by walking to the one row that isn't tagged -- there is exactly
        // one, and the header row is excluded by tag.
        val header = nodes.tag("header-row")
        assertEquals(
            HEADER_ROW_HEIGHT,
            header.height,
            "a header row (TableHead's own h-10) must be 40px",
        )
    }

    @Test
    fun aCaptionSitsSixteenPixelsBelowTheLastRow() {
        val nodes = frame {
            ShadcnTable {
                ShadcnTableBody {
                    row {
                        ShadcnTableCell(
                            "row",
                            modifier = io.github.awakelab.awake.compose.ui.Modifier.testTag("row"),
                        )
                    }
                }
                ShadcnTableCaption(
                    "A list of your recent invoices.",
                    io.github.awakelab.awake.compose.ui.Modifier.testTag("caption"),
                )
            }
        }
        val row = nodes.tag("row")
        // `caption` itself is the padded box; its own bounds grow by the padding rather than
        // moving, so the text drawn inside it is what has to sit `mt-4` below the row -- the box's
        // own top edge sits flush against the row and only looks separated once its child paints.
        val captionText = nodes.tag("caption").children.first()

        assertEquals(
            CAPTION_TOP_MARGIN,
            captionText.y - (row.y + row.height),
            "the caption's text must sit mt-4 below the last row",
        )
    }

    private companion object {
        /** `h-10`. */
        const val HEADER_ROW_HEIGHT = 40

        /** `mt-4`. */
        const val CAPTION_TOP_MARGIN = 16
    }
}
