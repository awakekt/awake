/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.ScenePrefab
import com.awakekt.awake.scene.document.SceneSerializers
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.document.SceneValidator
import com.awakekt.awake.ui.canvas.CanvasElement
import kotlinx.serialization.json.Json

/** Validates, reads and writes prefabs of `canvas_element` nodes, such as one [canvasPrefab] built. */
object CanvasPrefabs {
    /**
     * The JSON a canvas prefab is written in: the prefab format's, pretty-printed, that knows a
     * `canvas_element` whether or not anything registered it, and keeps any component it does not
     * know as data, so a prefab with another module's components reads and writes back whole.
     */
    val json: Json = Json(
        SceneSerializers.createJson(mapOf(SceneCanvasElement::class to SceneCanvasElement.serializer()), keepUnknownComponents = true),
    ) { prettyPrint = true }

    /**
     * [prefab] as JSON, the form an editor opens as it is: every field written, colours as hex.
     *
     * @param prefab The prefab to write.
     * @return The prefab's JSON.
     */
    fun toJson(prefab: ScenePrefab): String = prefab.toJson(json)

    /**
     * The prefab [text] holds, read with [json].
     *
     * @param text A prefab's JSON.
     * @return The prefab.
     */
    fun fromJson(text: String): ScenePrefab = ScenePrefab.fromJson(text, json)

    /**
     * What is wrong with [prefab]: its GUID is blank, a node's own components fail validation, two
     * nodes share a name, or two elements share an [SceneCanvasElement.elementId], so code could not
     * tell them apart. Empty when it is fine.
     *
     * @param prefab The prefab to check.
     * @return Each problem, with the path of the node it is on.
     */
    fun problems(prefab: ScenePrefab): List<SceneValidationIssue> = buildList {
        if (prefab.guid.isBlank()) add(SceneValidationIssue(prefab.name ?: "prefab", "prefab guid must not be blank"))
        addAll(SceneValidator.validate(SceneDocument(nodes = listOf(prefab.root))))
        val seen = HashMap<String, String>()
        prefab.root.visit(path = prefab.root.name ?: "#0") { node, path ->
            val id = node.canvasElement()?.elementId?.takeIf(String::isNotEmpty) ?: return@visit
            val first = seen[id]
            if (first == null) seen[id] = path else add(SceneValidationIssue(path, "canvas_element.elementId \"$id\" is already used at $first"))
        }
    }
}

/**
 * The node at or under this one whose `canvas_element` has [elementId], or null: how code finds an
 * element of a prefab by its stable id rather than by a name that changes on a rename.
 *
 * @param elementId The element's [SceneCanvasElement.elementId].
 * @return The first such node, depth first.
 */
fun SceneNode.findCanvasNode(elementId: String): SceneNode? = when {
    elementId.isEmpty() -> null
    canvasElement()?.elementId == elementId -> this
    else -> children.firstNotNullOfOrNull { it.findCanvasNode(elementId) }
}

/**
 * The node of this prefab whose `canvas_element` has [elementId], or null.
 *
 * @param elementId The element's [SceneCanvasElement.elementId].
 * @return The node, or null when no element has that id.
 */
fun ScenePrefab.findCanvasNode(elementId: String): SceneNode? = root.findCanvasNode(elementId)

/**
 * The entity at or under [root] whose [CanvasElement] has [elementId], or null: how code finds an
 * element of one placed prefab, such as its health bar, by the id its prefab gave it. Unlike a name,
 * the id survives a rename, and a second instance of the prefab, whose root an editor may name
 * `Unit Frame 2`, keeps its own elements apart under its own root.
 *
 * It looks at every canvas element in the world, so look up once, when the prefab is placed, and
 * keep the entity rather than calling it every frame. Code that builds the prefab and binds it
 * declares its elements as [CanvasParts] instead, and [resolve]s them once into typed handles; this
 * string lookup stays for tools and data-only lookups.
 *
 * @param root The placed prefab's root entity: the one its `prefab_link` node, or its root node, made.
 * @param elementId The element's [CanvasElement.elementId].
 * @return The entity, or null when none under [root] has that id.
 */
fun World.findCanvasElement(root: Entity, elementId: String): Entity? {
    if (elementId.isEmpty() || !isAlive(root)) return null
    var found: Entity? = null
    queryEach(CanvasElement::class) { entity, element ->
        if (found == null && element.elementId == elementId && isAtOrUnder(entity, root)) found = entity
    }
    return found
}

/** Whether [entity] is [root] or has it as a [Transform] ancestor; a parent chain that loops is neither. */
internal fun World.isAtOrUnder(entity: Entity, root: Entity): Boolean {
    var current: Entity? = entity
    var steps = 0
    while (current != null && steps++ <= MAX_DEPTH) {
        if (current == root) return true
        current = get<Transform>(current)?.parent
    }
    return false
}

/** Far deeper than any canvas: past it, a parent chain is taken to loop. */
private const val MAX_DEPTH = 1024

private fun SceneNode.canvasElement(): SceneCanvasElement? = components.firstNotNullOfOrNull { it as? SceneCanvasElement }

private fun SceneNode.visit(path: String, action: (SceneNode, String) -> Unit) {
    action(this, path)
    children.forEachIndexed { index, child -> child.visit("$path/${child.name?.takeIf(String::isNotBlank) ?: "#$index"}", action) }
}

/** Whether [id] can be an [SceneCanvasElement.elementId]: letters, digits, `_`, `.` and `-`, not starting with `.` or `-`. */
internal fun isCanvasElementId(id: String): Boolean = ELEMENT_ID.matches(id)

/** What [isCanvasElementId] accepts, as a validation message ends. */
internal const val ELEMENT_ID_RULE = "must be letters, digits, '_', '.' and '-', starting with a letter, digit or '_'"

private val ELEMENT_ID = Regex("[A-Za-z0-9_][A-Za-z0-9_.-]*")
