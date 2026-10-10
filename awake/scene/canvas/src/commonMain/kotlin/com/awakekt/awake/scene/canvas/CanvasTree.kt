/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform

/**
 * The elements of a world, each under the nearest element above it in the scene hierarchy. One that
 * follows a node is placed against the screen wherever it sits in the scene.
 */
internal class CanvasTree(
    val world: World,
    private val showTouchControls: Boolean,
    val images: Map<String, ImageBitmap>,
    val projector: CanvasProjector?,
) {
    val roots = ArrayList<Pair<Entity, CanvasElement>>()
    private val children = HashMap<Entity, ArrayList<Pair<Entity, CanvasElement>>>()
    private val named: Map<String, Entity> by lazy {
        HashMap<String, Entity>().also { names -> world.family<Name>().forEach { entity, name -> names.getOrPut(name.value) { entity } } }
    }

    init {
        world.family<CanvasElement>().forEach { entity, element ->
            val parent = if (element.follow.isEmpty()) world.canvasParent(entity) else null
            (if (parent == null) roots else children.getOrPut(parent) { ArrayList() }) += entity to element
        }
        roots.sortBy { it.second.order }
        for (siblings in children.values) siblings.sortBy { it.second.order }
    }

    fun childrenOf(entity: Entity): List<Pair<Entity, CanvasElement>> = children[entity].orEmpty()

    fun shows(element: CanvasElement): Boolean = element.visible && (showTouchControls || !element.touchOnly)

    /** The first node named [name], or null when there is none. */
    fun node(name: String): Entity? = named[name]
}

/**
 * Where a following [element] goes, in dp at [density] pixels each: on its node's screen box, or
 * with its anchor point on the node's screen point, nudged inward by its offsets. Null hides it: no
 * projector, no such node, or the node behind the camera.
 */
internal fun CanvasTree.placeFollower(element: CanvasElement, density: Float): Rectangle? {
    val projector = projector
    val node = node(element.follow)
    if (projector == null || node == null) return null
    val box = if (element.followBounds) projector.bounds(node) else null
    return if (box != null) {
        Rectangle(box.x / density, box.y / density, box.width / density, box.height / density)
    } else {
        placeOnPoint(element, node, projector, density)
    }
}

/** [element] with its anchor point on [node]'s screen point, or null when the node is behind the camera. */
private fun CanvasTree.placeOnPoint(element: CanvasElement, node: Entity, projector: CanvasProjector, density: Float): Rectangle? {
    val offset = element.followOffset
    val point = world.get<Transform>(node)?.worldMatrix?.let { projector.project(it.m03 + offset.x, it.m13 + offset.y, it.m23 + offset.z) }
        ?: return null
    val column = element.anchor.column
    val row = element.anchor.row
    val x = point.x / density - column / 2f * element.width + if (column == 2) -element.offsetX else element.offsetX
    val y = point.y / density - row / 2f * element.height + if (row == 2) -element.offsetY else element.offsetY
    return Rectangle(x, y, element.width, element.height)
}

private fun World.canvasParent(entity: Entity): Entity? {
    var node = get<Transform>(entity)?.parent
    while (node != null) {
        if (has(node, CanvasElement::class)) return node
        node = get<Transform>(node)?.parent
    }
    return null
}
