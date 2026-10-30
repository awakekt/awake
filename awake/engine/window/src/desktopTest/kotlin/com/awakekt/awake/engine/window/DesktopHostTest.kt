/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import java.io.IOException
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The desktop adapters: `main` arguments in, and the browser out, without a display. */
class DesktopHostTest {
    @Test
    fun mainArgumentsAreReadAsLaunchParameters() {
        val parameters = desktopLaunchParameters(
            arrayOf("--server=alpha", "--tag=x", "--tag=y", "--debug", "--token=a=b", "positional"),
        )

        assertEquals("alpha", parameters.first("server"))
        assertEquals(listOf("x", "y"), parameters.values("tag"))
        assertTrue(parameters.has("debug"))
        assertEquals("a=b", parameters.first("token"))
        assertFalse(parameters.has("positional"))
    }

    @Test
    fun noArgumentsMeansNoParameters() {
        assertTrue(desktopLaunchParameters(emptyArray()).isEmpty)
    }

    @Test
    fun aDesktopOffersNoPageReload() {
        assertNull(desktopHostActions().pageReload)
    }

    @Test
    fun withoutABrowserOpenUrlIsFalseNotAnException() {
        assertFalse(browseWith(null, "https://example.test"))
    }

    @Test
    fun aBrowserThatFailsIsFalse() {
        for (failure in listOf<Throwable>(
            IOException("no browser"),
            SecurityException("denied"),
            UnsupportedOperationException("no browse"),
            IllegalArgumentException("bad uri"),
        )) {
            assertFalse(browseWith(UrlBrowser { throw failure }, "https://example.test"), failure.message)
        }
    }

    @Test
    fun aMalformedUriIsFalseAndNeverReachesTheBrowser() {
        var browsed: URI? = null

        assertFalse(browseWith(UrlBrowser { browsed = it }, "https://exa mple.test"))
        assertNull(browsed)
    }

    @Test
    fun aWorkingBrowserIsHandedTheUri() {
        var browsed: URI? = null

        assertTrue(browseWith(UrlBrowser { browsed = it }, "https://example.test/a?b=c"))
        assertEquals(URI("https://example.test/a?b=c"), browsed)
    }

    @Test
    fun aRefusedUrlIsFalseWhateverTheMachineHas() {
        assertFalse(desktopHostActions().openUrl("file:///etc/passwd"))
    }

    @Test
    fun aDesktopHasNoOriginAndIsPointerFirst() {
        assertEquals(LaunchEnvironment(origin = null, primaryInput = PrimaryInput.Pointer), desktopLaunchEnvironment())
    }
}
