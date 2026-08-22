// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui.graphics

import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive
import io.github.ronjunevaldoz.awake.core.graphics2d.UiPath
import io.github.ronjunevaldoz.awake.ui.UiPrimitiveScope
import io.github.ronjunevaldoz.awake.core.graphics2d.UiShapeSpec
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle
import io.github.ronjunevaldoz.awake.core.graphics2d.bounds
import io.github.ronjunevaldoz.awake.core.graphics2d.safeInteriorMargin
import io.github.ronjunevaldoz.awake.core.graphics2d.toPath

fun UiPrimitiveScope.clip(rect: Rectangle, content: UiPrimitiveScope.() -> Unit) {
    val resolved = context.pushClipInternal(rect, overlay = emitsToOverlay)
    dispatchPrimitive(UiDrawPrimitive.ClipPush(resolved), overlay = false)
    content()
    val restore = context.popClipInternal(overlay = emitsToOverlay)
    dispatchPrimitive(UiDrawPrimitive.ClipPop(restore), overlay = false)
}

fun UiPrimitiveScope.clip(path: UiPath, content: UiPrimitiveScope.() -> Unit) = clipPath(path, safeInteriorRect = null, content)

fun UiPrimitiveScope.clip(shape: UiShapeSpec, rect: Rectangle, content: UiPrimitiveScope.() -> Unit) {
    // A trial pass discards every primitive it emits (UiContext.emit is gated on `!measuring`), so
    // tessellating the shape's corners here is pure garbage -- and trial passes are the frame's
    // multiplier. A rounded rect's path bounds are the rect it was built from, so clipping the
    // trial to the rect leaves its clip stack identical to what the path would have produced.
    if (context.isMeasuringInternal()) {
        clip(rect, content)
        return
    }
    val margin = shape.safeInteriorMargin(rect)
    val safeInteriorRect = Rectangle(
        x = rect.x + margin,
        y = rect.y + margin,
        width = (rect.width - 2f * margin).coerceAtLeast(0f),
        height = (rect.height - 2f * margin).coerceAtLeast(0f),
    )
    clipPath(shape.toPath(rect), safeInteriorRect, content)
}

private fun UiPrimitiveScope.clipPath(path: UiPath, safeInteriorRect: Rectangle?, content: UiPrimitiveScope.() -> Unit) {
    val resolvedBounds = context.pushClipInternal(path.bounds(), overlay = emitsToOverlay)
    dispatchPrimitive(UiDrawPrimitive.ClipPathPush(path, resolvedBounds, safeInteriorRect), overlay = false)
    content()
    val restore = context.popClipInternal(overlay = emitsToOverlay)
    dispatchPrimitive(UiDrawPrimitive.ClipPop(restore), overlay = false)
}
