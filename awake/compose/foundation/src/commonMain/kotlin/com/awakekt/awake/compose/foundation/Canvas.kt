/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.EmptyLayoutPolicy
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.layout.Layout

// PascalCase composables: Compose's own convention, and this is public API surface. See
// 11-refinements.md rule 1.

private object CanvasNodeType

/**
 * A surface to draw on directly, sized by its modifier.
 *
 * The escape hatch from declarative layout, and the reason a second immediate-mode *engine* is not
 * needed: a debug HUD, a gizmo or a chart draws per frame from live state inside one retained node.
 * A full-screen overlay costs exactly one node.
 */
context(_: Composer)
fun Canvas(modifier: Modifier = Modifier, onDraw: DrawScope.() -> Unit) {
    Layout(
        CanvasNodeType,
        modifier = modifier.drawBehind(onDraw),
        measurePolicy = EmptyLayoutPolicy,
    )
}
