/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.diagnostics.DebugRowBuffer
import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.core.math2d.Sp
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.ScenePrefab
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasDirection
import com.awakekt.awake.ui.canvas.CanvasLayout
import com.awakekt.awake.ui.canvas.CanvasShadow
import com.awakekt.awake.ui.canvas.CanvasStyle
import com.awakekt.awake.ui.canvas.CanvasTextShadow

/**
 * Where the debug panel sits and how big it is. The panel is [slots] rows of a label on the left and a value on the
 * right, in a small dark box; a source's title takes a row of its own, in an accent colour.
 *
 * @property slots Rows the panel has room for, titles included. More rows than this are dropped.
 * @property anchor The screen corner or edge the panel is pinned to.
 * @property offsetX How far in dp the panel sits inward from its anchor along the X axis.
 * @property offsetY How far in dp the panel sits inward from its anchor along the Y axis.
 * @property width The panel's width in dp.
 * @property refreshSeconds How long the panel waits between readings: 0.1 is ten times a second.
 * @property order The panel's draw order among the canvas's other roots; a high one draws over them.
 */
class DebugPanelStyle(
    val slots: Int = DebugRowBuffer.DEFAULT_SLOTS,
    val anchor: CanvasAnchor = CanvasAnchor.BottomRight,
    val offsetX: Float = DEFAULT_OFFSET,
    val offsetY: Float = DEFAULT_OFFSET,
    val width: Float = DEFAULT_WIDTH,
    val refreshSeconds: Float = DEFAULT_REFRESH_SECONDS,
    val order: Int = DEFAULT_ORDER,
) {
    init {
        require(slots >= 0) { "slots ($slots) must not be negative" }
        require(width > 2 * PADDING) { "width ($width) must leave room inside the panel's padding" }
        require(refreshSeconds >= 0f) { "refreshSeconds ($refreshSeconds) must not be negative" }
    }

    override fun equals(other: Any?): Boolean = other is DebugPanelStyle && slots == other.slots && anchor == other.anchor &&
        offsetX == other.offsetX && offsetY == other.offsetY && width == other.width &&
        refreshSeconds == other.refreshSeconds && order == other.order

    override fun hashCode(): Int = listOf(slots, anchor, offsetX, offsetY, width, refreshSeconds, order).hashCode()

    override fun toString(): String =
        "DebugPanelStyle(slots=$slots, anchor=$anchor, offsetX=$offsetX, offsetY=$offsetY, width=$width, " +
            "refreshSeconds=$refreshSeconds, order=$order)"

    /** The panel's height in dp with [rows] rows showing. */
    fun height(rows: Int): Float = PADDING * 2 + ROW_HEIGHT * rows + GAP * (rows - 1).coerceAtLeast(0)

    /** Defaults for a panel in the screen's bottom right. */
    companion object {
        /** The panel's distance in dp from its anchor's edges. */
        const val DEFAULT_OFFSET: Float = 12f

        /** The panel's width in dp. */
        const val DEFAULT_WIDTH: Float = 220f

        /** Ten readings a second. */
        const val DEFAULT_REFRESH_SECONDS: Float = 0.1f

        /** Over a game's own HUD, which draws at the default order. */
        const val DEFAULT_ORDER: Int = 950
    }
}

/**
 * The debug panel as a prefab: the same [ScenePrefab] an editor would save, with [DebugPanelStyle.slots] rows hidden
 * until [DebugPanelSystem] shows them. Its elements are found by the ids in [DebugPanelParts], not by name.
 *
 * Use it to look at the panel in an editor or to place one by hand; [DebugPanelSystem] makes its own.
 *
 * @param style Where the panel sits and how many rows it has.
 * @return The validated prefab.
 */
fun debugPanelPrefab(style: DebugPanelStyle = DebugPanelStyle()): ScenePrefab = debugPanelPrefab(DebugPanelParts(style.slots), style)

/** One row of the panel: the box, its label on the left and its value on the right. */
internal class DebugRowParts(scope: CanvasPartScope) : CanvasParts(scope) {
    val row = panel()
    val label = text("label")
    val value = text("value")
}

