/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How a page URL becomes a [LaunchOrigin], matching what a browser's `location` reports. */
class LaunchOriginTest {
    private fun origin(url: String): LaunchOrigin = assertNotNull(LaunchOrigin.fromUrl(url), "no origin for '$url'")

    @Test
    fun theSchemeHostAndPortAreReadAndThePathQueryAndFragmentAreNot() {
        val origin = origin("https://game.example:8443/play/index.html?server=a#lobby")

        assertEquals("https", origin.scheme)
        assertEquals("game.example", origin.host)
        assertEquals(8443, origin.port)
        assertEquals(8443, origin.effectivePort)
        assertTrue(origin.isSecure)
        assertEquals("https://game.example:8443", origin.toString())
    }

    @Test
    fun theDefaultPortIsNoPortLikeLocationPort() {
        assertNull(origin("https://game.example:443/").port)
        assertNull(origin("http://game.example:80").port)
        assertNull(origin("http://game.example:/").port)
        assertEquals(origin("https://game.example"), origin("https://game.example:443"))
        assertEquals(443, origin("https://game.example").effectivePort)
        assertEquals(80, origin("http://game.example").effectivePort)
        assertEquals(443, origin("http://game.example:443").port)
    }

    @Test
    fun schemeAndHostAreLowerCased() {
        val origin = origin("HTTP://Game.EXAMPLE:8080")

        assertEquals("http", origin.scheme)
        assertEquals("game.example", origin.host)
        assertFalse(origin.isSecure)
        assertEquals("http://game.example:8080", origin.toString())
    }

    @Test
    fun theAuthorityEndsAtTheFirstPathQueryOrFragment() {
        assertEquals("localhost", origin("http://localhost?x=1").host)
        assertEquals("localhost", origin("http://localhost#a:1").host)
        assertEquals("localhost", origin("http://localhost/a@b:9").host)
        assertNull(origin("http://localhost/a@b:9").port)
    }

    @Test
    fun userInfoIsDropped() {
        val origin = origin("https://player:secret@game.example:9000/")

        assertEquals("game.example", origin.host)
        assertEquals(9000, origin.port)
        assertFalse("secret" in origin.toString())
    }

    @Test
    fun anIpv6HostKeepsItsBrackets() {
        assertEquals("https://[::1]:8443", origin("https://[::1]:8443/").toString())
        assertEquals("[2001:db8::1]", origin("http://[2001:DB8::1]").host)
        assertNull(origin("http://[::1]").port)
    }

    @Test
    fun anIpv4HostIsAHost() {
        assertEquals("http://127.0.0.1:8080", origin("http://127.0.0.1:8080/index.html").toString())
    }

    @Test
    fun opaqueOriginsAndOtherSchemesHaveNoOrigin() {
        val none = listOf(
            "",
            "game.example",
            "//game.example",
            "file:///home/player/game/index.html",
            "data:text/html,hi",
            "blob:https://game.example/1234",
            "about:blank",
            "ws://game.example",
            "awake://launch?server=a",
            "https:game.example",
            "https://",
            "https:///path",
            "https://:8080",
            "https://player@",
        )

        for (url in none) assertNull(LaunchOrigin.fromUrl(url), "should have no origin: '$url'")
    }

    @Test
    fun aMalformedPortOrAuthorityHasNoOrigin() {
        val none = listOf(
            "https://game.example:65536",
            "https://game.example:99999999",
            "https://game.example:-1",
            "https://game.example:80a",
            "https://game.example:+80",
            "https://game.example:1:2",
            "https://[::1",
            "https://[::1]x",
            "https://game example",
            "https://game.example\t:80",
        )

        for (url in none) assertNull(LaunchOrigin.fromUrl(url), "should have no origin: '$url'")
        assertEquals(65535, origin("https://game.example:65535").port)
        assertEquals(8080, origin("https://game.example:08080").port)
    }

    @Test
    fun originsAreValues() {
        assertEquals(origin("https://a.example:1"), origin("HTTPS://A.example:1/x"))
        assertEquals(origin("https://a.example:1").hashCode(), origin("HTTPS://A.example:1/x").hashCode())
        assertFalse(origin("https://a.example") == origin("http://a.example"))
    }
}
