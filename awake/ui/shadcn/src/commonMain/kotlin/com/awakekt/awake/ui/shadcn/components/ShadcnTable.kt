/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.RowScope
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * Receiver scope for declaring table sections inside [ShadcnTable].
 */
@ShadcnTableDsl
class ShadcnTableScope internal constructor() {

    context(_: Composer)
    fun header(
        modifier: Modifier = Modifier,
        content: (
            context(Composer)
            ShadcnTableHeaderScope.() -> Unit
        )? = null,
    ) = ShadcnTableHeader(modifier, content)

    context(_: Composer)
    fun body(
        modifier: Modifier = Modifier,
        content: ShadcnTableRowsScope.() -> Unit,
    ) = ShadcnTableBody(modifier, content)

    context(_: Composer)
    fun footer(
        modifier: Modifier = Modifier,
        content: ShadcnTableRowsScope.() -> Unit,
    ) = ShadcnTableFooter(modifier, content)

    context(_: Composer)
    fun caption(
        text: String,
        modifier: Modifier = Modifier,
    ) = ShadcnTableCaption(text, modifier)
}

/**
 * Receiver scope for declaring header rows inside [ShadcnTableHeader].
 */
@ShadcnTableDsl
class ShadcnTableHeaderScope internal constructor() {

    context(_: Composer)
    fun row(
        modifier: Modifier = Modifier,
        selected: Boolean = false,
        bordered: Boolean = true,
        content: (
            context(Composer)
            RowScope.() -> Unit
        )? = null,
    ) = ShadcnTableRow(modifier, selected, bordered, content)
}

/**
 * `ShadcnTable`: Root table container for structured data display.
 *
 * **Tailwind Reference**: `w-full caption-bottom text-sm`.
 *
 * Use cases:
 * - Data grids, invoice tables, transaction histories, user rosters.
 *
 * **Example Usage (Concise DSL)**:
 * ```kotlin
 * ShadcnTable {
 *     header {
 *         row {
 *             head("Invoice")
 *             head("Amount", align = ShadcnTableCellAlign.End)
 *         }
 *     }
 *     body {
 *         row {
 *             cell("INV-001")
 *             cell("$250.00", align = ShadcnTableCellAlign.End)
 *         }
 *     }
 *     caption("A list of recent invoices.")
 * }
 * ```
 *
 * @param modifier Custom layout modifier applied to the table container.
 * @param header Optional header slot automatically wrapped in `ShadcnTableHeader`.
 * @param footer Optional footer slot automatically wrapped in `ShadcnTableFooter`.
 * @param caption Optional caption string automatically wrapped in `ShadcnTableCaption`.
 * @param content Receiver scope slot for table body or explicit sections.
 *
 * Keywords: table, data table, grid, data grid, rows, columns, invoice list.
 */
context(_: Composer)
fun ShadcnTable(
    modifier: Modifier = Modifier,
    header: (
        context(Composer)
        ShadcnTableHeaderScope.() -> Unit
    )? = null,
    footer: (ShadcnTableRowsScope.() -> Unit)? = null,
    caption: String? = null,
    content: (
        context(Composer)
        ShadcnTableScope.() -> Unit
    )? = null,
) {
    val scope = remember { ShadcnTableScope() }
    Column(modifier.fillMaxWidth()) {
        if (header != null) {
            ShadcnTableHeader { header() }
        }
        if (content != null) {
            content(scope)
        }
        if (footer != null) {
            ShadcnTableFooter { footer() }
        }
        if (caption != null) {
            ShadcnTableCaption(caption)
        }
    }
}

/**
 * `ShadcnTableHeader`: Header section containing header row and column titles.
 *
 * **Tailwind Reference**: `[&_tr]:border-b`.
 *
 * @param modifier Custom layout modifier.
 * @param content Receiver scope slot containing header rows (`ShadcnTableRow` with `ShadcnTableHead` cells).
 *
 * Keywords: table header, column headers, header row.
 */
