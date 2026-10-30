/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.toColor

/**
 * The logic a generated [SceneBinding] runs around its mapping, for a binding that is mostly copies
 * and a little more, such as a migration from a legacy field. Implement it as an `object` and name it
 * in [SceneBinding.hooks]; every method defaults to doing nothing.
 *
 * The two copy hooks run inside the binding's pure `toComponent()` and `toSceneComponent()`, so they see
 * no world. The other two run only when the binding attaches to or exports from a [World].
 * They also run for [SceneBinding.whole], around unwrapping and wrapping the live value.
 *
 * @param C The live ECS component.
 * @param S The scene schema.
 */
interface SceneBindingHooks<C : Any, S : SceneComponent> {
    /**
     * Completes [live], just built from [saved] by the field copy, and returns the component to use:
     * [live] itself, changed or not, or a replacement.
     */
    fun afterCopyToLive(saved: S, live: C): C = live

    /** Completes [saved], just built from [live] by the field copy, and returns the schema to use. */
    fun afterCopyToSaved(live: C, saved: S): S = saved

    /**
     * Runs once [live] has been added to [entity], for work that needs the world, such as deferring a
     * link to another node through [context].
     */
    fun afterAttach(world: World, entity: Entity, saved: S, live: C, context: SceneResolutionContext) = Unit

    /** Returns the schema to write for [entity]'s [live] component, [saved] by default, or null to write none. */
    fun beforeExport(world: World, entity: Entity, live: C, saved: S): S? = saved
}

/**
 * Converts a saved field of type [S] to its live field of type [L] and back, for a [SceneBinding] whose
 * two sides use different types for the same value. Implement it as an `object` and list it in
 * [SceneBinding.converters]; the generated binding uses it for every field pair of exactly these types,
 * and for their nullable forms. A [SceneBinding.whole] mapping uses the same converter for the
 * whole property's type and the live component's type.
 *
 * @param S The saved type.
 * @param L The live type.
 */
interface SceneValueConverter<S, L> {
    /** The live value [saved] stands for. */
    fun toLive(saved: S): L

    /** The saved form of [live]. */
    fun toSaved(live: L): S
}

/** Converts a saved [SceneColor] to a live [Color] and back, channel for channel, alpha included. */
object SceneColorConverter : SceneValueConverter<SceneColor, Color> {
    override fun toLive(saved: SceneColor): Color = saved.toColor()

    override fun toSaved(live: Color): SceneColor = SceneColor(live.r, live.g, live.b, live.a)
}
