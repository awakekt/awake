/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every build lists the Gradle Plugin Portal last (#1592).
 *
 * Gradle tries repositories in order and stops at one that fails to answer, so the Portal listed
 * first failed builds whose plugins Maven Central serves: `com.vanniktech.maven.publish` 0.37.0, in
 * CI runs whose Portal returned an error. Listed last it is asked only for what no other repository
 * holds.
 *
 * Listed last, the Portal is asked only after every repository before it, and one of them answering an
 * error instead of 404 (a Central `429`) stops the search there too. So each build lists the modules only
 * the Portal serves, each plugin's marker and implementation, in `portalOnly`, and Google and Central
 * exclude them.
 */
class PluginPortalOrderTest {
    private val root = File("..")

    @Test
    fun everyRepositoriesBlockListsThePortalLast() {
        val misordered = SETTINGS.flatMap { path ->
            repositoryBlocks(File(root, path).readText()).mapIndexedNotNull { index, entries ->
                val portal = entries.indexOfFirst { it.startsWith("gradlePluginPortal") }
                if (portal >= 0 && portal != entries.lastIndex) "$path, repositories block ${index + 1}: $entries" else null
            }
        }
        assertEquals(emptyList(), misordered, "the Plugin Portal is listed before another repository")
    }

    @Test
    fun everyBuildKeepsGoogleAndCentralOutOfThePortalOnlyLookups() {
        val failures = PORTAL_ONLY.flatMap { (path, modules) ->
            val source = File(root, path).readText()
            val declared = portalOnlyModules(source)
            val missing = modules.filterNot { it in declared }.map { "$path: portalOnly lacks $it" }
            val unfiltered = pluginRepositoryEntries(source)
                .filter { (name, text) -> name in NETWORK_REPOSITORIES && "excludePortalOnly()" !in text }
                .map { (name, _) -> "$path: $name doesn't exclude portalOnly" }
            missing + unfiltered
        }
        assertEquals(emptyList(), failures, "a module only the Plugin Portal serves can be looked up before it")
    }

    @Test
    fun everyGoogleEntryServesOnlyGooglesGroups() {
        val unfiltered = SETTINGS.flatMap { path ->
            repositoryEntries(File(root, path).readText()).flatten()
                .filter { (name, text) -> name == "google" && GOOGLE_GROUPS.any { "includeGroupAndSubgroups(\"$it\")" !in text } }
                .map { (_, text) -> "$path: ${text.lineSequence().first()}" }
        }
        assertEquals(emptyList(), unfiltered, "a Google entry is asked for groups it doesn't serve")
    }

    @Test
    fun theCheckReadsTheExclusionsAndEachRepositorysFilter() {
        val source = """
            pluginManagement {
                val portalOnly = listOf(
                    "a.b:a.b.gradle.plugin",
                    "a:impl",
                )
                repositories {
                    google { content { excludePortalOnly() } }
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
        """.trimIndent()
        assertEquals(setOf("a.b:a.b.gradle.plugin", "a:impl"), portalOnlyModules(source))
        assertEquals(
            listOf("google" to "google { content { excludePortalOnly() } }", "mavenCentral" to "mavenCentral()", "gradlePluginPortal" to "gradlePluginPortal()"),
            pluginRepositoryEntries(source),
        )
    }

    @Test
    fun theCheckSeesThePortalWhereItIsListed() {
        val blocks = repositoryBlocks(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    maven("https://example.org") { content { includeGroup("x") } }
                    google()
                }
            }
            """.trimIndent(),
        )
        assertEquals(listOf(listOf("gradlePluginPortal", "maven", "google")), blocks)
        assertTrue(SETTINGS.all { File(root, it).isFile }, "every listed settings file exists")
    }

    /** The `group:module` strings of the settings' `portalOnly` list. */
    private fun portalOnlyModules(source: String): Set<String> {
        val list = Regex("""val portalOnly = listOf\(([^)]*)\)""").find(source)?.groupValues?.get(1).orEmpty()
        return Regex(""""([^"]+:[^"]+)"""").findAll(list).map { it.groupValues[1] }.toSet()
    }

    /** `pluginManagement`'s `repositories` entries, each as its name and its whole text. */
    private fun pluginRepositoryEntries(source: String): List<Pair<String, String>> =
        repositoryEntries(source.substring(source.indexOf("pluginManagement"))).firstOrNull().orEmpty()

    private companion object {
        val SETTINGS = listOf(
            "settings.gradle.kts",
            "build-logic/settings.gradle.kts",
            "templates/game/settings.gradle.kts",
            "templates/plugin/settings.gradle.kts",
            "templates/project/settings.gradle.kts",
            "tools/scene-components-consumer/settings.gradle.kts",
        )

        private val FOOJAY = listOf(
            "org.gradle.toolchains.foojay-resolver-convention:org.gradle.toolchains.foojay-resolver-convention.gradle.plugin",
            "org.gradle.toolchains:foojay-resolver",
        )
        private val KTOR = listOf(
            "io.ktor.plugin:io.ktor.plugin.gradle.plugin",
            "io.ktor.plugin:plugin",
            "com.google.cloud.tools:jib-gradle-plugin",
        )

        /** The groups Google serves Core: AGP and its markers, androidx, and com.google's. */
        val GOOGLE_GROUPS = listOf("androidx", "com.android", "com.google")

        /** Repositories asked over the network before the Portal, which must exclude its modules. */
        val NETWORK_REPOSITORIES = setOf("google", "mavenCentral")

        /**
         * The modules each build resolves that only the Plugin Portal serves, read from the plugins'
         * marker POMs (20 Nov): each marker, the implementation it depends on, and ktor's jib.
         */
        val PORTAL_ONLY = mapOf(
            "settings.gradle.kts" to FOOJAY + listOf(
                "org.jetbrains.kotlinx.benchmark:org.jetbrains.kotlinx.benchmark.gradle.plugin",
                "org.jetbrains.kotlinx:kotlinx-benchmark-plugin",
            ),
            "build-logic/settings.gradle.kts" to listOf(
                "org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin",
                "org.gradle.kotlin:gradle-kotlin-dsl-plugins",
            ),
            "templates/game/settings.gradle.kts" to FOOJAY + KTOR,
            "templates/plugin/settings.gradle.kts" to FOOJAY,
            "templates/project/settings.gradle.kts" to FOOJAY + KTOR,
        )
    }
}
