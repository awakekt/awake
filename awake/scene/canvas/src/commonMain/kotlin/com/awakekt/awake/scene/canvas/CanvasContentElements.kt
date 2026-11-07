/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.node
import com.awakekt.awake.scene.document.SceneValidationException
import com.awakekt.awake.ui.canvas.CanvasElementKind

// The element functions of declarative canvas content. Each is a thin wrapper over
// CanvasElementScope, the settings the prefab builder exposes: one settings model serves both, and
// nothing here redefines a setting. A leaf's block is its settings; a panel's is its children.

context(_: Composer)
/**
 * Declares a canvas element of [kind], set up by [settings], with the elements [content] declares
 * inside it. What the other element functions are made of.
 *
 * The element is the same entity from one pass to the next while it stays at this place, or under
 * the same `key`, with the same [kind]; a pass writes only the settings that changed.
 *
 * @param kind What the element draws. Another kind is another element.
 * @param id The element's [CanvasElementScope.elementId]; empty for none.
 * @param settings Sets the element up, as a prefab builder's block does. It adds no children:
 * [content] declares them.
 * @param content The elements inside this one, and the Compose UI [compose] draws in it, or null for none.
 * @throws SceneValidationException when [settings] sets something [kind] does not use, or adds a
 * child or another component.
 */
fun element(
    kind: CanvasElementKind,
    id: String = "",
    settings: CanvasElementScope.() -> Unit = {},
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    val parts = canvasPartsOf(kind, id, settings)
    var self: CanvasContentNode? = null
    node(
        kind,
        update = { node ->
            val written = contentNode(node)
            written.applier.write(written, parts)
            self = written
        },
        // Always entered, so content that stops declaring `compose { }` takes it off the element.
        content = { composeInside(checkNotNull(self), content) },
    )
}

context(_: Composer)
/** Declares a Panel, set up by [settings], with the elements [content] declares inside it. */
fun panel(
    id: String = "",
    settings: CanvasElementScope.() -> Unit = {},
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) =
    element(CanvasElementKind.Panel, id, settings, content)

context(_: Composer)
/** Declares a Text, set up by [settings]: its words, or better, its [CanvasElementScope.textKey]. */
fun text(id: String = "", settings: CanvasElementScope.() -> Unit = {}) = element(CanvasElementKind.Text, id, settings)

context(_: Composer)
/** Declares an Image, set up by [settings]: its picture is its [CanvasElementScope.style]'s image. */
fun image(id: String = "", settings: CanvasElementScope.() -> Unit = {}) = element(CanvasElementKind.Image, id, settings)

context(_: Composer)
/** Declares a Bar, a progress bar set up by [settings]: its [CanvasElementScope.value] and [CanvasElementScope.bar]. */
fun bar(id: String = "", settings: CanvasElementScope.() -> Unit = {}) = element(CanvasElementKind.Bar, id, settings)

context(_: Composer)
/** Declares a Button, set up by [settings]: its label, its [CanvasElementScope.action] and its [CanvasElementScope.button]. */
fun button(id: String = "", settings: CanvasElementScope.() -> Unit = {}) = element(CanvasElementKind.Button, id, settings)

/** The canvas components [settings] makes for an element of [kind], checked as a prefab's are. */
private fun canvasPartsOf(kind: CanvasElementKind, id: String, settings: CanvasElementScope.() -> Unit) =
    CanvasElementScope(id.ifEmpty { kind.name }, kind, id).run {
        settings()
        require(!hasStructure) { "${id.ifEmpty { kind.name }}: declare children in the element's content, not in its settings" }
        canvasParts().also { if (problems.isNotEmpty()) throw SceneValidationException(problems.toList()) }
    }

internal fun contentNode(node: Any): CanvasContentNode =
    node as? CanvasContentNode ?: error("canvas elements are declared inside canvasContent { }, not in a Compose layout")
