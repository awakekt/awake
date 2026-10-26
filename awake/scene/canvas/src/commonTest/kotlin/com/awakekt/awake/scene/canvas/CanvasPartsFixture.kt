/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.math2d.dp
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toComponent
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.ScenePrefab
import com.awakekt.awake.ui.canvas.CanvasButton
import com.awakekt.awake.ui.canvas.CanvasDirection
import com.awakekt.awake.ui.canvas.CanvasLayout
import com.awakekt.awake.ui.canvas.CanvasSweep

// --8<-- [start:canvas-parts]
/** One slot of [ActionBarParts]: the button itself, its icon, its cooldown and its count. */
internal class SlotParts(scope: CanvasPartScope) : CanvasParts(scope) {
    val button = button()
    val icon = image("icon")
    val cooldown = sweep("cooldown")
    val count = text("count")
}

/** A generic action bar: a frame, a title, an energy meter and [slotCount] slots. */
internal class ActionBarParts(slotCount: Int = SLOT_COUNT) : CanvasWidgetParts("bar") {
    val frame = panel()
    val title = text("title")
    val energy = bar("energy")
    val slots = list("slot", slotCount) { SlotParts(it) }
}
// --8<-- [end:canvas-parts]

internal const val SLOT_COUNT = 3
internal const val BAR_GUID = "c0ffee00-0000-4000-8000-0000000010a2"

// --8<-- [start:canvas-parts-build]

/** The action bar built from [ActionBarParts]' handles. */
internal fun actionBarFromParts(parts: ActionBarParts = ActionBarParts()): ScenePrefab =
    canvasPrefab(guid = BAR_GUID, name = "Action Bar", root = parts.frame) {
        layout = CanvasLayout(direction = CanvasDirection.Row, gap = 4f)
        text(parts.title, name = "Title") { textKey = "hud.bar.title" }
        bar(parts.energy, name = "Energy") { size(120.dp, 8.dp) }
        for (slot in parts.slots) {
            button(slot.button, name = "Slot ${slot.index}") {
                size(40.dp, 40.dp)
                button = CanvasButton(cooldownSeconds = 2f)
                image(slot.icon) { size(32.dp, 32.dp) }
                sweep(slot.cooldown) { sweep = CanvasSweep(track = "#00000060") }
                text(slot.count) { fontSize = 10.sp }
            }
        }
    }
// --8<-- [end:canvas-parts-build]

// --8<-- [start:canvas-parts-strings]

/** The same action bar, as code wrote it before handles: every id and name concatenated by hand. */
internal fun actionBarFromStrings(): ScenePrefab =
    canvasPrefab(guid = BAR_GUID, name = "Action Bar", id = "bar") {
        layout = CanvasLayout(direction = CanvasDirection.Row, gap = 4f)
        text("Title", id = "bar.title") { textKey = "hud.bar.title" }
        bar("Energy", id = "bar.energy") { size(120.dp, 8.dp) }
        for (i in 0 until SLOT_COUNT) {
            button("Slot $i", id = "bar.slot-$i") {
                size(40.dp, 40.dp)
                button = CanvasButton(cooldownSeconds = 2f)
                image("bar.slot-$i.icon", id = "bar.slot-$i.icon") { size(32.dp, 32.dp) }
                sweep("bar.slot-$i.cooldown", id = "bar.slot-$i.cooldown") { sweep = CanvasSweep(track = "#00000060") }
                text("bar.slot-$i.count", id = "bar.slot-$i.count") { fontSize = 10.sp }
            }
        }
    }
// --8<-- [end:canvas-parts-strings]

/** Places [node] and everything under it, as a scene loader does for a prefab instance. */
internal fun World.placeCanvas(node: SceneNode, parent: Entity? = null): Entity {
    val entity = create()
    add(entity, Name(node.name.orEmpty()))
    add(entity, Transform(parent = parent))
    add(entity, node.components.filterIsInstance<SceneCanvasElement>().single().toComponent())
    node.children.forEach { placeCanvas(it, entity) }
    return entity
}
