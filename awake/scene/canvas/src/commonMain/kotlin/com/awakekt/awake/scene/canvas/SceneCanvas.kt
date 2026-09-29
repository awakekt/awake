/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.gestures.draggable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clip
import com.awakekt.awake.compose.ui.graphics.CircleShape
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World

/**
 * Draws [world]'s visible [CanvasElement]s over whatever is beneath, lowest [CanvasElement.order]
 * first. Each element is tagged `canvas-element-<entity id>` for tests and editor picking.
 * [CanvasElement.touchOnly] elements are drawn only when [showTouchControls] is true.
 */
context(_: Composer)
fun SceneCanvas(world: World, modifier: Modifier = Modifier, showTouchControls: Boolean = false) {
    val elements = ArrayList<Pair<Entity, CanvasElement>>()
    world.family<CanvasElement>().forEach { entity, element ->
        if (element.visible && (showTouchControls || !element.touchOnly)) elements += entity to element
    }
    elements.sortBy { it.second.order }
    Box(modifier.fillMaxSize()) {
        for ((entity, element) in elements) {
            key(entity) {
                // The inset wraps the element rather than sitting in its modifier chain, so the
                // element's own bounds (hit area, editor picking) are exactly its size.
                Box(Modifier.align(element.anchor.alignment).anchorInset(element)) {
                    CanvasElementView(
                        element,
                        Modifier.size(element.width.dp, element.height.dp).testTag("canvas-element-${entity.id}"),
                    )
                }
            }
        }
    }
}

context(_: Composer)
private fun CanvasElementView(element: CanvasElement, modifier: Modifier) {
    val fill = colorOf(element.color, Color.White)
    val back = colorOf(element.background, Color.Transparent)
    val textStyle = TextStyle(color = fill, size = element.fontSize.sp)
    when (element.kind) {
        CanvasElementKind.Text -> Box(modifier.background(back)) { Text(element.text, style = textStyle) }
        CanvasElementKind.Panel -> Box(modifier.background(back))
        CanvasElementKind.Bar -> Box(modifier.background(back)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(element.value.coerceIn(0f, 1f)).background(fill))
        }
        CanvasElementKind.Button -> Box(
            modifier.background(back).clickable(element.interactions) { element.press() },
            contentAlignment = Alignment.Center,
        ) { Text(element.text, style = textStyle) }
        CanvasElementKind.Joystick -> JoystickView(element, modifier, back, fill)
    }
}

/** A round pad whose knob follows a drag, up to the pad's edge, and springs back on release. */
context(_: Composer)
private fun JoystickView(element: CanvasElement, modifier: Modifier, back: Color, knob: Color) {
    val density = LocalDensity.current
    val radius = minOf(element.width, element.height) / 2f * density
    Box(
        modifier.clip(CircleShape).background(back)
            .draggable(
                onDrag = { dx, dy -> element.dragStick(dx.toFloat(), dy.toFloat(), radius) },
                onDragStopped = element::releaseStick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val size = minOf(element.width, element.height) * KNOB_FRACTION
        Box(
            Modifier.offset((element.knobX / density).dp, (element.knobY / density).dp)
                .size(size.dp).clip(CircleShape).background(knob),
        )
    }
}

private const val KNOB_FRACTION = 0.45f

/**
 * Moves the element inward from its anchor with padding, not `offset`: `offset` only moves the
 * drawing, so hit-testing and editor picking would still find the element at the anchor.
 * On a centred axis the padding goes on one side twice over, which shifts the centred box by the
 * offset. Offsets pointing off-screen (negative) are clamped to the anchor.
 */
private fun Modifier.anchorInset(element: CanvasElement): Modifier {
    val x = element.offsetX.coerceAtLeast(0f)
    val y = element.offsetY.coerceAtLeast(0f)
    val start = when (element.anchor.column) { 0 -> x; 1 -> 2 * x; else -> 0f }
    val end = if (element.anchor.column == 2) x else 0f
    val top = when (element.anchor.row) { 0 -> y; 1 -> 2 * y; else -> 0f }
    val bottom = if (element.anchor.row == 2) y else 0f
    return padding(start = start.dp, top = top.dp, end = end.dp, bottom = bottom.dp)
}

/** 0 = left, 1 = centre, 2 = right. */
private val CanvasAnchor.column: Int get() = ordinal % 3

/** 0 = top, 1 = centre, 2 = bottom. */
private val CanvasAnchor.row: Int get() = ordinal / 3

private val CanvasAnchor.alignment: Alignment
    get() = when (this) {
        CanvasAnchor.TopLeft -> Alignment.TopStart
        CanvasAnchor.TopCenter -> Alignment.TopCenter
        CanvasAnchor.TopRight -> Alignment.TopEnd
        CanvasAnchor.CenterLeft -> Alignment.CenterStart
        CanvasAnchor.Center -> Alignment.Center
        CanvasAnchor.CenterRight -> Alignment.CenterEnd
        CanvasAnchor.BottomLeft -> Alignment.BottomStart
        CanvasAnchor.BottomCenter -> Alignment.BottomCenter
        CanvasAnchor.BottomRight -> Alignment.BottomEnd
    }

// A colour typed wrong in the editor falls back instead of taking the whole frame down.
private fun colorOf(hex: String, fallback: Color): Color =
    if (isHexColor(hex)) Color.fromHex(hex) else fallback
