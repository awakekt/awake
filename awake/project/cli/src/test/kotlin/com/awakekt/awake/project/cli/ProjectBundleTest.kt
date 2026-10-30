/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.Sha256
import com.awakekt.awake.project.IndexedAssetSource
import com.awakekt.awake.project.ProjectIndex
import com.awakekt.awake.project.ProjectIndexEntry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `awake bundle`: a loose index's files packed into bundles on disk, and the bundled index beside them. */
class ProjectBundleTest {
    private val root: File = createTempDirectory("awake-bundle").toFile()
    private val output: File get() = root.resolve("build/published")

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun theBundledIndexReadsEveryFileBackFromTheBundlesWritten() {
        project()

        val run = awake("bundle", "build/project.index.json", "--output", "build/published", "--project", ".")

        assertEquals(0, run.code, run.all)
        assertContains(run.out, "bundled 4 files into 3 bundles")
        val index = Json.decodeFromString(ProjectIndex.serializer(), output.resolve("project.index.json").readText())
        val source = IndexedAssetSource(index, { url -> output.resolve(url).readBytes() })
        FILES.forEach { (path, text) -> assertContentEquals(text.encodeToByteArray(), runBlocking { source.read(AssetPath(path)).getOrThrow() }, path) }
        assertEquals(3, output.resolve("bundles").listFiles()!!.size)
    }

    @Test
    fun aSecondRunWritesTheSameBundlesAndNothingNew() {
        project()
        awake("bundle", "build/project.index.json", "--output", "build/published", "--project", ".")
        val first = output.resolve("bundles").listFiles()!!.associate { it.name to it.readBytes().toList() }

        val again = Json.parseToJsonElement(awake("bundle", "build/project.index.json", "--output", "build/published", "--project", ".", "--json").out).jsonObject

        assertEquals(0, again["written"]!!.jsonPrimitive.int, "unchanged bundles keep their names and aren't written again")
        assertEquals(first, output.resolve("bundles").listFiles()!!.associate { it.name to it.readBytes().toList() })
    }

    @Test
    fun anUnlistedBundleStaysUnlessPruneIsAsked() {
        project()
        awake("bundle", "build/project.index.json", "--output", "build/published", "--project", ".")
        val listed = output.resolve("bundles").listFiles()!!.map { it.name }.toSet()
        val stale = output.resolve("bundles/${"0".repeat(64)}.awkb").apply { writeText("the last release's bundle") }

        val kept = awake("bundle", "build/project.index.json", "--output", "build/published", "--project", ".")
        assertEquals(0, kept.code, kept.all)
        assertTrue(stale.isFile, "a publish folder may still serve the last release")

        val pruned = awake("bundle", "build/project.index.json", "--output", "build/published", "--project", ".", "--prune")
        assertEquals(0, pruned.code, pruned.all)
        assertFalse(stale.exists(), "--prune deletes a bundle the new index doesn't list")
        assertEquals(listed, output.resolve("bundles").listFiles()!!.map { it.name }.toSet())
    }

    @Test
    fun opaqueIdsNeedASaltAndLeaveNoPathInTheIndex() {
        project()

        val refused = awake("bundle", "build/project.index.json", "--output", "build/published", "--project", ".", "--opaque-ids")
        val run = awake("bundle", "build/project.index.json", "--output", "build/published", "--project", ".", "--opaque-ids", "--salt", "com.example.harbor")

        assertEquals(2, refused.code, refused.all)
        assertEquals(0, run.code, run.all)
        val index = output.resolve("project.index.json").readText()
        FILES.keys.forEach { assertFalse(it in index, "the index names $it") }
    }

    @Test
    fun aStaleIndexFailsNamingTheFile() {
        project()
        root.resolve("assets/models/crate.gltf").writeText("changed since the index was made")

        val run = awake("bundle", "build/project.index.json", "--output", "build/published", "--project", ".")

        assertEquals(1, run.code, run.all)
        assertContains(run.err, "assets/models/crate.gltf")
        assertTrue(!output.resolve("project.index.json").exists())
    }

