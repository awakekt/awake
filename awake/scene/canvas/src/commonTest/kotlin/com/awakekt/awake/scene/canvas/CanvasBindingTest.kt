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
import com.awakekt.awake.ui.canvas.CanvasBinding
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasResolver
import com.awakekt.awake.ui.canvas.numberField
import com.awakekt.awake.ui.canvas.textField
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

/** A game's whole-number state: a currency that outgrows a Float, a share, a measure and a name. */
private class Purse(var gold: Long, var share: Float, var ratio: Double = 1234.5678, var owner: String = "Harbor Town Guild")

@Serializable
@SerialName("purse")
private data class ScenePurse(val gold: Long, val share: Float, val ratio: Double, val owner: String) : SceneComponent

private object PurseBinding : SceneComponentBinding<Purse, ScenePurse> {
    override val componentClass: KClass<Purse> = Purse::class
    override val schemaClass: KClass<ScenePurse> = ScenePurse::class
    override val serializer = ScenePurse.serializer()

    override fun attachTyped(world: World, entity: Entity, component: ScenePurse, context: SceneResolutionContext) {
        world.add(entity, Purse(component.gold, component.share, component.ratio, component.owner))
    }

    override fun export(world: World, entity: Entity, component: Purse): ScenePurse =
        ScenePurse(component.gold, component.share, component.ratio, component.owner)
}

/** What a [CanvasOverlay][com.awakekt.awake.ui.canvas.CanvasOverlay] resolves a bound element's value and text to. */
private class Binder(private val resolver: CanvasResolver) {
    fun valueOf(element: CanvasElement): Float {
        val bind = element.bind?.takeIf { it.value.isNotBlank() } ?: return element.value
        val value = resolver.numberField(bind.node, bind.value)
        val max = if (bind.max.isBlank()) 1f else resolver.numberField(bind.node, bind.max)
        return if (value == null || max == null || max <= 0f) element.value else (value / max).coerceIn(0f, 1f)
    }

    fun textOf(element: CanvasElement): String {
        val bind = element.bind?.takeIf { it.text.isNotBlank() } ?: return element.text
        return resolver.textField(bind.node, bind.text)
    }
}

class CanvasBindingTest {
    private val world = World()
    private val health = Health(current = 120f, max = 150f)
    private val purse = Purse(gold = 16_777_217L, share = 0.456f)

    init {
        world.create().also {
            world.add(it, Name("Player"))
            world.add(it, health)
            world.add(it, purse)
        }
    }

    /** The same resolving a bound [CanvasElement] does, without composing a [CanvasOverlay][com.awakekt.awake.ui.canvas.CanvasOverlay]. */
    private fun binder() = Binder(SceneCanvasResolver(world, null, null, listOf(HealthBinding, PurseBinding)))

    private fun textOf(template: String) = binder().textOf(bound(CanvasBinding("Player", text = template)))

    private fun bound(bind: CanvasBinding, value: Float = 0.5f, text: String = "unbound") =
        CanvasElement().also {
            it.bind = bind
            it.value = value
            it.text = text
        }

    @Test
    fun aBarFollowsAFieldAsAShareOfAnother() {
        val bar = bound(CanvasBinding("Player", value = "health.current", max = "health.max"))

        val full = binder().valueOf(bar)
        health.current = 30f
        val low = binder().valueOf(bar)

        assertEquals(0.8f, full, 1e-5f)
        assertEquals(0.2f, low, 1e-5f)
    }

    @Test
    fun aMaxMayBeANumberAndAValueAlreadyAShare() {
        assertEquals(0.6f, binder().valueOf(bound(CanvasBinding("Player", value = "health.current", max = "200"))), 1e-5f)
        health.current = 0.25f
        assertEquals(0.25f, binder().valueOf(bound(CanvasBinding("Player", value = "health.current"))), 1e-5f)
    }

    @Test
    fun aTextFillsInEveryFieldItNames() {
        val label = bound(CanvasBinding("Player", text = "HP {health.current}/{health.max}, reserve {health.pool.reserve}, {health.regenerating}"))

        assertEquals("HP 120/150, reserve 40, false", binder().textOf(label))
        health.current = 87.5f
        assertEquals("HP 87.5/150, reserve 40, false", binder().textOf(label), "it follows the field")
    }

    @Test
    fun aWholeNumberIsShownExactlyAboveWhatAFloatHolds() {
        // 16,777,217 is the first whole number a Float cannot hold: it reads back as 16,777,216.
        assertEquals("16777217", textOf("{purse.gold}"))
        purse.gold = Long.MAX_VALUE
        assertEquals("9223372036854775807", textOf("{purse.gold}"))
        purse.gold = -4_000_000_001L
        assertEquals("-4000000001", textOf("{purse.gold}"))
        purse.gold = 0L
        assertEquals("0", textOf("{purse.gold}"))
    }

    @Test
    fun aNumberThatIsNotWholeStillShowsToOneDecimalPlace() {
        // The same text as before the format existed: the default is not what changed.
        assertEquals("HP 120/150, 0.5, 1234.6", textOf("HP {health.current}/{health.max}, {purse.share}, {purse.ratio}"))
    }

