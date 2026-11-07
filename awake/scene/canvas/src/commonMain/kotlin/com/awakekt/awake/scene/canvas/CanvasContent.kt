/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.Composition
import com.awakekt.awake.compose.runtime.PolledState
import com.awakekt.awake.core.state.Store
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import kotlinx.coroutines.flow.StateFlow

/**
 * Declares canvas content under [root] as a function of [state], and keeps it in step: the
 * elements [content] declares for the state's value become canvas entities under [root], in the
 * order it declares them, and a [CanvasContentSystem] recomposes the block on a frame where the
 * state holds a new value. Nothing recomposes on a frame where it does not.
 *
 * ```kotlin
 * canvasContent(world, root = hudRoot, state = partyState) { party ->
 *     panel(id = "party", settings = { anchor = CanvasAnchor.TopLeft }) {
 *         party.members.forEach { member -> key(member.id) { unitFrame(member) } }
 *         if (party.inCombat) text(id = "combat") { textKey = "hud.combat" }
 *     }
 * }
 * ```
 *
 * A pass that declares what the last one did writes nothing, and a keyed item that moves keeps its
 * entity. `key`, `remember` and composition locals work as they do in any composition. Canvas
 * layout, follow, binding and drawing are the canvas's own: this only makes, arranges and sets the
 * entities they read.
 *
 * @param S The state's type.
 * @param world The world the elements are made in.
 * @param root The entity they go under, such as a HUD root; disposing the content leaves it.
 * @param state The state the block is a function of.
 * @param content The content for one value of [state], made of [panel], [text], [image], [bar],
 * [button] and [element] calls and functions made of them.
 * @return The content, built for the state's current value; dispose it when it is no longer shown.
 */
fun <S> canvasContent(world: World, root: Entity, state: StateFlow<S>, content: context(Composer) (S) -> Unit): CanvasContent {
    val source = CanvasStateSource(state)
    return canvasContent(world, root) { content(source.value) }
}

/** [canvasContent] for a [Store]: the block is a function of its [Store.state]. */
fun <S> canvasContent(world: World, root: Entity, store: Store<S>, content: context(Composer) (S) -> Unit): CanvasContent =
    canvasContent(world, root, store.state, content)

/**
 * Declares canvas content under [root] that recomposes when the state its last pass read changes.
 * Builds it now.
 *
 * Reads are tracked: a [CanvasContentSystem] recomposes the block on a frame after a
 * `mutableStateOf` it read was written with a different value, or a `StateFlow` it read through
 * `collectAsState()` holds a new one. State it reads any other way, such as a plain `var`, is not
 * seen: its host calls [CanvasContent.recompose] when that changes.
 *
 * ```kotlin
 * val selected = mutableStateOf("ana")
 * canvasContent(world, root = hudRoot) {
 *     text(id = "target") { text = selected.value }
 * }
 * selected.value = "bo" // the next frame recomposes it
 * ```
 *
 * @param world The world the elements are made in.
 * @param root The entity they go under; disposing the content leaves it.
 * @param content The content, reading tracked state or whatever state its host keeps.
 * @return The content, to dispose when it is no longer shown.
 */
fun canvasContent(world: World, root: Entity, content: context(Composer) () -> Unit): CanvasContent =
    CanvasContent(world, root, content).also { it.recompose() }

/**
 * A block of declarative canvas content, made by [canvasContent]: its entities, and the
 * composition that keeps them in step with what the block declares.
 */
class CanvasContent internal constructor(
    private val world: World,
    root: Entity,
    private val content: context(Composer)
    () -> Unit,
) {
    /** The applier building the content, which counts what each pass writes, makes and destroys. */
    val applier: CanvasEntityApplier = CanvasEntityApplier(world, root)

    private val composition = Composition(applier, applier.root)

    init {
        world.canvasContents(root).add(this)
    }

    /**
     * Runs the block again: makes what it newly declares, moves what it moved, destroys what it
     * stopped declaring and writes the settings that changed. Content runs on its own when tracked
     * state it read changes; call this for a change it cannot see. Calling it with nothing changed
     * writes nothing.
     */
    fun recompose() {
        composition.reconcile(content)
        applier.finishPass()
    }

    /** Recomposes when tracked state the last pass read has changed; returns whether it did. */
    internal fun refresh(): Boolean {
        val changed = composition.isInvalid
        if (changed) recompose()
        return changed
    }

    /** Destroys every entity the content made, leaving the root, and forgets what it remembered. */
    fun dispose() {
        world.get<CanvasContents>(applier.root.entity)?.remove(this)
        composition.dispose()
    }
}

/** A [StateFlow] read as tracked state: a pass that read it is invalid once it holds a new value. */
internal class CanvasStateSource<S>(private val state: StateFlow<S>) : PolledState<S>() {
    override fun read(): S = state.value
}