context(_: Composer)
fun ShadcnTableHeader(
    modifier: Modifier = Modifier,
    content: (
        context(Composer)
        ShadcnTableHeaderScope.() -> Unit
    )? = null,
) {
    val scope = remember { ShadcnTableHeaderScope() }
    Column(modifier.fillMaxWidth()) {
        content?.let { it(scope) }
    }
}

/**
 * `ShadcnTableBody`: Table body section holding data rows.
 *
 * Automatically removes the bottom border rule on the last data row (`[&_tr:last-child]:border-0`).
 *
 * @param modifier Custom layout modifier.
 * @param content Builder scope for declaring rows via `row { ... }`.
 *
 * Keywords: table body, data rows, table rows.
 */
context(_: Composer)
fun ShadcnTableBody(
    modifier: Modifier = Modifier,
    content: ShadcnTableRowsScope.() -> Unit,
) {
    val rows = remember(content) { ShadcnTableRowsScope().apply(content).rows }
    Column(modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            ShadcnTableRow(
                selected = row.selected,
                bordered = index < rows.lastIndex,
                content = row.content,
            )
        }
    }
}

/**
 * `ShadcnTableFooter`: Table footer section with top separator and medium-weight cell styling.
 *
 * **Tailwind Reference**: `border-t bg-muted/50 font-medium [&>tr]:last:border-b-0`.
 *
 * @param modifier Custom layout modifier.
 * @param content Builder scope for declaring footer rows via `row { ... }`.
 *
 * Keywords: table footer, total row, summary row.
 */
context(_: Composer)
fun ShadcnTableFooter(
    modifier: Modifier = Modifier,
    content: ShadcnTableRowsScope.() -> Unit,
) {
    val theme = shadcnTheme
    val rows = remember(content) { ShadcnTableRowsScope().apply(content).rows }
    CompositionLocalProvider(LocalTableCellWeight provides FontWeight.Medium) {
        Column(
            modifier.fillMaxWidth().background(theme.palette.muted.withAlpha(FOOTER_MUTED_ALPHA)),
        ) {
            ShadcnSeparator(Modifier.fillMaxWidth().height(RowBorderThickness))
            rows.forEachIndexed { index, row ->
                ShadcnTableRow(
                    selected = row.selected,
                    bordered = index < rows.lastIndex,
                    content = row.content,
                )
            }
        }
    }
}

/** `font-medium` inherited from an ancestor `<tfoot>`; [FontWeight.Normal] everywhere else. */
private val LocalTableCellWeight = compositionLocalOf { FontWeight.Normal }

/** DSL Scope for declaring rows in [ShadcnTableBody] or [ShadcnTableFooter]. */
@ShadcnTableDsl
class ShadcnTableRowsScope internal constructor() {
    internal val rows = mutableListOf<ShadcnTableRowSpec>()

    /**
     * Declares a data row in the table body or footer.
     *
     * @param selected Whether the row is highlighted in selected state (`bg-muted`).
     * @param content Cell content slot with [RowScope] access for cell weighting.
     */
    fun row(
        selected: Boolean = false,
        content: context(Composer) RowScope.() -> Unit,
    ) {
        rows += ShadcnTableRowSpec(selected, content)
    }
}

@DslMarker
annotation class ShadcnTableDsl

internal class ShadcnTableRowSpec(
    val selected: Boolean,
    val content: context(Composer)
    RowScope.() -> Unit,
)

/**
 * `ShadcnTableRow`: Individual table row container.
 *
 * **Tailwind Reference**: `border-b transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted`.
 *
 * @param modifier Custom layout modifier.
 * @param selected Whether the row is in selected state.
 * @param bordered Whether to draw a bottom border separator line below the row.
 * @param content Row content slot with [RowScope] access for cell weighting.
 *
 * Keywords: table row, tr, row.
 */
