/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.RegisteredBinding
import com.awakekt.awake.scene.binding.SceneBindingHooks
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.binding.SceneValueConverter
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.ui.canvas.CanvasBinding
import com.awakekt.awake.ui.canvas.CanvasCluster
import com.awakekt.awake.ui.canvas.CanvasLayout
import com.awakekt.awake.ui.canvas.CanvasStepper
import com.awakekt.awake.ui.canvas.CanvasStepperWidget
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

// Most canvas components are bound by the `@SceneBinding` on their saved form, generated into
// CanvasSceneComponents. Three are values saved whole under one field, `layout`, `cluster` and `bind`,
// which a field-by-field copy can't express, so their bindings are written here and listed with
// `@RegisteredBinding`. The converter and hooks below are what the generated ones use.

/** The binding of a canvas component saved whole: [load] takes it out of its saved form, [save] puts it back. */
internal class CanvasComponentBinding<C : Any, S : SceneComponent>(
    override val componentClass: KClass<C>,
    override val schemaClass: KClass<S>,
    override val serializer: KSerializer<S>,
    private val load: (S) -> C,
    private val save: (C) -> S,
) : SceneComponentBinding<C, S> {
    override fun attachTyped(world: World, entity: Entity, component: S, context: SceneResolutionContext) {
        world.add(entity, componentClass, load(component))
    }

    override fun export(world: World, entity: Entity, component: C): S = save(component)
}

/** Loads and saves a [CanvasLayout] as the `canvas_layout` scene component. */
@RegisteredBinding
object CanvasLayoutBinding : SceneComponentBinding<CanvasLayout, SceneCanvasLayout> by CanvasComponentBinding(
    CanvasLayout::class,
    SceneCanvasLayout::class,
    SceneCanvasLayout.serializer(),
    { it.layout },
    { SceneCanvasLayout(it) },
)

/** Loads and saves a [CanvasCluster] as the `canvas_cluster` scene component. */
@RegisteredBinding
object CanvasClusterBinding : SceneComponentBinding<CanvasCluster, SceneCanvasCluster> by CanvasComponentBinding(
    CanvasCluster::class,
    SceneCanvasCluster::class,
    SceneCanvasCluster.serializer(),
    { it.cluster },
    { SceneCanvasCluster(it) },
)

/** Loads and saves a [CanvasBinding] as the `canvas_bind` scene component. */
@RegisteredBinding
object CanvasBindBinding : SceneComponentBinding<CanvasBinding, SceneCanvasBind> by CanvasComponentBinding(
    CanvasBinding::class,
    SceneCanvasBind::class,
    SceneCanvasBind.serializer(),
    { it.bind },
    { SceneCanvasBind(it) },
)

/** Converts a saved [SceneVec3] to a live [Vec3f] and back, as a follower's offset is saved. */
internal object CanvasVec3Converter : SceneValueConverter<SceneVec3, Vec3f> {
    override fun toLive(saved: SceneVec3): Vec3f = Vec3f(saved.x, saved.y, saved.z)

    override fun toSaved(live: Vec3f): SceneVec3 = SceneVec3(live.x, live.y, live.z)
}

/**
 * Saves a stepper's live number as where it starts. Loading needs nothing more: setting a stepper's
 * settings puts its number at their value.
 */
internal object CanvasStepperStart : SceneBindingHooks<CanvasStepperWidget, SceneCanvasStepper> {
    override fun afterCopyToSaved(live: CanvasStepperWidget, saved: SceneCanvasStepper): SceneCanvasStepper {
        val settings = live.settings
        val starts = settings != null || live.number != CanvasStepper.Default.value
        return SceneCanvasStepper(if (starts) (settings ?: CanvasStepper.Default).copy(value = live.number) else null)
    }
}
