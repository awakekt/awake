/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.ui.canvas.CanvasBinding
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/** A game's health, written in place by its systems, as a game's own component is. */
internal class HudHealth(var current: Float, var max: Float)

@Serializable
@SerialName("health")
internal data class SceneHudHealth(val current: Float, val max: Float) : SceneComponent

internal object HudHealthBinding : SceneComponentBinding<HudHealth, SceneHudHealth> {
    override val componentClass: KClass<HudHealth> = HudHealth::class
    override val schemaClass: KClass<SceneHudHealth> = SceneHudHealth::class
    override val serializer = SceneHudHealth.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneHudHealth, context: SceneResolutionContext) {
        world.add(entity, HudHealth(component.current, component.max))
    }

    override fun export(world: World, entity: Entity, component: HudHealth) = SceneHudHealth(component.current, component.max)
}

/** An action slot: seconds of cooldown left out of a total, rounds left, and whether it is ready. */
internal class HudSlot(var left: Float, var total: Float, var rounds: Long, var ready: Boolean)

@Serializable
@SerialName("slot")
internal data class SceneHudSlot(val left: Float, val total: Float, val rounds: Long, val ready: Boolean) : SceneComponent

internal object HudSlotBinding : SceneComponentBinding<HudSlot, SceneHudSlot> {
    override val componentClass: KClass<HudSlot> = HudSlot::class
    override val schemaClass: KClass<SceneHudSlot> = SceneHudSlot::class
    override val serializer = SceneHudSlot.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneHudSlot, context: SceneResolutionContext) {
        world.add(entity, HudSlot(component.left, component.total, component.rounds, component.ready))
    }

    override fun export(world: World, entity: Entity, component: HudSlot) =
        SceneHudSlot(component.left, component.total, component.rounds, component.ready)
}

internal val HUD_BINDINGS: List<SceneComponentBinding<*, *>> = listOf(HudHealthBinding, HudSlotBinding)

/**
 * A HUD whose bound elements read game state: per unit, a bar of its health, a text of it, and an
 * action button bound to its slot's cooldown, rounds and readiness.
 */
internal class BoundHud(val world: World = World(), units: Int, unboundPanels: Int = 0) {
    val health = ArrayList<HudHealth>()
    val slots = ArrayList<HudSlot>()

    init {
        val root = node(null, CanvasElement().apply { kind = CanvasElementKind.Panel; width = 600f; height = 400f })
        repeat(units) { unit ->
            val name = "Unit$unit"
            world.create().also {
                world.add(it, Name(name))
                world.add(it, HudHealth(80f, 100f).also(health::add))
                world.add(it, HudSlot(0f, 4f, 6L, true).also(slots::add))
            }
            node(
                root,
                CanvasElement().apply {
                    kind = CanvasElementKind.Bar
                    width = 100f
                    height = 8f
                    bind = CanvasBinding(name, value = "health.current", max = "health.max")
                },
            )
            node(
                root,
                CanvasElement().apply {
                    kind = CanvasElementKind.Text
                    bind = CanvasBinding(name, text = "HP {health.current}/{health.max:f0}")
                },
            )
            node(
                root,
                CanvasElement().apply {
                    kind = CanvasElementKind.Button
                    text = "Dash"
                    bind = CanvasBinding(name, cooldown = "slot.left", cooldownMax = "slot.total", charges = "slot.rounds", enabled = "slot.ready")
                },
            )
        }
        repeat(unboundPanels) { node(root, CanvasElement().apply { kind = CanvasElementKind.Panel; width = 10f; height = 10f }) }
    }

    private fun node(parent: Entity?, element: CanvasElement): Entity =
        world.create().also { world.add(it, Transform(parent = parent)); world.add(it, element) }
}
