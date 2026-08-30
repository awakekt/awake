/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.hoverable
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.RowScope
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.CompositionLocalProvider
import io.github.awakelab.awake.compose.runtime.compositionLocalOf
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.runtime.provides
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.core.text.font.FontWeight
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's `Table` family: eight parts, matching upstream's eight exports.
 *
 * Upstream nests freely -- a `TableRow` can appear directly under a hand-rolled `<tbody>`, and
 * `[&_tr:last-child]:border-0` finds the last one however it got there. There is no descendant
 * selector here, so "which row is last" is answered by declaring, not by asking the tree after the
 * fact: [shadcnTableBody] and [shadcnTableFooter] take a builder scope that collects rows before
 * placing any of them, the same shape [shadcnResizablePanelGroup] and [shadcnToggleGroup] already
 * use for "this container needs to know its children before any of them draw."
 *
 * There is no `<table>` layout model -- no column measured from the widest cell across every row.
 * Cells take an explicit `weight`, the same contract the `ui-core` recipe this replaces already
 * settled on, and the fix if a real one is ever needed is the same one every fixed-column list in
 * this codebase would want.
 */
context(_: Composer)
fun shadcnTable(
    modifier: Modifier = Modifier,
    content: context(Composer) () -> Unit,
) {
    Column(modifier.fillMaxWidth()) { content() }
}

/** `TableHeader`: usually one [shadcnTableRow] of [shadcnTableHead] cells. */
context(_: Composer)
fun shadcnTableHeader(
    modifier: Modifier = Modifier,
    content: context(Composer) () -> Unit,
) = Column(modifier.fillMaxWidth()) { content() }

/**
 * `TableBody`: rows, declared through [ShadcnTableRowsScope.row] so the last one can drop its own
 * bottom rule -- `[&_tr:last-child]:border-0` upstream.
 */
context(_: Composer)
fun shadcnTableBody(
    modifier: Modifier = Modifier,
    content: ShadcnTableRowsScope.() -> Unit,
) {
    val rows = remember(content) { ShadcnTableRowsScope().apply(content).rows }
    Column(modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            shadcnTableRow(selected = row.selected, bordered = index < rows.lastIndex, content = row.content)
        }
    }
}

/**
 * `TableFooter`: `border-t bg-muted/50 font-medium`, with a top rule of its own rather than
 * inheriting the body's bottom one, and every row's own bottom rule dropped except the last kept
 * -- upstream's `[&>tr]:last:border-b-0` reads backwards from [shadcnTableBody]'s rule, and only
 * the *last* footer row loses its border because a footer sits at the bottom of the table and
 * nothing needs separating it from what is below.
 *
 * `font-medium` reaches every cell through [LocalTableCellWeight] -- see [shadcnTableCell].
 */
context(_: Composer)
fun shadcnTableFooter(
    modifier: Modifier = Modifier,
    content: ShadcnTableRowsScope.() -> Unit,
) {
    val theme = shadcnTheme
    val rows = remember(content) { ShadcnTableRowsScope().apply(content).rows }
    CompositionLocalProvider(LocalTableCellWeight provides FontWeight.Medium) {
        Column(modifier.fillMaxWidth().background(theme.palette.muted.withAlpha(FOOTER_MUTED_ALPHA))) {
            ShadcnSeparator(Modifier.fillMaxWidth().height(RowBorderThickness))
            rows.forEachIndexed { index, row ->
                shadcnTableRow(selected = row.selected, bordered = index < rows.lastIndex, content = row.content)
            }
        }
    }
}

/** `font-medium` inherited from an ancestor `<tfoot>`; [FontWeight.Normal] everywhere else. */
private val LocalTableCellWeight = compositionLocalOf { FontWeight.Normal }

/** Declares the rows of a [shadcnTableBody] or [shadcnTableFooter], in order. */
@ShadcnTableDsl
class ShadcnTableRowsScope internal constructor() {
    internal val rows = mutableListOf<ShadcnTableRowSpec>()

