/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How an Android launch intent becomes [LaunchParameters], over the plain values the intent holds, so
 * it runs without a device. Reading the `Intent` and `Bundle` themselves is a thin layer over this.
 */
internal class AndroidHostTest {
    @Test
    fun stringExtrasAndTheDataQueryGiveTheSameParametersAsOtherPlatforms() {
        val parameters = launchParametersFrom(
            encodedQuery = "tag=x&debug&empty=&token=a=b=c&name=J%C3%B6rg+Lee",
            extras = mapOf("server" to "alpha", "tag" to arrayOf("y")),
        )

        assertEquals(
            mapOf(
                "tag" to listOf("x", "y"),
                "debug" to listOf(""),
                "empty" to listOf(""),
                "token" to listOf("a=b=c"),
                "name" to listOf("Jörg Lee"),
                "server" to listOf("alpha"),
            ),
            parameters.toMap(),
        )
    }

    @Test
    fun theDataQueryComesBeforeTheExtrasSoItsValueIsFirst() {
        val parameters = launchParametersFrom("mode=link", mapOf("mode" to "extra"))

        assertEquals(listOf("link", "extra"), parameters.values("mode"))
    }

    @Test
    fun listAndArrayExtrasGiveOneValuePerTextElement() {
        val parameters = launchParametersFrom(
            null,
            mapOf(
                "tag" to arrayListOf("x", "y"),
                "arr" to arrayOf<CharSequence>("p", "q"),
                "mixed" to listOf("a", 1, null, "b"),
                "none" to emptyList<String>(),
            ),
        )

        assertEquals(listOf("x", "y"), parameters.values("tag"))
        assertEquals(listOf("p", "q"), parameters.values("arr"))
        assertEquals(listOf("a", "b"), parameters.values("mixed"))
        assertFalse(parameters.has("none"))
    }

    @Test
    fun primitiveExtrasAreTheirTextAndANullExtraIsABareFlag() {
        val parameters = launchParametersFrom(
            null,
            mapOf("on" to true, "n" to 42, "x" to 1.5, "long" to 7L, "c" to 'z', "flag" to null),
        )

        assertEquals("true", parameters.first("on"))
        assertEquals("42", parameters.first("n"))
        assertEquals("1.5", parameters.first("x"))
        assertEquals("7", parameters.first("long"))
        assertEquals("z", parameters.first("c"))
        assertTrue(parameters.has("flag"))
        assertEquals("", parameters.first("flag"))
    }

    @Test
    fun extrasThatAreNotTextAreSkipped() {
        val parameters = launchParametersFrom(
            null,
            mapOf("bytes" to byteArrayOf(1, 2), "ints" to intArrayOf(1), "object" to Any(), "ok" to "yes"),
        )

        assertEquals(listOf("ok"), parameters.keys.toList())
    }

    @Test
    fun noQueryAndNoExtrasIsEmpty() {
        assertTrue(launchParametersFrom(null, emptyMap()).isEmpty)
        assertTrue(launchParametersFrom("", emptyMap()).isEmpty)
    }
}
