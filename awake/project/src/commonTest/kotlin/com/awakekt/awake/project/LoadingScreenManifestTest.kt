/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadingScreenManifestTest {
    @Test
    fun manifestWithoutLoadingScreenDecodesAndValidatesCleanly() {
        val legacyJson =
            """{"formatVersion":1,"id":"com.awake.demo","name":"Demo","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        val manifest = AwakeProjectValidator.decodeManifest(legacyJson)
        assertNull(manifest.loadingScreen)
        assertTrue(AwakeProjectValidator.manifestIssues(manifest).isEmpty())
    }

    @Test
    fun loadingScreenRoundTripsAndValidatesCleanly() {
        val manifest = AwakeProjectManifest(
            id = "com.awake.skyward",
            name = "Skyward Isles",
            version = "1.0.0",
            entryScene = "scenes/menu.scene.json",
            loadingScreen = AwakeLoadingScreen(
                title = "Skyward Isles",
                logoPath = "assets/ui/logo.png",
                backgroundColor = "#0B1020",
                accentColor = "#FF9F1C",
                showEngineAttribution = false,
            ),
        )
        assertTrue(AwakeProjectValidator.manifestIssues(manifest).isEmpty())
        val decoded = AwakeProjectValidator.decodeManifest(AwakeProjectValidator.encodeManifest(manifest))
        assertEquals(manifest.loadingScreen, decoded.loadingScreen)
    }

    @Test
    fun invalidLoadingScreenFieldsAreRejected() {
        val base = AwakeProjectManifest(
            id = "com.awake.skyward",
            name = "Skyward Isles",
            version = "1.0.0",
            entryScene = "scenes/menu.scene.json",
        )
        val unsafeLogo = AwakeProjectValidator.manifestIssues(
            base.copy(loadingScreen = AwakeLoadingScreen(logoPath = "../escape.png")),
        )
        assertTrue(unsafeLogo.any { "logoPath" in it }, "expected unsafe logoPath error, got $unsafeLogo")

        val badExt = AwakeProjectValidator.manifestIssues(
            base.copy(loadingScreen = AwakeLoadingScreen(logoPath = "assets/script.js")),
        )
        assertTrue(badExt.any { "logoPath" in it }, "expected non-image logoPath error, got $badExt")

        val badColors = AwakeProjectValidator.manifestIssues(
            base.copy(
                loadingScreen = AwakeLoadingScreen(
                    title = "   ",
                    backgroundColor = "url(javascript:alert(1))",
                    accentColor = "#12345",
                ),
            ),
        )
        assertTrue(badColors.any { "title" in it }, "expected blank title error, got $badColors")
        assertTrue(badColors.any { "backgroundColor" in it }, "expected hex bg error, got $badColors")
        assertTrue(badColors.any { "accentColor" in it }, "expected hex accent error, got $badColors")
    }
}