    fun row(selected: Boolean = false, content: context(Composer) RowScope.() -> Unit) {
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
 * `TableRow`: `border-b`, `hover:bg-muted/50`, `data-[state=selected]:bg-muted`.
 *
 * No fixed height. A body row is 37px in the browser and a header row is 40 -- not because rows
 * differ, but because [shadcnTableHead] carries its own `h-10` and [shadcnTableCell] does not,
 * and a row is only ever as tall as the cells inside it. Giving the row itself a height would
 * make every row 40px and the body rows measurably too tall.
 *
 * [bordered] is what [shadcnTableBody]/[shadcnTableFooter] set to `false` on the last row, rather
 * than a CSS pseudo-selector reaching for it after the fact.
 */
context(_: Composer)
fun shadcnTableRow(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    bordered: Boolean = true,
    content: context(Composer) RowScope.() -> Unit,
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
        ) { content() }
        if (bordered) ShadcnSeparator(Modifier.fillMaxWidth().height(RowBorderThickness))
    }
}

/** `TableHead`: `h-10 px-2 text-left align-middle font-medium`. */
context(_: Composer)
fun RowScope.shadcnTableHead(
    text: String,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    align: ShadcnTableCellAlign = ShadcnTableCellAlign.Start,
) {
    val theme = shadcnTheme
    Box(
        // `h-10 px-2` -- height on the head cell itself, not the row: this is the only reason a
        // header row measures 40px while a body row measures 37.
        modifier.weight(weight).height(RowHeight).padding(horizontal = Tw.Spacing.s2),
        horizontalAlignment = align.boxAlignment(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnText(text, variant = ShadcnTextVariant.Small, weight = HeadWeight, color = theme.palette.foreground)
    }
}

/**
 * `TableCell`: `p-2 align-middle`.
 *
 * [fontWeight] defaults to [LocalTableCellWeight] rather than `null`. Upstream's `font-medium` on
 * `TableFooter` is a class on the `<tfoot>`, and every cell inside inherits it through the
 * cascade -- measured on the reference case at weight 500 with no `font-medium` on the cell
 * itself. There is no cascade here, so [shadcnTableFooter] provides the same default the way
 * `LocalTextStyle` already provides text colour and size, and a caller can still override any one
 * cell by passing [fontWeight] explicitly.
 */
context(_: Composer)
fun RowScope.shadcnTableCell(
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
        ShadcnText(text, variant = ShadcnTextVariant.Small, weight = fontWeight ?: LocalTableCellWeight.current)
    }
}

/** `TableCaption`: `mt-4 text-sm text-muted-foreground`, centred under the table. */
context(_: Composer)
fun shadcnTableCaption(
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

/** Table column metadata is policy, while row/cell layout is layout behavior. */
data class ShadcnTableColumn(
    val header: String,
    val weight: Float = 1f,
    val align: ShadcnTableCellAlign = ShadcnTableCellAlign.Start,
)

fun shadcnTableColumnWidthsPx(columns: List<ShadcnTableColumn>, availableWidthPx: Float): List<Float> {
    val totalWeight = columns.sumOf { it.weight.toDouble() }.toFloat()
    if (totalWeight <= 0f) return columns.map { 0f }
    return columns.map { (it.weight / totalWeight) * availableWidthPx }
}

private fun ShadcnTableCellAlign.boxAlignment(): Alignment.Horizontal = when (this) {
    ShadcnTableCellAlign.Start -> Alignment.Start
    ShadcnTableCellAlign.End -> Alignment.End
}

/** `h-10`. */
private val RowHeight: Dp = Tw.Spacing.s10

/** `font-medium`. */
private val HeadWeight = FontWeight.Medium

/** Tailwind's bare `border`. */
private val RowBorderThickness: Dp = Dp(1f)

/** `hover:bg-muted/50`. */
private const val ROW_HOVER_ALPHA = 0.5f

/** `bg-muted/50` on the footer. */
private const val FOOTER_MUTED_ALPHA = 0.5f

/** `mt-4`. */
private val CaptionTopMargin: Dp = Tw.Spacing.s4
