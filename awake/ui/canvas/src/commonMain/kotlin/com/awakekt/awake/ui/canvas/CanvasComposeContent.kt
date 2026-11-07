/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming") // A composable, named as Compose names them.

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxScope
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.ProvidedLocals
import com.awakekt.awake.compose.ui.Modifier

/**
 * Compose UI a canvas element draws in its box: [CanvasElement.composeContent].
 *
 * The canvas composes it where it draws the element, so it takes the element's place in canvas
 * order, its alpha and its follow position, is laid out in the element's box, and gets the pointer
 * through the canvas's own hit testing: an element drawn over it takes a tap first.
 *
 * @property locals Composition locals to provide over the canvas's own, such as those open where
 * the content was declared; null for none.
 * @property content The UI, laid out in a box the element's size.
 */
class CanvasComposeContent(
    val locals: ProvidedLocals?,
    val content: context(Composer)
    BoxScope.() -> Unit,
)

/** [element]'s [CanvasElement.composeContent], filling this box, when it has any. */
context(_: Composer)
internal fun BoxScope.ComposeContent(element: CanvasElement) {
    val compose = element.composeContent ?: return
    val locals = compose.locals
    val draw = compose.content
    Box(Modifier.fillMaxSize()) {
        if (locals == null) draw() else CompositionLocalProvider(locals) { draw() }
    }
}
