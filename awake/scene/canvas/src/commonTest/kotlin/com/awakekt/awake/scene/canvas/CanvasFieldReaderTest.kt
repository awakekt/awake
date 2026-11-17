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
import com.awakekt.awake.scene.document.SceneComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private enum class Stance { Idle, Guarding }

@Serializable
private data class Inner(val depth: Int = 3, val label: String = "deep", val stance: Stance = Stance.Guarding)

@Serializable
@SerialName("every")
private data class EveryKind(
    val count: Int = 7,
    val small: Short = 2,
    val big: Long = 16_777_217L,
    val share: Float = 0.456f,
    val whole: Float = 120f,
    val ratio: Double = 1234.5678,
    val missing: Float = Float.NaN,
    val ready: Boolean = true,
    val letter: Char = 'x',
    val name: String = "Harbor Town",
    val stance: Stance = Stance.Idle,
    val nothing: String? = null,
    val inner: Inner = Inner(),
    val stats: Map<String, Float> = mapOf("speed" to 2.5f, "reach" to 4f),
    val levels: Map<Int, Int> = mapOf(1 to 10, 2 to 20),
    val list: List<Int> = listOf(1, 2),
    val counter: UInt = 4_000_000_000u,
) : SceneComponent

private class Every(var saved: EveryKind)

private object EveryBinding : SceneComponentBinding<Every, EveryKind> {
    override val componentClass: KClass<Every> = Every::class
    override val schemaClass: KClass<EveryKind> = EveryKind::class
    override val serializer = EveryKind.serializer()

    override fun attachTyped(world: World, entity: Entity, component: EveryKind, context: SceneResolutionContext) {
        world.add(entity, Every(component))
    }

    override fun export(world: World, entity: Entity, component: Every): EveryKind = component.saved
}

/**
 * [CanvasFieldReader] gives every field the text the JSON it replaced gave (#1512), so a bound element
 * shows exactly what it did: each kind of value, nested fields, map keys, and what JSON finds nothing at.
 */
class CanvasFieldReaderTest {
    private val world = World()
    private val every = Every(EveryKind())
    private val entity = world.create().also { world.add(it, every) }
    private val reader = CanvasFieldReader(world, listOf(EveryBinding))
    private val json = Json {
        encodeDefaults = true
        allowSpecialFloatingPointValues = true
    }

    private fun throughJson(path: String): String? {
        var element: JsonElement? = json.encodeToJsonElement(EveryKind.serializer(), every.saved)
        for (segment in path.substringAfter('.').split('.')) element = (element as? JsonObject)?.get(segment)
        return (element as? JsonPrimitive)?.content
    }

    @Test
    fun everyFieldReadsAsJsonWouldGiveIt() {
        val paths = listOf(
            "count", "small", "big", "share", "whole", "ratio", "missing", "ready", "letter", "name", "stance", "nothing",
            "inner.depth", "inner.label", "inner.stance", "inner", "stats.speed", "stats.reach", "stats.absent",
            "levels.1", "levels.2", "levels.3", "list", "list.0", "absent", "inner.absent", "counter",
        ).map { "every.$it" }
        for (path in paths) assertEquals(throughJson(path), reader.field(entity, path), path)
    }

    @Test
    fun aChangeIsSeenByValueAndAnEqualWriteIsNot() {
        val fields = listOf(FieldValue(listOf("whole")), FieldValue(listOf("stats", "speed")), FieldValue(listOf("missing")))
        reader.read(entity, "every", fields)
        fields.forEach { it.changed() }
        every.saved = every.saved.copy() // Equal, NaN included.
        reader.read(entity, "every", fields)
        assertFalse(fields.any { it.changed() }, "an equal component is no change")
        every.saved = every.saved.copy(stats = mapOf("speed" to 3f, "reach" to 4f))
        reader.read(entity, "every", fields)
        assertTrue(fields[1].changed(), "a map entry's change is seen")
        assertFalse(fields[0].changed() || fields[2].changed(), "only that one")
    }

    @Test
    fun aComponentTheNodeDoesNotHaveReadsNothing() {
        val bare = world.create()
        assertEquals(null, reader.field(bare, "every.count"))
        assertEquals(null, reader.field(entity, "unknown.count"))
        assertEquals(null, reader.field(entity, "every"))
    }
}
