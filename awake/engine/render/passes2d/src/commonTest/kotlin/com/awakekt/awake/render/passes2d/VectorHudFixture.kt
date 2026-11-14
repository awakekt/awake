/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.ColoredVertex
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.DrawPoint
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.LinearGradient
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.dp
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A vector-drawn game HUD as one frame's draw commands, with no image skin: what #1545 measured.
 *
 * Status frames (player, target, four party members), a row of buff icons with timers, a quest
 * tracker, two slot bars of twelve slots, a clipped chat panel, and an open inventory window in a flat
 * design: a tab strip, round gauges and a 10 x 8 grid of small rounded slots with 1 px borders and
 * counts. Over a thousand draw spans, the shape of a HUD of a few hundred to a couple of thousand
 * pieces. Every piece is drawn the way a vector theme draws it: soft shadows, rounded panels
 * filled as paths, gradient bars, stroked borders, coloured polygon icons (some under a rounded
 * clip, so they are clipped to its contour) and text. Text and shapes interleave, so most commands
 * open a span of their own, as in a real HUD. Each call builds fresh command objects, as a UI
 * rebuilt every frame does: equal values, new instances.
 */
internal object VectorHudFixture {
    val font: UiFont = UiFonts.default()
    private val panel = Color(0.08f, 0.1f, 0.14f, 0.92f)
    private val border = Color(0.55f, 0.62f, 0.75f, 1f)
    private val shadow = Color(0f, 0f, 0f, 0.55f)
    private val text = Color(0.92f, 0.94f, 0.98f, 1f)
    private val health = LinearGradient.horizontal(Color(0.85f, 0.15f, 0.12f, 1f), Color(0.55f, 0.05f, 0.05f, 1f))
    private val mana = LinearGradient.horizontal(Color(0.15f, 0.35f, 0.9f, 1f), Color(0.05f, 0.15f, 0.55f, 1f))

    /**
     * Whether rounded borders are analytic, one `RoundedQuad` with a stroke width each, as
     * `Modifier.border` draws them since #1545, or stroked paths, as it drew them before.
     */
    private var analyticBorders = true

    /** The whole HUD for a [width] x [height] surface; [analytic] picks the border form, see [analyticBorders]. */
    fun frame(width: Float = 1920f, height: Float = 1080f, analytic: Boolean = true): List<DrawCommand> = buildList {
        analyticBorders = analytic
        statusFrame(24f, 24f, "Player One", large = true)
        statusFrame(420f, 24f, "Training Dummy", large = true)
        repeat(PARTY) { statusFrame(24f, 140f + it * 72f, "Member ${it + 1}", large = false) }
        repeat(BUFFS) { buff(24f + it * 40f, 470f, it) }
        questTracker(width - 344f, 260f)
        repeat(SLOT_BARS) { bar -> slotBar(width / 2f - 330f, height - 80f - bar * 64f, firstKey = bar * SLOTS) }
        chatPanel(24f, height - 320f)
        inventoryWindow(width - 620f, 120f)
    }

    private fun MutableList<DrawCommand>.inventoryWindow(x: Float, y: Float) {
        add(DrawCommand.FilledPath(rounded(x, y, 580f, 560f, 8f), panel))
        border(x, y, 580f, 560f, 8f, 1f)
        repeat(TABS) { tab ->
            val tx = x + 12f + tab * 92f
            add(DrawCommand.FilledPath(rounded(tx, y + 10f, 86f, 26f, 13f), if (tab == 0) border else panel))
            border(tx, y + 10f, 86f, 26f, 13f, 1f)
            text(tx + 12f, y + 16f, "Tab ${tab + 1}")
        }
        repeat(GAUGES) { gauge ->
            val cx = x + 60f + gauge * 120f
            add(DrawCommand.StrokedPath(DrawShape.Circle.toPath(Rectangle(cx - 26f, y + 50f, 52f, 52f)), DrawStroke(4f.dp), border))
            text(cx - 12f, y + 70f, "${40 + gauge * 10}%")
        }
        repeat(GRID_ROWS * GRID_COLUMNS) { slot ->
            val sx = x + 14f + (slot % GRID_COLUMNS) * 55f
            val sy = y + 120f + (slot / GRID_COLUMNS) * 54f
            add(DrawCommand.FilledPath(rounded(sx, sy, 48f, 48f, 6f), Color(0.14f, 0.16f, 0.22f, 1f)))
            border(sx, sy, 48f, 48f, 6f, 1f)
            add(DrawCommand.Mesh(polygon(sx + 24f, sy + 22f, 13f, sides = 4 + slot % 5, Color(0.6f, 0.45f + slot % 7 * 0.05f, 0.3f, 1f))))
            text(sx + 30f, sy + 32f, "${slot % 20 + 1}")
        }
    }

    private fun MutableList<DrawCommand>.statusFrame(x: Float, y: Float, name: String, large: Boolean) {
        val w = if (large) 360f else 240f
        val h = if (large) 96f else 60f
        softPanel(x, y, w, h, radius = 10f)
        add(DrawCommand.Mesh(polygon(x + h / 2f, y + h / 2f, h / 2f - 8f, sides = 6, Color(0.7f, 0.6f, 0.3f, 1f))))
        text(x + h + 4f, y + 8f, name)
        bar(x + h + 4f, y + h * 0.42f, w - h - 16f, h * 0.2f, health)
        text(x + h + 10f, y + h * 0.42f, "1840 / 2000")
        bar(x + h + 4f, y + h * 0.7f, w - h - 16f, h * 0.16f, mana)
        text(x + h + 10f, y + h * 0.7f, "920 / 1000")
    }