/** The panel's elements, declared once so the prefab and the system that writes to it agree on their ids. */
internal class DebugPanelParts(slots: Int) : CanvasWidgetParts(ROOT_ID) {
    val frame = panel()
    val rows = list("row", slots) { DebugRowParts(it) }
}

internal fun debugPanelPrefab(parts: DebugPanelParts, style: DebugPanelStyle): ScenePrefab =
    canvasPrefab(guid = GUID, name = "Debug Panel", root = parts.frame) {
        anchor = style.anchor
        offset(Dp(style.offsetX), Dp(style.offsetY))
        size(Dp(style.width), Dp(style.height(1)))
        background = Color.fromHex(PANEL)
        this.style = CanvasStyle(
            cornerRadius = RADIUS,
            borderWidth = 1f,
            borderColor = BORDER,
            shadow = CanvasShadow(DROP, offsetX = 0f, offsetY = 2f, blur = 8f, spread = 0f),
        )
        order = style.order
        visible = false
        layout = CanvasLayout(direction = CanvasDirection.Column, gap = GAP, padding = PADDING)
        val inner = style.width - PADDING * 2
        val labelWidth = inner * LABEL_SHARE
        for (slot in parts.rows) {
            panel(slot.row) {
                size(Dp(inner), Dp(ROW_HEIGHT))
                visible = false
                layout = CanvasLayout(direction = CanvasDirection.Row, gap = 0f)
                text(slot.label) {
                    size(Dp(labelWidth), Dp(ROW_HEIGHT))
                    textAlign = CanvasAnchor.CenterLeft
                    fontSize = Sp(LABEL_FONT)
                    color = Color.fromHex(DebugPanelColors.LABEL)
                    this.style = TEXT
                }
                text(slot.value) {
                    size(Dp(inner - labelWidth), Dp(ROW_HEIGHT))
                    textAlign = CanvasAnchor.CenterRight
                    fontSize = Sp(VALUE_FONT)
                    color = Color.fromHex(DebugPanelColors.VALUE)
                    this.style = TEXT
                }
            }
        }
    }

/** The panel's text colours; the system sets them as a slot changes between a title, a row and a warning. */
internal object DebugPanelColors {
    const val LABEL = "#9AA0A6"
    const val VALUE = "#E8EAED"
    const val ACCENT = "#6CC4FF"
    const val WARN = "#FFB52E"
}

/**
 * Makes [node] and everything under it in this world, each node an entity under its parent, with the canvas
 * components its node holds. The loader does this for a scene's nodes, which this module doesn't reach.
 */
internal fun World.placeDebugPanel(node: SceneNode, parent: Entity? = null): Entity {
    val entity = create()
    add(entity, Name(node.name.orEmpty()))
    add(entity, Transform(parent = parent))
    val context = object : SceneResolutionContext {
        override val world: World = this@placeDebugPanel

        override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit

        override fun recordRequest(request: Any) = Unit
    }
    for (component in node.components) canvasBindings.resolve(this, entity, component, context)
    for (child in node.children) placeDebugPanel(child, entity)
    return entity
}

/** Only the canvas bindings, held to themselves so the panel adds no serializer to the global set. */
private val canvasBindings: SceneComponentRegistry = SceneComponentRegistry.scoped(bindings = CanvasSceneComponents.bindings)

internal const val ROOT_ID = "debug-panel"

private const val GUID = "5b0e2c1d-8f47-4a6b-9c3e-2d71a0f4b6e9"
private const val PANEL = "#101216D9"
private const val BORDER = "#FFFFFF33"
private const val SHADOW = "#000000CC"
private const val DROP = "#00000059"
private const val PADDING = 10f
private const val GAP = 2f
private const val ROW_HEIGHT = 17f
private const val RADIUS = 6f
private const val LABEL_FONT = 12f
private const val VALUE_FONT = 14f
private const val LABEL_SHARE = 0.5f
private val TEXT = CanvasStyle(textShadow = CanvasTextShadow(SHADOW, offsetX = 0f, offsetY = 1f))