context(_: Composer)
fun ShadcnTableRow(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    bordered: Boolean = true,
    content: (
        context(Composer)
        RowScope.() -> Unit
    )? = null,
) {
    val theme = shadcnTheme
    val interaction = remember { InteractionSource() }
    val background = when {
        selected -> theme.palette.muted
        interaction.isHovered -> theme.palette.muted.withAlpha(ROW_HOVER_ALPHA)
        else -> null
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier
                .fillMaxWidth()
                .let { if (background == null) it else it.background(background) }
                .hoverable(interaction),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content?.let { it() }
        }
        if (bordered) ShadcnSeparator(Modifier.fillMaxWidth().height(RowBorderThickness))
    }
}

/**
 * `ShadcnTableHead`: Table header cell container.
 *
 * **Tailwind Reference**: `h-10 px-2 text-left align-middle font-medium text-muted-foreground`.
 *
 * @param text Header text label.
 * @param modifier Custom layout modifier.
 * @param weight Cell width proportion weight within the row (`1f` by default).
 * @param align Alignment of cell content (`Start` or `End`).
 *
 * Keywords: table head, th, column title, header cell.
 */
context(_: Composer)
fun RowScope.ShadcnTableHead(
    text: String,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    align: ShadcnTableCellAlign = ShadcnTableCellAlign.Start,
) {
    val theme = shadcnTheme
    Box(
        modifier.weight(weight).height(RowHeight).padding(horizontal = Tw.Spacing.s2),
        horizontalAlignment = align.boxAlignment(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnText(
            text,
            variant = ShadcnTextVariant.Small,
            weight = HeadWeight,
            color = theme.palette.foreground,
        )
    }
}

/**
 * `ShadcnTableCell`: Individual table data cell container.
 *
 * **Tailwind Reference**: `p-2 align-middle`.
 *
 * @param text Data cell text label.
 * @param modifier Custom layout modifier.
 * @param weight Cell width proportion weight within the row (`1f` by default).
 * @param align Alignment of cell content (`Start` or `End`).
 * @param fontWeight Optional font weight override (inherits `Medium` in footer, `Normal` elsewhere).
 *
 * Keywords: table cell, td, cell, data cell.
 */
context(_: Composer)
fun RowScope.ShadcnTableCell(
    text: String,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    align: ShadcnTableCellAlign = ShadcnTableCellAlign.Start,
    fontWeight: FontWeight? = null,
) {
    Box(
        modifier.weight(weight).padding(Tw.Spacing.s2),
        horizontalAlignment = align.boxAlignment(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnText(
            text,
            variant = ShadcnTextVariant.Small,
            weight = fontWeight ?: LocalTableCellWeight.current,
        )
    }
}

context(_: Composer)
fun RowScope.head(
    text: String,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    align: ShadcnTableCellAlign = ShadcnTableCellAlign.Start,
) = ShadcnTableHead(text, modifier, weight, align)

context(_: Composer)
fun RowScope.cell(
    text: String,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    align: ShadcnTableCellAlign = ShadcnTableCellAlign.Start,
    fontWeight: FontWeight? = null,
) = ShadcnTableCell(text, modifier, weight, align, fontWeight)

/**
 * `ShadcnTableCaption`: Table caption text displayed centered below the table.
 *
 * **Tailwind Reference**: `mt-4 text-sm text-muted-foreground`.
 *
 * @param text Caption description text.
 * @param modifier Custom layout modifier.
 *
 * Keywords: table caption, caption, table footnote.
 */
context(_: Composer)
fun ShadcnTableCaption(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxWidth().padding(top = CaptionTopMargin),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShadcnText(text, variant = ShadcnTextVariant.Muted)
    }
}

enum class ShadcnTableCellAlign { Start, End }

private fun ShadcnTableCellAlign.boxAlignment(): Alignment.Horizontal = when (this) {
    ShadcnTableCellAlign.Start -> Alignment.Start
    ShadcnTableCellAlign.End -> Alignment.End
}

private val RowHeight: Dp = Tw.Spacing.s10
private val HeadWeight = FontWeight.Medium
private val RowBorderThickness: Dp = Dp(1f)
private const val ROW_HOVER_ALPHA = 0.5f
private const val FOOTER_MUTED_ALPHA = 0.5f
private val CaptionTopMargin: Dp = Tw.Spacing.s4
