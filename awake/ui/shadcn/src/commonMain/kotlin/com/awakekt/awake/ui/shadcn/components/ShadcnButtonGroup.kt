/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.BorderSides
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.IntrinsicSize
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.graphics.Shape
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's button group: buttons joined into one control.
 *
 * The scope owns member rendering so joined geometry is a recipe rule instead of every caller
 * reauthoring modifier order. A later opaque member paints over the previous member's shared edge,
 * leaving the later member's one-pixel border just as the browser's `border-*-0` rule does.
 */
context(_: Composer)
fun ShadcnButtonGroup(
    modifier: Modifier = Modifier,
    orientation: ShadcnButtonGroupOrientation = ShadcnButtonGroupOrientation.Horizontal,
    content: ShadcnButtonGroupScope.() -> Unit,
) {
    // Collected directly, not inside `remember`. The key was the content lambda, whose identity
    // differs every pass, so the memoization never hit.
    //
    // It also removed a trap. The scope is declarative -- `button(...)` records a member, and the
    // members are composed later by `render()` -- but nothing stops a caller composing directly in
    // that lambda, because the enclosing Composer is in scope. Doing so while the collector ran as a
    // remember calculation left the composer mid-slot, and the misuse surfaced as an
    // IndexOutOfBoundsException from the slot table rather than as anything diagnosable.
    val items = ShadcnButtonGroupScope().apply(content).items
    val buttonCount = items.count { it is ButtonGroupMember.Button }
    var buttonIndex = 0
    fun render() {
        items.forEachIndexed { itemIndex, member ->
            when (member) {
                is ButtonGroupMember.Button -> {
                    val index = buttonIndex++
                    val modifier = if (orientation == ShadcnButtonGroupOrientation.Vertical) {
                        member.modifier.fillMaxWidth()
                    } else {
                        // Upstream's root uses `items-stretch`: mixed-size members share the
                        // tallest member's cross-axis extent.
                        member.modifier.fillMaxHeight()
                    }
                    val shape = memberShape(orientation, index, buttonCount, shadcnTheme.radii.md)
                    val nextIsSeparator = items.getOrNull(itemIndex + 1) is ButtonGroupMember.Separator
                    val borderSides = memberBorderSides(orientation, index, buttonCount, nextIsSeparator)
                    val memberContent = member.content
                    shadcnButtonContent(
                        modifier = modifier,
                        variant = member.variant,
                        size = member.size,
                        enabled = member.enabled,
                        onClick = member.onClick,
                        shape = shape,
                        borderSides = borderSides,
                        hasIcon = false,
                    ) {
                        if (memberContent == null) {
                            ShadcnText(
                                member.label,
                                variant = member.size.text,
                                color = shadcnTheme.buttonForeground(member.variant),
                            )
                        } else {
                            memberContent()
                        }
                    }
                }
                is ButtonGroupMember.Separator -> ShadcnButtonGroupSeparator(member.modifier, orientation)
            }
        }
    }
    when (orientation) {
        // As tall as its tallest member, for the reason the vertical case is as wide as its widest:
        // the separator between members is a hairline that fills the cross axis, and without the
        // intrinsic it fills whatever the *parent* offered. In a height-constrained bar that is
        // invisible; in a panel it made the group as tall as the panel and pushed everything below
        // it off the screen.
        ShadcnButtonGroupOrientation.Horizontal -> Row(modifier.height(IntrinsicSize.Min)) { render() }
        // As wide as its widest member, which is then what every member fills. Without the
        // intrinsic, `fillMaxWidth()` above reads the space the *parent* offered and the group
        // stretches across it -- the reference spells the pair the same way.
        ShadcnButtonGroupOrientation.Vertical -> Column(modifier.width(IntrinsicSize.Min)) { render() }
    }
}

class ShadcnButtonGroupScope internal constructor() {
    internal val items = mutableListOf<ButtonGroupMember>()

    fun button(
        label: String,
        modifier: Modifier = Modifier,
        variant: ShadcnButtonVariant = ShadcnButtonVariant.Default,
        size: ShadcnButtonSizeVariant = ShadcnButtonSizeVariant.Default,
        enabled: Boolean = true,
        onClick: () -> Unit = {},
        content: (
            context(Composer)
            () -> Unit
        )? = null,
    ) {
        items += ButtonGroupMember.Button(label, modifier, variant, size, enabled, onClick, content)
    }

    fun separator(modifier: Modifier = Modifier) {
        items += ButtonGroupMember.Separator(modifier)
    }
}

internal sealed interface ButtonGroupMember {
    data class Button(
        val label: String,
        val modifier: Modifier,
        val variant: ShadcnButtonVariant,
        val size: ShadcnButtonSizeVariant,
        val enabled: Boolean,
        val onClick: () -> Unit,
        val content: (
            context(Composer)
            () -> Unit
        )?,
    ) : ButtonGroupMember
    data class Separator(val modifier: Modifier) : ButtonGroupMember
}

internal fun memberShape(
    orientation: ShadcnButtonGroupOrientation,
    index: Int,
    count: Int,
    radius: Dp,
): Shape {
    val first = index == 0
    val last = index == count - 1
    return when (orientation) {
        ShadcnButtonGroupOrientation.Horizontal -> RoundedCornerShape(
            topStart = if (first) radius else 0.dp,
            topEnd = if (last) radius else 0.dp,
            bottomEnd = if (last) radius else 0.dp,
            bottomStart = if (first) radius else 0.dp,
        )
        ShadcnButtonGroupOrientation.Vertical -> RoundedCornerShape(
            topStart = if (first) radius else 0.dp,
            topEnd = if (first) radius else 0.dp,
            bottomEnd = if (last) radius else 0.dp,
            bottomStart = if (last) radius else 0.dp,
        )
    }
}

internal fun memberBorderSides(
    orientation: ShadcnButtonGroupOrientation,
    index: Int,
    count: Int,
    followedBySeparator: Boolean = false,
): BorderSides = when (orientation) {
    ShadcnButtonGroupOrientation.Horizontal -> BorderSides(
        top = true,
        end = !followedBySeparator,
        bottom = true,
        start = index == 0,
    )
    ShadcnButtonGroupOrientation.Vertical -> BorderSides(
        top = index == 0,
        end = true,
        bottom = !followedBySeparator,
        start = true,
    )
}
enum class ShadcnButtonGroupOrientation {
    Horizontal,
    Vertical,
}
