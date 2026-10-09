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

    /** A two-digit pre-release sorted as text put alpha.10 before alpha.4 and refused every project. */
    @Test
    fun engineVersionsOrderAsTheyAreCut() {
        val ordered = listOf(
            "0.1.0-alpha.4-SNAPSHOT",
            "0.1.0-alpha.4",
            "0.1.0-alpha.9",
            "0.1.0-alpha.10-SNAPSHOT",
            "0.1.0-alpha.10",
            "0.1.0-beta.1",
            "0.1.0-SNAPSHOT",
            "0.1.0",
            "0.1.1-SNAPSHOT",
        )
        ordered.zipWithNext { lower, higher ->
            val manifest = AwakeProjectManifest(
                id = "com.example.demo",
                name = "Demo",
                version = "1.0.0",
                minEngineVersion = higher,
                entryScene = "scenes/main.scene.json",
            )
            assertFalse(AwakeProjectValidator.isCompatible(manifest, lower), "$lower must not satisfy $higher")
            assertTrue(AwakeProjectValidator.isCompatible(manifest.copy(minEngineVersion = lower), higher), "$higher must satisfy $lower")
        }
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
        assertFalse(AwakeProjectValidator.isSafeProjectPath("assets/./world.zip"))
        assertFalse(AwakeProjectValidator.isSafeProjectPath("assets//world.zip"))
        assertFalse(AwakeProjectValidator.isSafeProjectPath("assets\\world.zip"))
        assertTrue(AwakeProjectValidator.isSafeProjectPath("assets/world.zip"))
    }

    @Test
    fun manifestRejectsDuplicateRootsAndPlugins() {
        val manifest = AwakeProjectManifest(
            id = "com.example.game",
            name = "Example Game",
            version = "1.0.0",
            entryScene = "scenes/main.scene.json",
            assetRoots = listOf("assets", "assets"),
            plugins = listOf(
                AwakeProjectPluginReference("com.example.tools", "plugins/tools.awakeplugin"),
                AwakeProjectPluginReference("com.example.tools", "plugins/other.awakeplugin"),
            ),
        )

        val issues = AwakeProjectValidator.manifestIssues(manifest)
        assertTrue(issues.any { it.contains("assetRoots must not contain duplicates") })
        assertTrue(issues.any { it.contains("plugins must not contain duplicate ids") })
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

    @Test
    fun pluginReferenceNamesItsPublishedArtifactAndCapabilityClass() {
        val published = AwakeProjectPluginReference(
            id = "com.example.docks",
            path = "plugins/docks.awakeplugin",
            required = true,
            artifact = AwakeProjectArtifact(group = "com.example", name = "docks-capability", version = "1.2.0"),
            capabilityClass = "com.example.docks.DocksCapability",
        )
        val manifest = AwakeProjectManifest(
            id = "com.example.game",
            name = "Example Game",
            version = "1.0.0",
            entryScene = "scenes/main.scene.json",
            plugins = listOf(published, AwakeProjectPluginReference(id = "com.example.local", path = "capabilities")),
        )

        val json = AwakeProjectValidator.encodeManifest(manifest)
        val decoded = AwakeProjectValidator.decodeManifest(json)

        assertEquals(manifest, decoded)
        assertTrue("\"capabilityClass\"" in json && "\"artifact\"" in json, json)
        assertEquals("com.example:docks-capability:1.2.0", decoded.plugins.first().artifact.toString())
        assertTrue(AwakeProjectValidator.manifestIssues(decoded).isEmpty(), "a project-local plugin needs neither field")
    }

    @Test
    fun pluginReferenceRejectsMalformedArtifactAndCapabilityClass() {
        val manifest = AwakeProjectManifest(
            id = "com.example.game",
            name = "Example Game",
            version = "1.0.0",
            entryScene = "scenes/main.scene.json",
            plugins = listOf(
                AwakeProjectPluginReference(
                    id = "com.example.docks",
                    path = "plugins/docks.awakeplugin",
                    artifact = AwakeProjectArtifact(group = "com.example", name = "docks capability", version = ""),
                    capabilityClass = "DocksCapability",
                ),
            ),
        )

        val issues = AwakeProjectValidator.manifestIssues(manifest)

        assertTrue(issues.any { it.contains("plugins[0].artifact.name must be") }, issues.toString())
        assertTrue(issues.any { it.contains("plugins[0].artifact.version must be") }, issues.toString())
        assertFalse(issues.any { it.contains("artifact.group") }, issues.toString())
        assertTrue(issues.any { it.contains("plugins[0].capabilityClass must be a fully qualified class name") }, issues.toString())
    }

    /** A game's own capability is compiled into its app project, so its entry names a class and no file. */
    @Test
    fun aCapabilityCompiledIntoTheGameNamesNoFile() {
        val json = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0",
            "entryScene":"scenes/main.scene.json",
            "plugins":[{"id":"com.example.harbor-town.beacon","capabilityClass":"com.example.harbor.BeaconCapability","required":true}]}"""

        val manifest = AwakeProjectValidator.decodeManifest(json)

        assertEquals("", manifest.plugins.single().path)
        assertTrue(AwakeProjectValidator.manifestIssues(manifest).isEmpty(), AwakeProjectValidator.manifestIssues(manifest).toString())
        val nothingToLoad = manifest.copy(plugins = listOf(AwakeProjectPluginReference(id = "com.example.harbor-town.beacon")))
        assertTrue(
            AwakeProjectValidator.manifestIssues(nothingToLoad).any { it.contains("plugins[0].path must be a safe project-relative path") },
            "an entry with neither a file nor a capability class names nothing",
        )
    }
}
