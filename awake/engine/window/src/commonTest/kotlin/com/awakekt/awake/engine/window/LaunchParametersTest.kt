/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The launch-parameter rules, once, for every source. The conformance cases state one set of
 * parameters in each source's own encoding; every platform reads its source through one of these
 * factories, so the same inputs give the same result on web, desktop, Android and iOS.
 */
class LaunchParametersTest {
    /** What each conformance case spells: a repeated key, a bare flag, an empty value, `=` in a value, non-ASCII text. */
    private val expected = mapOf(
        "server" to listOf("alpha"),
        "tag" to listOf("x", "y"),
        "debug" to listOf(""),
        "empty" to listOf(""),
        "token" to listOf("a=b=c"),
        "name" to listOf("Jörg Lee"),
    )

    @Test
    fun aQueryKeepsRepeatedKeysFlagsEmptyValuesEqualsAndPercentEscapes() {
        val query = "?server=alpha&tag=x&tag=y&debug&empty=&token=a=b=c&name=J%C3%B6rg+Lee"

        assertEquals(expected, LaunchParameters.fromQuery(query).toMap())
    }

    @Test
    fun commandLineArgumentsKeepRepeatedKeysFlagsEmptyValuesAndEquals() {
        val arguments = listOf(
            "--server=alpha",
            "--tag=x",
            "--tag=y",
            "--debug",
            "--empty=",
            "--token=a=b=c",
            "--name=Jörg Lee",
        )

        assertEquals(expected, LaunchParameters.fromArguments(arguments).toMap())
    }

    @Test
    fun aLaunchUrlReadsItsQueryAndIgnoresTheRest() {
        val url = "myapp://open/level?server=alpha&tag=x&tag=y&debug&empty=&token=a=b=c&name=J%C3%B6rg+Lee#section?no=1"

        assertEquals(expected, LaunchParameters.fromUrl(url).toMap())
    }

    @Test
    fun everySourceGivesTheSameParameters() {
        val fromQuery = LaunchParameters.fromQuery("tag=x&tag=y&debug&token=a%3Db")
        val fromArguments = LaunchParameters.fromArguments(listOf("--tag=x", "--tag=y", "--debug", "--token=a=b"))
        val fromUrl = LaunchParameters.fromUrl("https://example.test/play?tag=x&tag=y&debug&token=a%3Db")
        val fromEntries = LaunchParameters.fromEntries(listOf("tag" to "x", "tag" to "y", "debug" to "", "token" to "a=b"))

        assertEquals(fromQuery, fromArguments)
        assertEquals(fromQuery, fromUrl)
        assertEquals(fromQuery, fromEntries)
        assertEquals(fromQuery.hashCode(), fromArguments.hashCode())
    }

    @Test
    fun valuesKeepTheOrderTheyWereGivenAndKeysKeepTheirFirstPosition() {
        val parameters = LaunchParameters.fromQuery("b=1&a=1&b=2&a=2&b=3")

        assertEquals(listOf("b", "a"), parameters.keys.toList())
        assertEquals(listOf("1", "2", "3"), parameters.values("b"))
        assertEquals("1", parameters.first("b"))
    }

    @Test
    fun aBareFlagIsPresentWithAnEmptyValueAndAbsentKeysAreNot() {
        val parameters = LaunchParameters.fromQuery("debug")

        assertTrue(parameters.has("debug"))
        assertEquals("", parameters.first("debug"))
        assertEquals(listOf(""), parameters.values("debug"))
        assertFalse(parameters.has("missing"))
        assertNull(parameters.first("missing"))
        assertEquals(emptyList(), parameters.values("missing"))
    }

    @Test
    fun aBareFlagAndAnEmptyValueAreTheSameBecauseABrowserCannotTellThemApart() {
        assertEquals(LaunchParameters.fromQuery("debug"), LaunchParameters.fromQuery("debug="))
        assertEquals(LaunchParameters.fromArguments(listOf("--debug")), LaunchParameters.fromArguments(listOf("--debug=")))
    }

