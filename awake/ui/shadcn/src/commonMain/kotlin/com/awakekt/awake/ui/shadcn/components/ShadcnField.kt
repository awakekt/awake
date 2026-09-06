/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.RowScope
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.Sp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's `Field` family: the anatomy a labelled control is assembled from.
 *
 * Ten parts, matching upstream's ten exports. **Not** the seven `shadcnFieldTextField`-style
 * composites the `ui-core` recipes carry: those have no counterpart upstream and existed because a
 * polled `Boolean` made `field { label; control; error }` awkward to write by hand. The controls
 * take callbacks now, so the composite saves one line and costs an eight-parameter wrapper per
 * control -- and each wrapper is a second place for a control's own API to drift.
 *
 * Three upstream behaviours have no counterpart here, named rather than approximated:
 *
 * - **`orientation = "responsive"`** is `@container/field-group` -- a container query, which styles
 *   an element by its own resolved width. There is no mechanism for a measure-dependent style in
 *   this engine, and faking it with a viewport breakpoint would be a different feature wearing the
 *   same name. [ShadcnFieldOrientation] has two values.
 * - **Descendant-driven spacing**: `has-[>[data-slot=checkbox-group]]:gap-3` tightens a field set
 *   that happens to hold a checkbox group. A parent styling itself from what its children turn out
 *   to be needs the children measured before the parent is styled.
 * - **The label-as-card pattern**, `has-[>[data-slot=field]]:rounded-md border`, where a label
 *   wrapping a whole field becomes a selectable card. Same reason. Reachable by hand with
 *   [shadcnSurface] until the selector has an equivalent.
 */
context(_: Composer)
fun shadcnFieldSet(
    modifier: Modifier = Modifier,
    content: context(Composer) () -> Unit,
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FieldSetGap),
    ) { content() }
}

/**
 * `FieldGroup`: several fields, spaced further apart than the parts within one.
 *
 * `gap-7` against a field's own `gap-3`. The two gaps are what makes a form read as groups of
 * related lines rather than one evenly spaced list, so they are not the same constant.
 */
context(_: Composer)
fun shadcnFieldGroup(
    modifier: Modifier = Modifier,
    content: context(Composer) () -> Unit,
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FieldGroupGap),
    ) { content() }
}

/**
 * `Field`: one labelled control, with its description and error.
 *
 * Upstream carries `role="group"`, which is what tells a reader the label and the control belong
 * together rather than merely being adjacent. There is no `Group` in [SemanticsRole] and this does
 * not invent one: `SelectableGroup` is the nearest existing key and means something else (mutually
 * exclusive options), so setting it would be a lie a screen reader acts on. The grouping is
 * currently visual only.
 */
context(_: Composer)
fun shadcnField(
    modifier: Modifier = Modifier,
    orientation: ShadcnFieldOrientation = ShadcnFieldOrientation.Vertical,
    content: context(Composer) ShadcnFieldScope.() -> Unit,
) {
    val grouped = modifier.fillMaxWidth()
    when (orientation) {
        ShadcnFieldOrientation.Vertical -> Column(
            grouped,
            verticalArrangement = Arrangement.spacedBy(FieldGap),
        ) {
            // Null scope: a vertical field wraps its own height, so there is no leftover main-axis
            // space for `flex-1` to claim and `fillRemaining()` is honestly a no-op.
            ShadcnFieldScope(null).content()
        }
        ShadcnFieldOrientation.Horizontal -> Row(
            grouped,
            horizontalArrangement = Arrangement.spacedByHorizontal(FieldGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnFieldScope(this).content()
        }
    }
}

// `ShadcnFieldOrientation` already exists in `ShadcnFieldContracts.kt` and models the same two
// values, so this takes it rather than declaring a second one -- which the compiler caught, as it
// did for `ShadcnButtonGroupOrientation` and `ShadcnMenuItem`.

/**
 * What a [shadcnField]'s content may say about itself.
 *
 * A scope rather than a composition local. `ShadcnButtonGroupSeparator` used to read its
 * orientation from one, which meant a separator declared outside a group silently drew the wrong
 * way; here `fillRemaining()` cannot be called outside a field at all.
 */
@ShadcnFieldDsl
class ShadcnFieldScope internal constructor(private val row: RowScope?) {
    /**
     * `flex-1` on this part: takes the width the rest of the row did not.
     *
     * A no-op in a vertical field, which is upstream's behaviour too -- `flex-1` along a column's
     * main axis has nothing to divide when the column wraps its content.
     */
    fun Modifier.fillRemaining(): Modifier = if (row == null) this else with(row) { weight(1f) }
}

@DslMarker
annotation class ShadcnFieldDsl

/**
 * `FieldContent`: the label-and-description column beside a control in a horizontal field.
 *
 * `gap-1.5`, tighter than the field's own `gap-3`, because a description belongs to the label
 * above it rather than sitting between two equals.
 */
context(_: Composer)
fun shadcnFieldContent(
    modifier: Modifier = Modifier,
    content: context(Composer) () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FieldContentGap)) { content() }
}

/**
 * `FieldSeparator`: a rule between fields, optionally with a word on it.
 *
 * Upstream overlays the label on the rule and paints `bg-background` behind it to punch a hole; a
 * row of rule, label, rule reaches the same picture without needing a node to draw over its
 * sibling, and does not depend on the label's background matching whatever is behind the form.
 *
 * The `-my-2` that pulls it into the surrounding gap is not applied: negative insets have no
 * modifier. A separator therefore sits 8dp lower in a group than upstream's.
 */
context(_: Composer)
fun shadcnFieldSeparator(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    if (label == null) {
        ShadcnSeparator(modifier.fillMaxWidth().height(SeparatorThickness))
        return
    }
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(SeparatorThickness)) {
            ShadcnSeparator(Modifier.fillMaxWidth().height(SeparatorThickness))
        }
        ShadcnText(label, variant = ShadcnTextVariant.Muted)
        Box(Modifier.weight(1f).height(SeparatorThickness)) {
            ShadcnSeparator(Modifier.fillMaxWidth().height(SeparatorThickness))
        }
    }
}

/** `gap-6`. */
private val FieldSetGap: Dp = Tw.Spacing.s6

/** `gap-7` -- deliberately wider than a field's own gap. */
private val FieldGroupGap: Dp = Tw.Spacing.s7

/** `gap-3`. */
private val FieldGap: Dp = Tw.Spacing.s3

/** `gap-1.5`. */
private val FieldContentGap: Dp = Tw.Spacing.s1_5

/** Tailwind's bare `border`. */
private val SeparatorThickness: Dp = Dp(1f)
