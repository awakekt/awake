/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.project

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** One `projectContent` declaration splits a project into a public sign-in set and protected content. */
class PublicContentSetTest {
    @Test
    fun theSignInSceneBringsEverythingItNamesAndNothingElse() {
        val root = project()

        val set = publicContentSet(root, paths(root), globs = listOf("strings/**"), scenes = listOf("scenes/sign-in.scene.json"))

        assertEquals(
            setOf(
                "awake.project.json",
                "strings/en.json",
                "scenes/sign-in.scene.json",
                "assets/ui/logo.png",
                "assets/prefabs/button.prefab.json",
                "assets/fonts/ui.ttf",
                "assets/models/frame.glb",
            ),
            set,
        )
    }

    @Test
    fun noDeclarationMeansNoPublicSet() {
        val root = project()

        assertEquals(emptySet(), publicContentSet(root, paths(root), emptyList(), emptyList()))
    }

    @Test
    fun aGlobThatMatchesNothingOrAMissingSceneFails() {
        val root = project()

        assertFailsWith<IllegalArgumentException> { publicContentSet(root, paths(root), listOf("fonts/**"), emptyList()) }
        assertFailsWith<IllegalArgumentException> { publicContentSet(root, paths(root), emptyList(), listOf("scenes/login.scene.json")) }
    }

    @Test
    fun theTaskWritesEachFileToExactlyOneIndex() {
        val root = project()
        val content = paths(root).toSet()
        val project = ProjectBuilder.builder().withProjectDir(root).build()
        project.pluginManager.apply("com.awakekt.awake.plugin.project-content")
        val declaration = project.extensions.getByType(ProjectContentExtension::class.java)
        declaration.publicScenes.set(listOf("scenes/sign-in.scene.json"))
        declaration.publicFiles.set(listOf("strings/**"))
        val task = project.tasks.getByName("generateProjectIndex") as GenerateProjectIndexTask

        task.generate()

        val public = listed(task.publicIndexOutputFile.get().asFile)
        val protected = listed(task.outputFile.get().asFile)
        assertTrue("scenes/sign-in.scene.json" in public && "awake.project.json" in public, "public: $public")
        assertEquals(setOf("scenes/harbor.scene.json", "assets/models/ship.glb", "assets.lock.json"), protected)
        assertEquals(emptySet(), public intersect protected, "no file is in both indexes")
        assertEquals(content, public + protected, "every file is in one index")
    }

    @Test
    fun withoutAPublicSetTheTaskWritesOneIndexAsBefore() {
        val root = project()
        val content = paths(root).toSet()
        val project = ProjectBuilder.builder().withProjectDir(root).build()
        project.pluginManager.apply("com.awakekt.awake.plugin.project-content")
        val task = project.tasks.getByName("generateProjectIndex") as GenerateProjectIndexTask

        task.generate()

        assertFalse(task.publicIndexOutputFile.get().asFile.exists())
        assertEquals(content, listed(task.outputFile.get().asFile))
    }

    @Test
    fun theTaskReportsABadDeclarationAsABuildFailure() {
        val root = project()
        val project = ProjectBuilder.builder().withProjectDir(root).build()
        project.pluginManager.apply("com.awakekt.awake.plugin.project-content")
        project.extensions.getByType(ProjectContentExtension::class.java).publicFiles.set(listOf("nothing/**"))
        val task = project.tasks.getByName("generateProjectIndex") as GenerateProjectIndexTask

        assertFailsWith<GradleException> { task.generate() }
    }

    /** Each set bundles from its own index into its own folder, so neither prunes the other's bundles. */
    @Test
    fun thePublicSetBundlesApartFromTheProtectedOne() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.awakekt.awake.plugin.project-content")
        val index = project.tasks.getByName("generateProjectIndex") as GenerateProjectIndexTask
        val publicBundles = project.tasks.getByName("generatePublicContentBundles") as GenerateContentBundlesTask
        val bundles = project.tasks.getByName("generateContentBundles") as GenerateContentBundlesTask

        assertEquals(index.publicIndexOutputFile.get().asFile, publicBundles.indexFile.get().asFile)
        assertEquals(project.layout.buildDirectory.dir("published-public").get().asFile, publicBundles.outputDirectory.get().asFile)
        assertTrue(publicBundles.outputDirectory.get().asFile != bundles.outputDirectory.get().asFile)
        assertEquals(bundles.grouping.get(), publicBundles.grouping.get(), "both sets bundle alike")
        assertTrue(publicBundles in bundles.taskDependencies.getDependencies(bundles), "one command writes both sets")
    }

    /** Harbor Town: a sign-in scene, a protected world, and a prefab and model the sign-in scene names. */
    private fun project(): File = kotlin.io.path.createTempDirectory("public-set").toFile().apply {
        write("awake.project.json", """{"id":"com.example.harbor-town","name":"Harbor Town","version":"1","entryScene":"scenes/harbor.scene.json"}""")
        write("assets.lock.json", """{"formatVersion":1,"assets":{}}""")
        write("strings/en.json", """{"sign_in":"Sign in"}""")
        write("scenes/sign-in.scene.json", """{"nodes":[{"image":"assets/ui/logo.png","prefab":"assets/prefabs/button.prefab.json"}]}""")
        write("assets/prefabs/button.prefab.json", """{"font":"assets/fonts/ui.ttf","mesh":"assets/models/frame.glb#0","label":"Play"}""")
        write("scenes/harbor.scene.json", """{"nodes":[{"mesh":"assets/models/ship.glb","image":"assets/ui/logo.png"}]}""")
        listOf("assets/ui/logo.png", "assets/fonts/ui.ttf", "assets/models/frame.glb", "assets/models/ship.glb").forEach { write(it, "bytes of $it") }
    }

    private fun File.write(path: String, text: String) = resolve(path).apply { parentFile.mkdirs() }.writeText(text)

    private fun paths(root: File): List<String> =
        root.walkTopDown().filter { file ->
            file.isFile && file.relativeTo(root).invariantSeparatorsPath.split('/').none { it == "build" || it.startsWith(".") }
        }
            .map { it.relativeTo(root).invariantSeparatorsPath }.sorted().toList()

    private fun listed(index: File): Set<String> =
        Json.parseToJsonElement(index.readText()).jsonObject.getValue("files").jsonArray
            .map { it.jsonObject.getValue("path").jsonPrimitive.content }.toSet()
}
