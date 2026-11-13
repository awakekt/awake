/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "MatchingDeclarationName")

package com.awakekt.awake.compose.foundation.skin

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.focusable
import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxScope
import com.awakekt.awake.compose.foundation.rememberInteractionSource
import com.awakekt.awake.compose.foundation.sheet.SheetState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.core.color.Color

/**
 * The token look of a [SkinnedButton], used when no skin is provided or the skin lacks
 * [SkinnedKeys.ButtonFace]: one colour per state. Pass theme tokens to match a colour theme.
 *
 * @property normal The colour at rest.
 * @property hovered The colour under the pointer.
 * @property pressed The colour while pressed.
 * @property disabled The colour when the button is not enabled.
 */
class SkinnedButtonColors(
    val normal: Color = Color(0.20f, 0.22f, 0.28f),
    val hovered: Color = Color(0.28f, 0.31f, 0.40f),
    val pressed: Color = Color(0.14f, 0.15f, 0.20f),
    val disabled: Color = Color(0.30f, 0.30f, 0.30f, 0.5f),
) {
    internal fun forState(state: SheetState): Color = when (state) {
        SheetState.Disabled -> disabled
        SheetState.Pressed -> pressed
        SheetState.Hovered -> hovered
        else -> normal
    }
}

/**
 * A button drawn from [LocalSkin]'s [part] (normal, hovered, pressed, disabled frames) and, with no
 * skin or no such part, from [colors]; one layout serves an image skin and a colour theme.
 *
 * It behaves as a click target should: hover and press feed the frame, a disabled button takes no
 * click and leaves the focus ring, and it reports the button role and [label] to semantics. The
 * frame is read each draw, so a state change redraws with no recomposition.
 */
context(_: Composer)
fun SkinnedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    part: StatesKey = SkinnedKeys.ButtonFace,
    colors: SkinnedButtonColors = SkinnedButtonColors(),
    contentAlignment: Alignment = Alignment.Center,
    content: (
        context(Composer)
        BoxScope.() -> Unit
    )? = null,
) {
    val source = rememberInteractionSource()
    val base = modifier
        .skinnedFace(part, source, enabled, selected = false, colors = colors)
        .hoverable(source, enabled)
        .focusable(enabled, source)
        .semantics {
            this[SemanticsProperties.Role] = SemanticsRole.Button
            if (label != null) this[SemanticsProperties.Label] = label
            if (!enabled) this[SemanticsProperties.Disabled] = true
        }
    Box(if (enabled) base.clickable(source, onClick) else base, contentAlignment, content)
}
