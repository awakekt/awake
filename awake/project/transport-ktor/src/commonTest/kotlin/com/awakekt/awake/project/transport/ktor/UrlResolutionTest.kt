/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.transport.ktor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A relative content URL resolves against the base as RFC 3986 says, and so as a browser would. */
class UrlResolutionTest {
    @Test
    fun rfc3986NormalExamples() {
        val base = "http://a/b/c/d;p?q"
        mapOf(
            "g:h" to "g:h",
            "g" to "http://a/b/c/g",
            "./g" to "http://a/b/c/g",
            "g/" to "http://a/b/c/g/",
            "/g" to "http://a/g",
            "//g" to "http://g",
            "?y" to "http://a/b/c/d;p?y",
            "g?y" to "http://a/b/c/g?y",
            "#s" to "http://a/b/c/d;p?q#s",
            "g#s" to "http://a/b/c/g#s",
            "g?y#s" to "http://a/b/c/g?y#s",
            ";x" to "http://a/b/c/;x",
            "g;x" to "http://a/b/c/g;x",
            "" to "http://a/b/c/d;p?q",
            "." to "http://a/b/c/",
            "./" to "http://a/b/c/",
            ".." to "http://a/b/",
            "../" to "http://a/b/",
            "../g" to "http://a/b/g",
            "../.." to "http://a/",
            "../../" to "http://a/",
            "../../g" to "http://a/g",
        ).forEach { (reference, expected) -> assertEquals(expected, resolveUrl(base, reference), "'$reference'") }
    }

    @Test
    fun rfc3986AbnormalExamples() {
        val base = "http://a/b/c/d;p?q"
        mapOf(
            "../../../g" to "http://a/g",
            "../../../../g" to "http://a/g",
            "/./g" to "http://a/g",
            "/../g" to "http://a/g",
            "g." to "http://a/b/c/g.",
            ".g" to "http://a/b/c/.g",
            "g.." to "http://a/b/c/g..",
            "..g" to "http://a/b/c/..g",
            "./../g" to "http://a/b/g",
            "./g/." to "http://a/b/c/g/",
            "g/./h" to "http://a/b/c/g/h",
            "g/../h" to "http://a/b/c/h",
            "g;x=1/./y" to "http://a/b/c/g;x=1/y",
            "g;x=1/../y" to "http://a/b/c/y",
            "g?y/./x" to "http://a/b/c/g?y/./x",
            "g#s/../x" to "http://a/b/c/g#s/../x",
        ).forEach { (reference, expected) -> assertEquals(expected, resolveUrl(base, reference), "'$reference'") }
    }

    @Test
    fun aContentUrlResolvesAgainstThePublishedFolder() {
        val base = "https://cdn.example.com/games/harbor-town/release-7/"
        assertEquals("https://cdn.example.com/games/harbor-town/release-7/project.index.json", resolveUrl(base, "project.index.json"))
        assertEquals(
            "https://cdn.example.com/games/harbor-town/release-7/files/assets/crate.glb?access_token=t",
            resolveUrl(base, "files/assets/crate.glb?access_token=t"),
        )
        assertEquals("https://cdn.example.com/games/shared/a.awkb", resolveUrl(base, "../../shared/a.awkb"))
        assertEquals("https://other.example.com/a.glb", resolveUrl(base, "https://other.example.com/a.glb"))
        // A base without a trailing slash names a file, so its last segment is replaced.
        assertEquals("https://cdn.example.com/release/a.glb", resolveUrl("https://cdn.example.com/release/index.json?sig=1", "a.glb"))
    }

    @Test
    fun aSchemeMarksAnAbsoluteUrl() {
        assertTrue(hasScheme("https://a/b"))
        assertTrue(hasScheme("blob:https://a/1"))
        assertFalse(hasScheme("files/a.glb"))
        assertFalse(hasScheme("/a:b"))
        assertFalse(hasScheme("1http://a"))
    }
}
