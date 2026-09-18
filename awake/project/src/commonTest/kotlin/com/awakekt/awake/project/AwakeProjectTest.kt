/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AwakeProjectTest {
    @Test
    fun preservesTheExistingManifestJsonShape() {
        val manifest = AwakeProjectManifest(name = "Demo", id = "demo")
        assertEquals(manifest, AwakeProjectValidator.decode(AwakeProjectValidator.encode(manifest)))
    }

    @Test
    fun comparesPrereleaseVersionsBeforeStableVersions() {
        val manifest = AwakeProjectManifest(name = "Demo", id = "demo", minEngineVersion = "0.1.0-beta.1")
        assertFalse(AwakeProjectValidator.isCompatible(manifest, "0.1.0-alpha.1"))
        assertTrue(AwakeProjectValidator.isCompatible(manifest, "0.1.0"))
        assertTrue(AwakeProjectValidator.isCompatible(manifest, "1.0.0"))
    }

    @Test
    fun versionOneManifestRoundTripsAndValidates() {
        val manifest = AwakeProjectManifestV1(
            id = "com.example.game",
            name = "Example Game",
            version = "1.0.0",
            entryScene = "scenes/main.scene.json",
            assetRoots = listOf("assets", "shared-assets"),
            plugins = listOf(
                AwakeProjectPluginReferenceV1(
                    id = "com.example.tools",
                    path = "plugins/tools.awakeplugin",
                    version = "2.1.0",
                    required = true,
                ),
            ),
        )

        val restored = AwakeProjectV1Validator.decodeManifest(
            AwakeProjectV1Validator.encodeManifest(manifest),
        )

        assertEquals(manifest, restored)
        assertTrue(AwakeProjectV1Validator.manifestIssues(restored).isEmpty())
    }

    @Test
    fun versionOneValidatorsRejectUnsafePathsAndInvalidPins() {
        val manifest = AwakeProjectManifestV1(
            id = "com.example.game",
            name = "Example Game",
            version = "1.0.0",
            entryScene = "../outside.scene.json",
        )
        val lock = AwakeAssetsLockV1(
            assets = mapOf(
                "assets/world.zip" to AwakeAssetLockEntryV1("not-a-digest", -1),
            ),
        )

        assertTrue(AwakeProjectV1Validator.manifestIssues(manifest).isNotEmpty())
        assertTrue(AwakeProjectV1Validator.assetsLockIssues(lock).isNotEmpty())
        assertFalse(AwakeProjectV1Validator.isSafeProjectPath("../outside"))
        assertTrue(AwakeProjectV1Validator.isSafeProjectPath("assets/world.zip"))
    }
}
