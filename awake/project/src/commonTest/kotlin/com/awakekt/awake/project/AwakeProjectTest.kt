/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AwakeProjectTest {
    @Test
    fun projectManifestRoundTripsAndValidates() {
        val manifest = AwakeProjectManifest(
            id = "com.example.game",
            name = "Example Game",
            version = "1.0.0",
            entryScene = "scenes/main.scene.json",
            assetRoots = listOf("assets", "shared-assets"),
            plugins = listOf(
                AwakeProjectPluginReference(
                    id = "com.example.tools",
                    path = "plugins/tools.awakeplugin",
                    version = "2.1.0",
                    required = true,
                ),
            ),
        )

        val restored = AwakeProjectValidator.decodeManifest(
            AwakeProjectValidator.encodeManifest(manifest),
        )

        assertEquals(manifest, restored)
        assertTrue(AwakeProjectValidator.manifestIssues(restored).isEmpty())
    }

    @Test
    fun manifestCompatibilityUsesExplicitMinimumEngineVersion() {
        val manifest = AwakeProjectManifest(
            id = "com.example.demo",
            name = "Demo",
            version = "1.0.0",
            minEngineVersion = "0.1.0-beta.1",
            entryScene = "scenes/main.scene.json",
        )

        assertFalse(AwakeProjectValidator.isCompatible(manifest, "0.1.0-alpha.3"))
        assertTrue(AwakeProjectValidator.isCompatible(manifest, "0.1.0-beta.1"))
        assertTrue(AwakeProjectValidator.isCompatible(manifest, "0.1.0"))
        assertTrue(AwakeProjectValidator.isCompatible(manifest, "1.0.0"))
    }

    @Test
    fun manifestWithoutEngineMinimumDoesNotInventCompatibilityRequirement() {
        val manifest = AwakeProjectManifest(
            id = "com.example.demo",
            name = "Demo",
            version = "1.0.0",
            entryScene = "scenes/main.scene.json",
        )

        assertNull(manifest.minEngineVersion)
        assertTrue(AwakeProjectValidator.isCompatible(manifest, "0.0.1"))
    }

    @Test
    fun buildInfoExposesTheGradleDerivedEngineVersion() {
        assertTrue(AwakeEngineBuildInfo.VERSION.matches(Regex("\\d+\\.\\d+\\.\\d+.*")))
    }

    @Test
    fun validatorsRejectUnsafePathsAndInvalidPins() {
        val manifest = AwakeProjectManifest(
            id = "com.example.game",
            name = "Example Game",
            version = "1.0.0",
            entryScene = "../outside.scene.json",
        )
        val lock = AwakeAssetsLock(
            assets = mapOf(
                "assets/world.zip" to AwakeAssetLockEntry("not-a-digest", -1),
            ),
        )

        assertTrue(AwakeProjectValidator.manifestIssues(manifest).isNotEmpty())
        assertTrue(AwakeProjectValidator.assetsLockIssues(lock).isNotEmpty())
        assertFalse(AwakeProjectValidator.isSafeProjectPath("../outside"))
        assertTrue(AwakeProjectValidator.isSafeProjectPath("assets/world.zip"))
    }

    @Test
    fun pluginReferenceSupportsSha256DigestAndOmittedVersion() {
        val validHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val manifest = AwakeProjectManifest(
            id = "com.example.game",
            name = "Example Game",
            version = "1.0.0",
            entryScene = "scenes/main.scene.json",
            plugins = listOf(
                AwakeProjectPluginReference(
                    id = "com.example.tools",
                    path = "plugins/tools.awakeplugin",
                    sha256 = validHash,
                    required = true,
                ),
            ),
        )

        val json = AwakeProjectValidator.encodeManifest(manifest)
        val decoded = AwakeProjectValidator.decodeManifest(json)

        assertEquals(manifest, decoded)
        assertTrue(AwakeProjectValidator.manifestIssues(decoded).isEmpty())

        val invalidManifest = manifest.copy(
            plugins = listOf(
                AwakeProjectPluginReference(
                    id = "com.example.tools",
                    path = "plugins/tools.awakeplugin",
                    sha256 = "INVALID_HASH",
                ),
            ),
        )
        val issues = AwakeProjectValidator.manifestIssues(invalidManifest)
        assertTrue(issues.any { it.contains("sha256 must be a lowercase SHA-256 digest") })
    }
}
