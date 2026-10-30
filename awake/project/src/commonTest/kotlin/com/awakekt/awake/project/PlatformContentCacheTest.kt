/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlin.test.Test
import kotlin.test.assertFailsWith

class PlatformContentCacheTest {
    @Test
    fun aNamespaceIsOneSafeFolderName() {
        listOf("harbor-town", "com.example.harbortown", "a", "Game_2", "1st-light", "x".repeat(MAX_CONTENT_CACHE_NAMESPACE_LENGTH))
            .forEach(::requireContentCacheNamespace)
    }

    @Test
    fun aNamespaceThatIsNotOneSafeFolderNameIsRefusedBeforeAnyStorageIsTouched() {
        val refused = listOf(
            "", // empty
            ".", // this folder
            "..", // the parent
            ".hidden", // hidden, and starts with no letter or digit
            "-flag", // reads as a command-line option
            "a/b", // two segments
            "a\\b", // two segments on Windows
            "/abs", // absolute
            "C:", // a drive
            "harbor town", // a space
            "trailing.", // Windows drops the dot
            "café", // not ASCII
            "CON", // Windows devices, any case, with or without an extension
            "nul.txt",
            "Com1",
            "lpt9.cache",
            "x".repeat(MAX_CONTENT_CACHE_NAMESPACE_LENGTH + 1),
        )
        refused.forEach { namespace ->
            assertFailsWith<IllegalArgumentException>("\"$namespace\" must be refused") { platformContentCache(namespace) }
        }
    }
}
