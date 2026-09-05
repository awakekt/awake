/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.BorderSides
import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.border
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.fillMaxHeight
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.layout.widthIn
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.CompositionLocalProvider
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.runtime.provides
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.platform.LocalTextStyle
import io.github.awakelab.awake.compose.ui.semantics.SemanticsProperties
import io.github.awakelab.awake.compose.ui.semantics.SemanticsRole
import io.github.awakelab.awake.compose.ui.semantics.semantics
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.text.font.FontWeight
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's `Sheet`: a panel pinned to one edge of the viewport, over a scrim.
 *
 * Unlike an alert dialog it *is* dismissible by its backdrop -- upstream builds it on the same
 * primitive as `Dialog`, and only the alert variant opts out.
 *
 * A sheet fills the edge it is pinned to and takes its own size on the other axis: left and right
 * are full-height and `w-3/4 sm:max-w-sm`, top and bottom are full-width and as tall as their
 * content. That asymmetry is upstream's, and it is why the side picks both an alignment and which
 * axis fills.
 */
context(_: Composer)
fun ShadcnSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    side: ShadcnSheetSide = ShadcnSheetSide.Right,
    title: String? = null,
    description: String? = null,
    id: String? = null,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    shadcnModalLayer(
        visible = visible,
        alignment = side.alignment(),
        onDismissRequest = onDismissRequest,
        scrimModifier = scrimModifier(id, onScrimClick = onDismissRequest),
    ) {
        sheetPanel(modifier.sheetSemantics(title, id), side, title, description, content)
    }
}

context(_: Composer)
private fun sheetPanel(
    modifier: Modifier,
    side: ShadcnSheetSide,
    title: String?,
    description: String?,
    content: (
        context(Composer)
        () -> Unit
    )?,
) {
    val theme = shadcnTheme
    // Two boxes because `w-3/4 sm:max-w-sm` is a minimum of two rules, and no single chain says it:
    // `fillMaxWidth(0.75f)` fixes the width before `widthIn` can cap it, and putting the cap outside
    // instead makes the fraction a fraction *of the cap*. The outer box is the three quarters, the
    // panel fills it up to the maximum, and the edge the sheet is pinned to is where it settles.
    Box(Modifier.sizeForSide(side), horizontalAlignment = side.panelAlignment()) {
        Box(
            modifier
                .panelSize(side)
                .background(theme.palette.background)
                .border(SheetBorderWidth, theme.palette.border, sides = side.borderSides()),
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(color = theme.palette.foreground),
            ) {
                Column(
                    Modifier.padding(SheetPadding),
                    verticalArrangement = Arrangement.spacedBy(SheetSectionGap),
                ) {
                    if (title != null || description != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(SheetHeaderGap)) {
                            if (title != null) ShadcnText(title, weight = SheetTitleWeight)
                            if (description != null) {
                                ShadcnText(
                                    description,
                                    variant = ShadcnTextVariant.Muted,
                                )
                            }
                        }
                    }
                    content?.let { it() }
                }
            }
        }
    }
}

private fun Modifier.sheetSemantics(title: String?, id: String?): Modifier = semantics {
    this[SemanticsProperties.Role] = SemanticsRole.Dialog
    if (title != null) this[SemanticsProperties.Label] = title
    if (id != null) this[SemanticsProperties.TestTag] = id
}

/** The space the panel settles in: `inset-y-0 w-3/4` on the sides, `inset-x-0` top and bottom. */
private fun Modifier.sizeForSide(side: ShadcnSheetSide): Modifier = when (side) {
    ShadcnSheetSide.Left, ShadcnSheetSide.Right -> fillMaxHeight().fillMaxWidth(SHEET_SIDE_FRACTION)
    ShadcnSheetSide.Top, ShadcnSheetSide.Bottom -> fillMaxWidth()
}

/**
 * The panel itself: all of that space, up to `sm:max-w-sm` on the sides.
 *
 * The cap goes *outside* the fill. `fillMaxWidth` reports the maximum it was offered, so a cap
 * inside it never gets to shrink anything -- it can only narrow what its own child is allowed,
 * which a fill above has already committed to. Ordered the other way the cap narrows the offer and
 * the fill takes what is left.
 */
private fun Modifier.panelSize(side: ShadcnSheetSide): Modifier = when (side) {
    ShadcnSheetSide.Left, ShadcnSheetSide.Right ->
        fillMaxHeight().widthIn(max = SheetSideMaxWidth).fillMaxWidth()

    ShadcnSheetSide.Top, ShadcnSheetSide.Bottom -> fillMaxWidth()
}

/** Within its three quarters, the panel hugs the edge it is pinned to. */
private fun ShadcnSheetSide.panelAlignment(): Alignment.Horizontal = when (this) {
    ShadcnSheetSide.Left -> Alignment.Start
    ShadcnSheetSide.Right -> Alignment.End
    ShadcnSheetSide.Top, ShadcnSheetSide.Bottom -> Alignment.CenterHorizontally
}

private fun ShadcnSheetSide.alignment(): Alignment = when (this) {
    ShadcnSheetSide.Left -> Alignment.CenterStart
    ShadcnSheetSide.Right -> Alignment.CenterEnd
    ShadcnSheetSide.Top -> Alignment.TopCenter
    ShadcnSheetSide.Bottom -> Alignment.BottomCenter
}

/** Only the edge facing the page is drawn: `border-l` on a right sheet, and so on. */
private fun ShadcnSheetSide.borderSides(): BorderSides = when (this) {
    ShadcnSheetSide.Left -> BorderSides(top = false, end = true, bottom = false, start = false)
    ShadcnSheetSide.Right -> BorderSides(top = false, end = false, bottom = false, start = true)
    ShadcnSheetSide.Top -> BorderSides(top = false, end = false, bottom = true, start = false)
    ShadcnSheetSide.Bottom -> BorderSides(top = true, end = false, bottom = false, start = false)
}

/** `w-3/4`. */
private const val SHEET_SIDE_FRACTION = 0.75f

/** `sm:max-w-sm`. */
private val SheetSideMaxWidth: Dp = 384.dp
private val SheetBorderWidth: Dp = 1.dp
private val SheetPadding: Dp = Tw.Spacing.s4
private val SheetSectionGap: Dp = Tw.Spacing.s4

/** `gap-1.5`. */
private val SheetHeaderGap: Dp = Tw.Spacing.s1_5
private val SheetTitleWeight: FontWeight = FontWeight.SemiBold