    private fun MutableList<DrawCommand>.slotBar(x: Float, y: Float, firstKey: Int) {
        softPanel(x, y, SLOTS * 54f + 12f, 60f, radius = 12f)
        repeat(SLOTS) { slot ->
            val sx = x + 8f + slot * 54f
            add(DrawCommand.RoundedQuad(sx, y + 6f, 48f, 48f, Color(0.14f, 0.16f, 0.22f, 1f), radius = 8f, smoothing = 1f))
            add(DrawCommand.Mesh(polygon(sx + 24f, y + 30f, 16f, sides = 5 + slot % 4, Color(0.3f + slot * 0.05f, 0.5f, 0.7f, 1f))))
            border(sx, y + 6f, 48f, 48f, 8f, 1.5f)
            text(sx + 4f, y + 8f, "${(firstKey + slot) % 10}")
        }
    }

    private fun MutableList<DrawCommand>.buff(x: Float, y: Float, index: Int) {
        add(DrawCommand.RoundedQuad(x, y, 34f, 34f, Color(0.12f, 0.14f, 0.2f, 1f), radius = 6f, smoothing = 1f))
        add(DrawCommand.Mesh(polygon(x + 17f, y + 17f, 12f, sides = 3 + index % 5, Color(0.8f, 0.7f - index * 0.02f, 0.2f, 1f))))
        text(x + 4f, y + 36f, "${index + 1}m")
    }

    private fun MutableList<DrawCommand>.questTracker(x: Float, y: Float) {
        softPanel(x, y, 320f, QUEST_LINES * 22f + 16f, radius = 10f)
        repeat(QUEST_LINES) { line ->
            add(DrawCommand.Mesh(polygon(x + 14f, y + 18f + line * 22f, 5f, sides = 4, Color(0.95f, 0.8f, 0.3f, 1f))))
            text(x + 26f, y + 10f + line * 22f, "Objective $line: 0 / 5")
        }
    }

    private fun MutableList<DrawCommand>.chatPanel(x: Float, y: Float) {
        val w = 520f
        val h = 280f
        softPanel(x, y, w, h, radius = 14f)
        val clip = rounded(x, y, w, h, 14f)
        add(DrawCommand.ClipPathPush(clip, Rectangle(x, y, w, h), safeInteriorRect = Rectangle(x + 14f, y + 14f, w - 28f, h - 28f)))
        repeat(CHAT_LINES) { line ->
            val ly = y + 8f + line * 18f
            // The channel badge sits on the panel's edge, so it is clipped to the rounded contour.
            add(DrawCommand.Mesh(polygon(x + 4f, ly + 8f, 7f, sides = 8, Color(0.4f, 0.8f, 0.4f, 1f))))
            text(x + 16f, ly, "[Party] Member ${line % PARTY + 1}: line $line of the conversation")
        }
        add(DrawCommand.ClipPop(Rectangle(0f, 0f, 1920f, 1080f)))
    }

    private fun MutableList<DrawCommand>.softPanel(x: Float, y: Float, w: Float, h: Float, radius: Float) {
        add(DrawCommand.ShadowQuad(x, y, w, h, radius, offsetX = 0f, offsetY = 6f, blurRadius = 18f, spread = 2f, color = shadow))
        add(DrawCommand.FilledPath(rounded(x, y, w, h, radius), panel))
        border(x, y, w, h, radius, 2f)
    }

    private fun MutableList<DrawCommand>.bar(x: Float, y: Float, w: Float, h: Float, gradient: LinearGradient) {
        add(DrawCommand.RoundedQuad(x, y, w, h, Color(0f, 0f, 0f, 0.6f), radius = h / 2f, smoothing = 1f))
        add(DrawCommand.GradientQuad(x + 2f, y + 2f, (w - 4f) * 0.9f, h - 4f, gradient))
    }

    private fun MutableList<DrawCommand>.text(x: Float, y: Float, value: String) {
        value.forEachIndexed { i, _ ->
            add(DrawCommand.Glyph(x + i * 8f, y, 8f, 14f, 0f, 0f, 0.1f, 0.1f, text, font = font))
        }
    }

    private fun MutableList<DrawCommand>.border(x: Float, y: Float, w: Float, h: Float, r: Float, stroke: Float) {
        add(
            if (analyticBorders) {
                DrawCommand.RoundedQuad(x, y, w, h, border, r, strokeWidth = stroke)
            } else {
                DrawCommand.StrokedPath(rounded(x, y, w, h, r), DrawStroke(stroke.dp), border)
            },
        )
    }

    private fun rounded(x: Float, y: Float, w: Float, h: Float, r: Float) =
        DrawShape.RoundedRectangle(r.dp).toPath(Rectangle(x, y, w, h))

    /** A filled regular polygon as a centre fan, the shape of a vector icon. */
    private fun polygon(cx: Float, cy: Float, radius: Float, sides: Int, color: Color): ColoredTriangleMesh {
        val vertices = ArrayList<ColoredVertex>(sides + 1)
        vertices += ColoredVertex(DrawPoint(cx, cy), color)
        repeat(sides) {
            val angle = 2.0 * PI * it / sides
            vertices += ColoredVertex(DrawPoint(cx + radius * cos(angle).toFloat(), cy + radius * sin(angle).toFloat()), color)
        }
        val indices = IntArray(sides * 3) { i ->
            val corner = i / 3
            when (i % 3) {
                0 -> 0
                1 -> corner + 1
                else -> (corner + 1) % sides + 1
            }
        }
        return ColoredTriangleMesh(vertices, indices)
    }

    const val PARTY = 4
    const val SLOT_BARS = 2
    const val SLOTS = 12
    const val CHAT_LINES = 14
    const val BUFFS = 20
    const val QUEST_LINES = 10
    const val TABS = 6
    const val GAUGES = 4
    const val GRID_ROWS = 8
    const val GRID_COLUMNS = 10
}
