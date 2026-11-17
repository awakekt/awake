/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlinedQueryChecksTest {
    @Test
    fun aFunctionKeptInAFieldIsASite() {
        val source = """
            class Tracker {
                private val trackNew: (Entity, Effect) -> Unit = { entity, effect -> }
                fun update(world: World) {
                    world.queryEach(Effect::class, trackNew)
                    world.queryEach(
                        Effect::class,
                        Body::class,
                        ::track,
                    )
                    world.family<Effect>().forEach(trackNew)
                }
            }
        """.trimIndent()

        assertEquals(listOf(4, 5, 10), functionObjectQueryLines(source))
    }

    @Test
    fun aLambdaAtTheCallIsNot() {
        val source = """
            fun update(world: World) {
                world.queryEach(Effect::class) { entity, effect -> track(entity, effect) }
                world.queryEach<Effect, Body> { entity, effect, body -> }
                world.queryEach(Effect::class, { entity, effect -> })
                world.family(Effect::class).forEach { entity, effect -> }
                items.forEach(::println)
            }
        """.trimIndent()

        assertEquals(emptyList(), functionObjectQueryLines(source))
    }

    @Test
    fun declarationsCommentsAndStringsAreNotCalls() {
        val source = """
            /** Not `world.queryEach(Effect::class, trackNew)`, which boxes. */
            inline fun <A : Any> queryEach(type: KClass<A>, block: (Entity, A) -> Unit) {
                family(type).forEach(block)
            }
            val hint = "queryEach(Effect::class, trackNew)"
        """.trimIndent()

        // The body forwards the inline function's own parameter, which is inlined in turn.
        assertEquals(emptyList(), functionObjectQueryLines(source))
    }

    @Test
    fun eachSiteIsNamedWithItsFileAndLine() {
        val failures = inlinedQueryFailures(mapOf("awake/scene/a/System.kt" to listOf(4, 9)))

        assertEquals(2, failures.size)
        assertTrue(failures[0].startsWith("awake/scene/a/System.kt:4 "))
        assertTrue(failures[1].startsWith("awake/scene/a/System.kt:9 "))
    }
}
