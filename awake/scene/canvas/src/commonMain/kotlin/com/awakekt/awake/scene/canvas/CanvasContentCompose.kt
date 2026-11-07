/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.layout.BoxScope
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.ui.canvas.CanvasComposeContent
import com.awakekt.awake.ui.canvas.CanvasElement

/** The canvas element whose content is being declared: where [compose] puts its UI. */
private val LocalCanvasContentNode = compositionLocalOf<CanvasContentNode?> { null }

context(composer: Composer)
/**
 * Draws [content], ordinary Compose UI, in the box of the canvas element whose content declares it.
 *
 * ```kotlin
 * canvasContent(world, root = hudRoot, state = partyState) { party ->
 *     panel(id = "party", settings = { size(240.dp, 120.dp) }) {
 *         compose { PartyFrames(party) }
 *     }
 * }
 * ```
 *
 * The canvas composes it where it draws the element: in the element's place in canvas order, under
 * the elements inside it, with its alpha and its follow position, laid out in a box the element's
 * size. The pointer reaches it through the canvas's hit testing, so an element drawn over it takes a
 * tap first. Compose layout applies inside the box only: the element itself is placed by the canvas.
 *
 * What the block reads, its state and the values it remembers, reaches [content] as any Kotlin
 * closure does, and the composition locals open here are provided over the canvas's. [content] is
 * composed with the canvas, keyed to the element, so what it remembers lives as long as the element.
 * An element that stops declaring it stops drawing it.
 *
 * It counts as one of the applier's writes when the lambda is another than the last pass's: none
 * for a lambda kept in a `val`, and one for a lambda that captures the block's state, which runs
 * only when that state changed.
 *
 * @param content The UI, in the element's box.
 * @throws IllegalStateException outside a canvas element's content.
 */
fun compose(content: context(Composer) BoxScope.() -> Unit) {
    val node = LocalCanvasContentNode.current ?: error("compose { } draws in a canvas element: declare it in an element's content")
    node.composes = true
    val element = checkNotNull(node.applier.world.get<CanvasElement>(node.entity)) { "the element was not written" }
    // The locals are taken afresh each pass; only another lambda is another write.
    if (element.composeContent?.content !== content) node.applier.writes++
    element.composeContent = CanvasComposeContent(composer.localsSnapshot, content)
}

context(_: Composer)
/** Runs [content], an element's, with [node] as the element [compose] draws in, and clears what it no longer declares. */
internal fun composeInside(
    node: CanvasContentNode,
    content: (
        context(Composer)
        () -> Unit
    )?,
) {
    node.composes = false
    if (content != null) CompositionLocalProvider(LocalCanvasContentNode, node, content)
    val element = if (node.composes) null else node.applier.world.get<CanvasElement>(node.entity)
    if (element?.composeContent != null) {
        element.composeContent = null
        node.applier.writes++
    }
}