    @Test
    fun generateContentBundlesGradleTaskWritesByteIdenticalOutputToCli() {
        FILES.forEach { (path, text) -> root.resolve(path).apply { parentFile.mkdirs() }.writeText(text) }
        writeGradleContentSettings()

        writeGradleContentBuild()
        runGenerateContentBundles()
        val cliOut = root.resolve("build/cli-published")
        val cli = awake("bundle", "build/project.index.json", "--output", "build/cli-published", "--project", ".")
        assertEquals(0, cli.code, cli.all)
        assertSameTree(cliOut, output)

        writeGradleContentBuild(
            """
            projectContent {
                bundleGrouping.set("one")
                targetBundleBytes.set(4L * 1024 * 1024)
                bundleOpaqueIds.set(true)
                bundlePathSalt.set("com.example.harbor")
            }
            """.trimIndent(),
        )
        runGenerateContentBundles()
        val cliOpaqueOut = root.resolve("build/cli-opaque")
        val cliOpaque = awake(
            "bundle",
            "build/project.index.json",
            "--output",
            "build/cli-opaque",
            "--project",
            ".",
            "--group",
            "one",
            "--target-mb",
            "4",
            "--opaque-ids",
            "--salt",
            "com.example.harbor",
        )
        assertEquals(0, cliOpaque.code, cliOpaque.all)
        assertSameTree(cliOpaqueOut, output)
    }

    private fun writeGradleContentSettings() {
        val buildLogicDir = File(System.getProperty("awake.buildLogic.dir")).invariantSeparatorsPath
        val gradleJavaHome = File(System.getProperty("awake.gradle.javaHome")).invariantSeparatorsPath
        root.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                includeBuild("$buildLogicDir")
                repositories {
                    gradlePluginPortal()
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "harbor-content"
            """.trimIndent() + "\n",
        )
        root.resolve("gradle.properties").writeText("org.gradle.java.home=$gradleJavaHome\n")
    }

    private fun writeGradleContentBuild(extraConfig: String = "") {
        val workerFiles = System.getProperty("awake.project.workerClasspath")
            .split(File.pathSeparator)
            .filter(String::isNotBlank)
            .joinToString(", ") { "\"${File(it).invariantSeparatorsPath}\"" }
        root.resolve("build.gradle.kts").writeText(
            """
            import com.awakekt.awake.build.project.GenerateContentBundlesTask

            plugins {
                id("com.awakekt.awake.plugin.project-content")
            }

            $extraConfig

            tasks.named<GenerateContentBundlesTask>("generateContentBundles") {
                workerClasspath.setFrom(files($workerFiles))
            }
            """.trimIndent() + "\n",
        )
    }

    private fun runGenerateContentBundles() {
        GradleRunner.create()
            .withProjectDir(root)
            .withArguments("generateContentBundles", "--stacktrace")
            .build()
    }

    private fun assertSameTree(expectedDir: File, actualDir: File) {
        val expected = expectedDir.walkTopDown()
            .filter(File::isFile)
            .associate { it.relativeTo(expectedDir).invariantSeparatorsPath to it.readBytes() }
        val actual = actualDir.walkTopDown()
            .filter(File::isFile)
            .associate { it.relativeTo(actualDir).invariantSeparatorsPath to it.readBytes() }
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (path, bytes) -> assertContentEquals(bytes, actual.getValue(path), path) }
    }

    private fun project() {
        FILES.forEach { (path, text) -> root.resolve(path).apply { parentFile.mkdirs() }.writeText(text) }
        val index = ProjectIndex(
            files = FILES.map { (path, text) ->
                val bytes = text.encodeToByteArray()
                ProjectIndexEntry(path = path, sizeBytes = bytes.size.toLong(), sha256 = Sha256.digestHex(bytes), url = "files/$path")
            },
        )
        root.resolve("build/project.index.json").apply { parentFile.mkdirs() }.writeText(Json.encodeToString(ProjectIndex.serializer(), index))
    }

    private fun awake(vararg args: String): Run {
        val out = StringBuilder()
        val err = StringBuilder()
        val code = AwakeCli(out, err, root).run(args.toList())
        return Run(code, out.toString(), err.toString())
    }

    private class Run(val code: Int, val out: String, val err: String) {
        val all: String get() = "exit $code\n$out\n$err"
    }

    private companion object {
        val FILES = linkedMapOf(
            "awake.project.json" to """{"formatVersion":1,"id":"com.example.harbor","name":"Harbor","version":"1.0.0","entryScene":"scenes/harbor.scene.json"}""",
            "scenes/harbor.scene.json" to """{"version":1,"name":"harbor","nodes":[]}""",
            "assets/models/crate.gltf" to """{"asset":{"version":"2.0"}}""",
            "assets/textures/wood.txt" to "grain",
        )
    }
}
