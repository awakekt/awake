/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.node
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.ScenePrefab

// Placing a canvas prefab from declarative content. The prefab stays the authoring format, the one
// an editor opens and saves; code places it where its state says, and binds its parts.

context(_: Composer)
/**
 * Places [prefab] here: its root element on this node's entity and the elements under it below,
 * made once and kept while this place, or its `key`, declares the same prefab. The placed copy is
 * destroyed with the node.
 *
 * @param prefab A canvas prefab, such as [canvasPrefab] builds or [CanvasPrefabs.fromJson] reads.
 * @throws IllegalArgumentException when a node of [prefab] has a component that is not a canvas one.
 */
fun prefab(prefab: ScenePrefab) {
    node(PlacedPrefab(prefab.guid), update = { node -> contentNode(node).place(prefab) })
}

context(_: Composer)
/**
 * Places [prefab] here as [prefab] with no parts does, and binds it on every pass: [parts] makes the
 * prefab's [CanvasParts] declaration once for this copy, which is resolved against it, and [bind]
 * writes the copy's overrides through those typed handles.
 *
 * ```kotlin
 * prefab(unitFramePrefab, parts = { UnitFrameParts() }) {
 *     name.text = member.name
 *     health.value = member.health
 * }
 * ```
 *
 * @param P The prefab's parts declaration.
 * @param prefab A canvas prefab.
 * @param parts Makes the declaration of [prefab]'s parts: called once per placed copy.
 * @param bind Writes this pass's values through the resolved parts.
 * @throws IllegalStateException when a part [parts] declares is missing from [prefab].
 */
fun <P : CanvasParts> prefab(prefab: ScenePrefab, parts: () -> P, bind: P.() -> Unit) {
    node(
        PlacedPrefab(prefab.guid),
        update = { node ->
            val placed = contentNode(node)
            if (placed.place(prefab) || placed.bound == null) {
                placed.bound = parts().resolve(placed.applier.world, placed.entity)
            }
            @Suppress("UNCHECKED_CAST") // Made by this call's parts, at this place, for this prefab.
            (placed.bound as P).bind()
        },
    )
}

/** What a placed prefab is declared as: another prefab at the same place is another node. */
private data class PlacedPrefab(val guid: String)

/** Places [prefab] on this node, in place of another it held; returns whether it placed anything. */
private fun CanvasContentNode.place(prefab: ScenePrefab): Boolean {
    if (placed == prefab) return false
    val world = applier.world
    for (index in owned.indices.reversed()) world.destroy(owned[index])
    val made = ArrayList<Entity>()
    world.add(entity, Name(prefab.root.name.orEmpty()))
    applier.write(this, prefab.root.canvasComponents())
    for (child in prefab.root.children) world.placeCanvasNode(child, entity, made)
    owned = made
    placed = prefab
    bound = null
    return true
}

/** Makes [node] and everything under it as canvas entities under [parent], adding each to [made]. */
private fun World.placeCanvasNode(node: SceneNode, parent: Entity, made: MutableList<Entity>) {
    val entity = create()
    made += entity
    add(entity, Name(node.name.orEmpty()))
    add(entity, Transform(parent = parent))
    for (part in node.canvasComponents()) attachCanvasComponent(entity, part.toCanvasPart())
    for (child in node.children) placeCanvasNode(child, entity, made)
}

/** This node's canvas components, an older `canvas_element` read as the ones it is now. */
private fun SceneNode.canvasComponents(): List<SceneComponent> {
    require(components.all { it.isCanvasElementPart() }) { "${name.orEmpty()}: only canvas components can be placed from canvas content" }
    return components.flatMap { if (it is SceneCanvasElement) it.toCanvasComponents() else listOf(it) }
}