    @Test
    fun aFormatGroupsDigitsAndSetsDecimals() {
        assertEquals("16,777,217", textOf("{purse.gold:n0}"))
        assertEquals("16,777,217.00", textOf("{purse.gold:n2}"))
        assertEquals("16777217", textOf("{purse.gold:f0}"))
        assertEquals("1,234.57", textOf("{purse.ratio:n2}"))
        assertEquals("1,235", textOf("{purse.ratio:n0}"))
        assertEquals("46%", textOf("{purse.share:p0}"))
        assertEquals("45.6%", textOf("{purse.share:p1}"))
        assertEquals("150", textOf("{health.max:n0}"), "a Float field that holds a whole number")
        health.current = 87.5f
        assertEquals("88 of 150.0", textOf("{health.current:f0} of {health.max:f1}"), "each placeholder has its own format")
    }

    @Test
    fun aFormatKeepsAWholeNumberExactToTheLastDigit() {
        purse.gold = Long.MAX_VALUE
        assertEquals("9,223,372,036,854,775,807", textOf("{purse.gold:n0}"))
        purse.gold = Long.MIN_VALUE
        assertEquals("-9,223,372,036,854,775,808", textOf("{purse.gold:n0}"))
        purse.gold = 123_456_789_012L
        assertEquals("123456789012.0", textOf("{purse.gold:f1}"))
    }

    @Test
    fun aFormatLeavesAFieldThatIsNotANumberAsItIs() {
        assertEquals("Harbor Town Guild, false", textOf("{purse.owner:n0}, {health.regenerating:p1}"))
        health.current = Float.NaN
        assertEquals("NaN", textOf("{health.current:n0}"))
        health.current = Float.NEGATIVE_INFINITY
        assertEquals("-Infinity", textOf("{health.current:f2}"))
    }

    @Test
    fun anUnknownFormatKeepsItsPlaceholderAndTheRestOfTheTextFollowsTheFields() {
        assertEquals("{purse.gold:zz} 16,777,217 {purse.gold:n}", textOf("{purse.gold:zz} {purse.gold:n0} {purse.gold:n}"))
        assertEquals("{purse.gold:int}", textOf("{purse.gold:int}"))
        assertEquals("{purse.gold:#,##0}", textOf("{purse.gold:#,##0}"))
    }

    @Test
    fun aFormatOnAFieldThatIsMissingStillShowsThePlaceholder() {
        assertEquals("{mana.current:n0} 120", textOf("{mana.current:n0} {health.current:n0}"))
    }

    @Test
    fun anUnknownNodeOrFieldShowsTheElementsOwnValue() {
        assertEquals(0.5f, binder().valueOf(bound(CanvasBinding("Nobody", value = "health.current"))))
        assertEquals(0.5f, binder().valueOf(bound(CanvasBinding("Player", value = "health.missing"))))
        assertEquals(0.5f, binder().valueOf(bound(CanvasBinding("Player", value = "mana.current"))))
        assertEquals("HP {mana.current}", binder().textOf(bound(CanvasBinding("Player", text = "HP {mana.current}"))))
    }

    @Test
    fun aFieldThatIsNotANumberShowsTheElementsOwnValueWithoutFailing() {
        health.current = Float.NaN
        val bar = bound(CanvasBinding("Player", value = "health.current", max = "health.max"))
        val label = bound(CanvasBinding("Player", text = "HP {health.current}"))

        assertEquals(0.5f, binder().valueOf(bar))
        assertEquals("HP NaN", binder().textOf(label))
        health.current = Float.NEGATIVE_INFINITY
        assertEquals("HP -Infinity", binder().textOf(label))
    }

    @Test
    fun aMaxThatIsNotAboveZeroShowsTheElementsOwnValue() {
        health.max = -10f
        assertEquals(0.5f, binder().valueOf(bound(CanvasBinding("Player", value = "health.current", max = "health.max"))))
        assertEquals(0.5f, binder().valueOf(bound(CanvasBinding("Player", value = "health.current", max = "0"))))
    }

    @Test
    fun aBoundBarDrawsItsFieldsShare() {
        world.create().also {
            world.add(
                it,
                CanvasElement().apply {
                    kind = CanvasElementKind.Bar
                    offsetX = 0f
                    offsetY = 0f
                    width = 100f
                    height = 10f
                    color = "#FF0000"
                    value = 1f
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
    fun validationRejectsAFormatThatIsNotOne() {
        val issues = SceneCanvasElement(bind = CanvasBinding("Player", text = "{purse.gold:zz} {purse.gold:n0} {health.current:p10}")).validate("n").map { it.message }

        assertEquals(
            listOf(
                "canvas_element.bind.text format \"zz\" in {purse.gold:zz} must be n, f or p and a digit, such as n0",
                "canvas_element.bind.text format \"p10\" in {health.current:p10} must be n, f or p and a digit, such as n0",
            ),
            issues,
        )
        val valid = CanvasBinding("Player", text = "{purse.gold:n0} {purse.ratio:F2} {purse.share:p1} {health.current}")
        assertEquals(emptyList(), SceneCanvasElement(bind = valid).validate("n").map { it.message })
    }

    @Test
    fun validationRejectsABindingThatReadsNothing() {
        val issues = SceneCanvasElement(bind = CanvasBinding("", value = "health", max = ".max")).validate("nodes[0]").map { it.message }

        assertEquals(
            listOf(
                "canvas_element.bind.node must name a node",
                "canvas_element.bind.value \"health\" must be component.field or a number",
                "canvas_element.bind.max \".max\" must be component.field or a number",
            ),
            issues,
        )
        assertEquals(emptyList(), SceneCanvasElement(bind = CanvasBinding("Player", value = "health.pool.reserve", max = "150")).validate("n").map { it.message })
        assertEquals(
            listOf("canvas_element.bind.needs a value or a text to show"),
            SceneCanvasElement(bind = CanvasBinding("Player")).validate("nodes[0]").map { it.message },
        )
    }
}
