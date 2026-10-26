/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ui.canvas.CanvasElement

/**
 * Binds every part of this declaration, and of the groups in it, to the elements of the placed
 * prefab under [root], and returns it, so its handles write to that copy from now on.
 *
 * It walks the world's canvas elements once, however many parts there are, so call it once, when
 * the prefab is placed, and keep the declaration: reading and writing a handle afterwards does no
 * lookup. Call it again to bind the same declaration to the copy a reload placed.
 *
 * Nothing is bound unless every part is found, so a declaration is never half bound.
 *
 * @param T The declaration's type.
 * @param world The world the prefab was placed in.
 * @param root The placed prefab's root entity: the one its `prefab_link` node, or its root node, made.
 * @return This declaration, resolved.
 * @throws CanvasPartResolutionException naming each part that is missing or of another kind.
 * @throws IllegalArgumentException when two parts share an id or [root] is not alive.
 */
fun <T : CanvasParts> T.resolve(world: World, root: Entity): T {
    val parts = ArrayList<CanvasPart>()
    collectParts(parts)
    world.resolveCanvasParts(root, parts)
    return this
}

/**
 * Binds every part of each item of this list to the elements of the placed prefab under [root], as
 * [CanvasParts.resolve] does, and returns the list.
 *
 * @param T Each item's type.
 * @param world The world the prefab was placed in.
 * @param root The placed prefab's root entity.
 * @return This list, resolved.
 * @throws CanvasPartResolutionException naming each part that is missing or of another kind.
 * @throws IllegalArgumentException when two parts share an id or [root] is not alive.
 */
fun <T : CanvasParts> CanvasPartList<T>.resolve(world: World, root: Entity): CanvasPartList<T> {
    val parts = ArrayList<CanvasPart>()
    forEach { it.collectParts(parts) }
    world.resolveCanvasParts(root, parts)
    return this
}

private fun World.resolveCanvasParts(root: Entity, parts: List<CanvasPart>) {
    require(isAlive(root)) { "cannot resolve canvas parts under $root: it is not alive" }
    syncCanvasElements()
    val wanted = HashMap<String, CanvasPart>(parts.size * 2)
    parts.forEach { part ->
        val previous = wanted.put(part.id, part)
        require(previous == null) { "canvas part id \"${part.id}\" is declared twice" }
    }
    val found = HashMap<String, Pair<Entity, CanvasElement>>(parts.size * 2)
    queryEach(CanvasElement::class) { entity, element ->
        val id = element.elementId
        if (id in wanted && id !in found && isAtOrUnder(entity, root)) found[id] = entity to element
    }
    val problems = parts.mapNotNull { part ->
        val (_, element) = found[part.id] ?: return@mapNotNull part.id to "no element under the root has it"
        if (element.kind != part.kind) part.id to "it is a ${element.kind.name}, not a ${part.kind.name}" else null
    }
    if (problems.isNotEmpty()) {
        val list = problems.joinToString("; ") { (id, why) -> "\"$id\": $why" }
        throw CanvasPartResolutionException("canvas parts did not resolve under $root: $list", problems.map { it.first })
    }
    parts.forEach { part ->
        val (entity, element) = found.getValue(part.id)
        part.bind(entity, element)
    }
}
