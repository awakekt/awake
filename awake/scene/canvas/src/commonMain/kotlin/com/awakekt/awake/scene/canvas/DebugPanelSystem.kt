/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.diagnostics.DebugRegistry
import com.awakekt.awake.core.diagnostics.DebugRowBuffer
import com.awakekt.awake.core.diagnostics.DebugSource
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World

/**
 * Shows the rows of every [DebugSource] that [registry] has on, each source's under its title, in a panel on the
 * canvas ([debugPanelPrefab]), as often as [DebugPanelStyle.refreshSeconds] says.
 *
 * The rows are laid out by a [DebugRowBuffer] and this system only writes what the buffer says changed, so a steady
 * reading sets no text and allocates nothing. With no source on it does nothing: the panel is not made until the
 * first source is on, and is hidden again when the last one is taken out. If the world it was made in is replaced,
 * it is made again.
 *
 * It needs the canvas to draw it, so it belongs in a scene that has one, such as the default infrastructure
 * systems' scene. It reads the canvas's elements through [CanvasParts], which `syncCanvasElements` keeps in step.
 *
 * @param registry The sources to show; hold one for the whole game and pass it to every host.
 * @param style Where the panel sits and how many rows it has.
 */
class DebugPanelSystem(
    private val registry: DebugRegistry,
    private val style: DebugPanelStyle = DebugPanelStyle(),
) : System {
    private val buffer = DebugRowBuffer(style.slots)
    private var panel: DebugPanelParts? = null
    private var panelWorld: World? = null
    private var visible = false
    private var rowsShown = 0
    private var since = style.refreshSeconds

    // What each slot's colours were last set to, so a colour is written only when it changes.
    private val labelColors = IntArray(style.slots)
    private val valueColors = IntArray(style.slots)

    override fun update(world: World, delta: Float) {
        if (registry.active.isEmpty()) {
            hide()
            return
        }
        since += delta
        if (since < style.refreshSeconds) return
        since = 0f
        val parts = panelIn(world)
        val used = buffer.refresh(registry)
        for (slot in 0 until used) show(parts, slot)
        if (used != rowsShown) {
            for (slot in minOf(used, rowsShown) until maxOf(used, rowsShown)) parts.rows[slot].row.visible = slot < used
            parts.frame.height = style.height(used)
            rowsShown = used
        }
        if (!visible) {
            parts.frame.visible = true
            visible = true
        }
    }

    /** Writes slot [slot] to its row if it changed. */
    private fun show(parts: DebugPanelParts, slot: Int) {
        if (!buffer.consumeChanged(slot)) return
        val row = parts.rows[slot]
        val title = buffer.isTitle(slot)
        val labelColor = if (title) ACCENT else NORMAL
        val valueColor = if (buffer.isWarn(slot)) WARN else NORMAL
        val label = buffer.label(slot)
        val value = buffer.value(slot)
        if (row.label.text !== label) row.label.text = label
        if (row.value.text !== value) row.value.text = value
        if (labelColors[slot] != labelColor) {
            labelColors[slot] = labelColor
            row.label.color = if (title) DebugPanelColors.ACCENT else DebugPanelColors.LABEL
        }
        if (valueColors[slot] != valueColor) {
            valueColors[slot] = valueColor
            row.value.color = if (valueColor == WARN) DebugPanelColors.WARN else DebugPanelColors.VALUE
        }
    }

    private fun hide() {
        if (!visible) return
        panel?.frame?.visible = false
        visible = false
        since = style.refreshSeconds
    }

    /** The panel in [world], made the first time it is needed, and again if its world or its entities are gone. */
    private fun panelIn(world: World): DebugPanelParts {
        val current = panel
        if (current != null && panelWorld === world && world.isAlive(current.frame.entity)) return current
        val parts = DebugPanelParts(style.slots)
        val root = world.placeDebugPanel(debugPanelPrefab(parts, style).root)
        parts.resolve(world, root)
        panel = parts
        panelWorld = world
        // The new elements show nothing yet: draw every slot, and every row's visibility and colours, again.
        buffer.invalidate()
        rowsShown = 0
        visible = false
        labelColors.fill(UNSET)
        valueColors.fill(UNSET)
        return parts
    }
}

// The colour states a slot's label or value can be in; UNSET until it is first written.
private const val UNSET = 0
private const val NORMAL = 1
private const val ACCENT = 2
private const val WARN = 3
