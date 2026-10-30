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

/** Which URLs a host is asked to open, and how the reload capability is offered. */
class HostActionsTest {
    @Test
    fun webAndMailLinksAreOpenable() {
        assertTrue(isOpenableUrl("https://example.test"))
        assertTrue(isOpenableUrl("http://example.test:8080/a/b?c=d#e"))
        assertTrue(isOpenableUrl("HTTPS://EXAMPLE.TEST/"))
        assertTrue(isOpenableUrl("mailto:team@example.test"))
    }

    @Test
    fun everythingElseIsRefused() {
        val refused = listOf(
            "",
            "example.test",
            "//example.test",
            ":https://example.test",
            "https:",
            "https:example.test",
            "https://",
            "https:///path",
            "https://?q=1",
            "mailto:",
            "javascript:alert(1)",
            "file:///etc/passwd",
            "intent://scan/#Intent;scheme=zxing;end",
            "market://details?id=com.example",
            "data:text/html;base64,AAAA",
            "ftp://example.test",
            "https://example.test/a b",
            "https://example.test/\n",
            "https://example.test/\u0000",
            " https://example.test",
        )

        for (url in refused) assertFalse(isOpenableUrl(url), "should refuse: '$url'")
    }

    @Test
    fun aRefusedUrlNeverReachesThePlatformOpener() {
        val opened = mutableListOf<String>()
        val actions = CheckedHostActions(open = { opened.add(it) })

        assertFalse(actions.openUrl("javascript:alert(1)"))
        assertTrue(actions.openUrl("https://example.test"))

        assertEquals(listOf("https://example.test"), opened)
    }

    @Test
    fun openUrlReportsWhatThePlatformOpenerSays() {
        assertFalse(CheckedHostActions(open = { false }).openUrl("https://example.test"))
    }

    @Test
    fun reloadIsACapabilityThatIsAbsentUnlessGiven() {
        val without: HostActions = CheckedHostActions(open = { true })
        assertNull(without.pageReload)

        var reloads = 0
        val with: HostActions = CheckedHostActions(open = { true }, pageReload = PageReload { reloads++ })
        assertNotNull(with.pageReload).reload()
        assertEquals(1, reloads)
    }
}
