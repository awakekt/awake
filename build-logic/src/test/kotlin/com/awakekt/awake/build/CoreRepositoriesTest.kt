/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Core's own build asks only the repositories it resolves from (#1592).
 *
 * Gradle tries repositories in order and reports "Could not find" only once every one has answered
 * 404, so each extra repository is one more lookup per missing module, and one more host whose error
 * fails the build. Core's dependencies came from Google, Central and the `io.ygdrasil` mirror, yet it
 * also listed Maven Local and three snapshot repositories (two of them retired hosts). Its only
 * snapshots are `io.ygdrasil`'s, which all three excluded.
 *
 * Consumer builds (the templates and `tools/`) read Core's own local publish from Maven Local, so
 * they keep it, for Core's group only.
 */
class CoreRepositoriesTest {
    private val root = File("..")

    @Test
    fun coresDependenciesComeFromGoogleCentralItsMirrorAndTheWgpuMirrorOnly() {
        val settings = File(root, "settings.gradle.kts").readText()
        val dependencies = repositoryEntries(settings.substring(settings.indexOf("dependencyResolutionManagement"))).first()

        assertEquals(listOf("google", "mavenCentral", "maven", "maven"), dependencies.map { it.first })
        assertEquals(
            """maven("https://awakekt.github.io/awake/") { content { includeGroup("io.ygdrasil") } }""",
            dependencies.last().second,
        )
    }

    @Test
    fun neitherCoreBuildListsMavenLocalOrASnapshotRepository() {
        val extra = CORE_SETTINGS.flatMap { path ->
            repositoryEntries(File(root, path).readText()).flatten()
                .filter { (name, text) -> name == "mavenLocal" || "snapshots" in text }
                .map { (_, text) -> "$path: ${text.lineSequence().first()}" }
        }
        assertEquals(emptyList(), extra, "Core's own build asks a repository it resolves nothing from")
    }

    @Test
    fun aConsumerBuildReadsOnlyCoreFromMavenLocal() {
        val unfiltered = CONSUMER_BUILDS.flatMap { path -> unfilteredMavenLocal(File(root, path).readText()).map { "$path: $it" } }
        assertEquals(emptyList(), unfiltered, "Maven Local is asked for modules Core doesn't publish")
    }

    @Test
    fun theCheckSeesMavenLocalBehindAConditionToo() {
        val source = """
            repositories {
                if (local) $CORE_ONLY_LOCAL
                if (other) mavenLocal()
                mavenCentral()
            }
        """.trimIndent()

        assertEquals(listOf("if (other) mavenLocal()"), unfilteredMavenLocal(source))
    }

    /** Each entry of [source]'s `repositories` blocks that asks Maven Local for more than Core. */
    private fun unfilteredMavenLocal(source: String): List<String> =
        repositoryEntries(source).flatten().map { it.second }.filter { "mavenLocal" in it && CORE_ONLY_LOCAL !in it }

    private companion object {
        val CORE_SETTINGS = listOf("settings.gradle.kts", "build-logic/settings.gradle.kts")

        val CONSUMER_BUILDS = listOf(
            "templates/game/settings.gradle.kts",
            "templates/plugin/settings.gradle.kts",
            "templates/project/settings.gradle.kts",
            "tools/scene-components-consumer/settings.gradle.kts",
            "tools/vulkan-kmp-smoke/build.gradle.kts",
            "tools/core-alignment-check/build.gradle.kts",
        )

        /** Every module Core publishes, its Gradle plugins' markers included, is in this group. */
        const val CORE_ONLY_LOCAL = """mavenLocal { content { includeGroupAndSubgroups("com.awakekt.awake") } }"""
    }
}
