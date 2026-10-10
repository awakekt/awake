/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.document.SceneComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/** A game's own component, as its capability would register it. */
private class Health(var current: Float, var max: Float, var regenerating: Boolean = false)

@Serializable
@SerialName("health")
private data class SceneHealth(val current: Float, val max: Float, val regenerating: Boolean = false, val pool: Pool = Pool()) : SceneComponent

@Serializable
private data class Pool(val reserve: Float = 40f)

private object HealthBinding : SceneComponentBinding<Health, SceneHealth> {
    override val componentClass: KClass<Health> = Health::class
    override val schemaClass: KClass<SceneHealth> = SceneHealth::class
    override val serializer = SceneHealth.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneHealth, context: SceneResolutionContext) {
        world.add(entity, Health(component.current, component.max, component.regenerating))
    }

    override fun export(world: World, entity: Entity, component: Health): SceneHealth =
        SceneHealth(component.current, component.max, component.regenerating)
}

class CanvasBindingTest {
    private val world = World()
    private val health = Health(current = 120f, max = 150f)

    init {
        world.create().also {
            world.add(it, Name("Player"))
            world.add(it, health)
        }
    }

    private fun tree() = CanvasTree(world, false, emptyMap(), null, listOf(HealthBinding))

    private fun bound(bind: CanvasBinding, value: Float = 0.5f, text: String = "unbound") =
        CanvasElement().also {
            it.bind = bind
            it.value = value
            it.text = text
        }

    @Test
    fun aBarFollowsAFieldAsAShareOfAnother() {
        val bar = bound(CanvasBinding("Player", value = "health.current", max = "health.max"))

        val full = tree().valueOf(bar)
        health.current = 30f
        val low = tree().valueOf(bar)

        assertEquals(0.8f, full, 1e-5f)
        assertEquals(0.2f, low, 1e-5f)
    }

    @Test
    fun aMaxMayBeANumberAndAValueAlreadyAShare() {
        assertEquals(0.6f, tree().valueOf(bound(CanvasBinding("Player", value = "health.current", max = "200"))), 1e-5f)
        health.current = 0.25f
        assertEquals(0.25f, tree().valueOf(bound(CanvasBinding("Player", value = "health.current"))), 1e-5f)
    }

    @Test
    fun aTextFillsInEveryFieldItNames() {
        val label = bound(CanvasBinding("Player", text = "HP {health.current}/{health.max}, reserve {health.pool.reserve}, {health.regenerating}"))

        assertEquals("HP 120/150, reserve 40, false", tree().textOf(label))
        health.current = 87.5f
        assertEquals("HP 87.5/150, reserve 40, false", tree().textOf(label), "it follows the field")
    }

    @Test
    fun anUnknownNodeOrFieldShowsTheElementsOwnValue() {
        assertEquals(0.5f, tree().valueOf(bound(CanvasBinding("Nobody", value = "health.current"))))
        assertEquals(0.5f, tree().valueOf(bound(CanvasBinding("Player", value = "health.missing"))))
        assertEquals(0.5f, tree().valueOf(bound(CanvasBinding("Player", value = "mana.current"))))
        assertEquals("HP {mana.current}", tree().textOf(bound(CanvasBinding("Player", text = "HP {mana.current}"))))
    }

    @Test
    fun aBoundBarDrawsItsFieldsShare() {
        world.create().also {
            world.add(
                it,
                CanvasElement().apply {
                    kind = CanvasElementKind.Bar; offsetX = 0f; offsetY = 0f; width = 100f; height = 10f; color = "#FF0000"; value = 1f
                    bind = CanvasBinding("Player", value = "health.current", max = "health.max")
                },
            )
        }
        health.current = 45f

        val pixels = ComposeHost().frame(FrameInput(100, 10)) { SceneCanvas(world, bindings = listOf(HealthBinding)) }
            .primitives.rasterize(100, 10, Color.Black)
        val red = (0 until 100).count { x -> (pixels[(5 * 100 + x) * 4].toInt() and 0xFF) > 200 }

        assertEquals(30, red, "45 of 150 is 30% of the bar")
    }

    @Test
    fun validationRejectsABindingThatReadsNothing() {
        val issues = SceneCanvasElement(bind = CanvasBinding("", value = "health")).validate("nodes[0]").map { it.message }

        assertEquals(
            listOf(
                "canvas_element.bind.node must name a node",
                "canvas_element.bind.value \"health\" must be component.field or a number",
            ),
            issues,
        )
        assertEquals(
            listOf("canvas_element.bind.needs a value or a text to show"),
            SceneCanvasElement(bind = CanvasBinding("Player")).validate("nodes[0]").map { it.message },
        )
    }
}