    @Test
    fun anEmptyQueryAndAUrlWithoutOneHaveNoParameters() {
        assertTrue(LaunchParameters.fromQuery("").isEmpty)
        assertTrue(LaunchParameters.fromQuery("?").isEmpty)
        assertTrue(LaunchParameters.fromQuery("&&").isEmpty)
        assertTrue(LaunchParameters.fromUrl("https://example.test/play").isEmpty)
        assertTrue(LaunchParameters.fromUrl("https://example.test/play#a=b").isEmpty)
        assertEquals(LaunchParameters.Empty, LaunchParameters.fromArguments(emptyList()))
    }

    @Test
    fun queryDecodingMatchesFormUrlDecoding() {
        val parameters = LaunchParameters.fromQuery(
            "a%20b=c+d&%E2%82%AC=%f0%9f%8e%AE&pct=100%&bad=%zz&short=%4&plus=%2B&amp=a%26b&lone=%FF",
        )

        assertEquals("c d", parameters.first("a b"))
        assertEquals("\uD83C\uDFAE", parameters.first("€"))
        assertEquals("100%", parameters.first("pct"))
        assertEquals("%zz", parameters.first("bad"))
        assertEquals("%4", parameters.first("short"))
        assertEquals("+", parameters.first("plus"))
        assertEquals("a&b", parameters.first("amp"))
        assertEquals("\uFFFD", parameters.first("lone"))
    }

    @Test
    fun semicolonsAndHashesInAQueryAreOrdinaryText() {
        val parameters = LaunchParameters.fromQuery("a=1;b=2&c=#d")

        assertEquals(listOf("a", "c"), parameters.keys.toList())
        assertEquals("1;b=2", parameters.first("a"))
        assertEquals("#d", parameters.first("c"))
    }

    @Test
    fun anEmptyKeyIsDroppedInEverySource() {
        assertTrue(LaunchParameters.fromQuery("=x&%=y").has("%"))
        assertFalse(LaunchParameters.fromQuery("=x").has(""))
        assertTrue(LaunchParameters.fromQuery("=x").isEmpty)
        assertTrue(LaunchParameters.fromArguments(listOf("--=x", "--")).isEmpty)
        assertTrue(LaunchParameters.fromEntries(listOf("" to "x")).isEmpty)
    }

    @Test
    fun keysAreCaseSensitive() {
        val parameters = LaunchParameters.fromQuery("Mode=a&mode=b")

        assertEquals(listOf("a"), parameters.values("Mode"))
        assertEquals(listOf("b"), parameters.values("mode"))
    }

    @Test
    fun aFlagFollowedByAWordDoesNotTakeItAsItsValue() {
        val parameters = LaunchParameters.fromArguments(listOf("--server", "alpha", "--debug"))

        assertEquals(listOf("server", "debug"), parameters.keys.toList())
        assertEquals("", parameters.first("server"))
    }

    @Test
    fun argumentsWithoutTwoDashesAreIgnoredAndADoubleDashEndsTheOptions() {
        val parameters = LaunchParameters.fromArguments(
            listOf("level.dat", "-v", "-key=value", "---odd", "--a=1", "--", "--b=2"),
        )

        assertEquals(listOf("-odd", "a"), parameters.keys.toList())
        assertEquals("1", parameters.first("a"))
        assertFalse(parameters.has("b"))
    }

    @Test
    fun argumentValuesAreNotPercentDecoded() {
        val parameters = LaunchParameters.fromArguments(listOf("--path=a%20b+c"))

        assertEquals("a%20b+c", parameters.first("path"))
    }

    @Test
    fun toMapIsACopyAndToStringNamesKeysButNeverValues() {
        val parameters = LaunchParameters.fromQuery("token=secret-value&mode=x")

        val copy = parameters.toMap()

        assertEquals(mapOf("token" to listOf("secret-value"), "mode" to listOf("x")), copy)
        assertEquals("LaunchParameters(keys=[token, mode])", parameters.toString())
        assertFalse(parameters.toString().contains("secret-value"))
    }

    @Test
    fun emptyIsTheNoParametersValue() {
        assertTrue(LaunchParameters.Empty.isEmpty)
        assertEquals(emptySet(), LaunchParameters.Empty.keys)
        assertEquals(LaunchParameters.Empty, LaunchParameters.fromEntries(emptyList()))
    }
}
