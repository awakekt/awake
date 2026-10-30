/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.project

import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectContentPluginTest {
    /** Kotlin's wasm setup applies the lifecycle plugin to the root of a project with a wasmJs module. */
    @Test
    fun aProjectRootStillConfiguresWhenTheLifecyclePluginComesAfterIt() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.awakekt.awake.plugin.project-content")

        project.pluginManager.apply(LifecycleBasePlugin::class.java)

        val check = project.tasks.getByName("check")
        val runs = check.taskDependencies.getDependencies(check).map { it.name }
        assertTrue("checkProjectContent" in runs, "check runs the content checks: $runs")
    }

    /** The index records each file's size and SHA-256, so it must regenerate when a file changes. */
    @Test
    fun theIndexTakesTheFilesItListsAsInputs() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.awakekt.awake.plugin.project-content")
        val asset = project.file("assets/ui/a b.png").apply {
            parentFile.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }

        val task = project.tasks.getByName("generateProjectIndex") as GenerateProjectIndexTask

        assertTrue(asset in task.inputs.files.asFileTree.files, "an indexed file is an input: ${task.inputs.files.files}")
    }

    /** The lock records each asset's size and SHA-256, so it must regenerate when an asset changes. */
    @Test
    fun theLockTakesTheAssetsItPinsAsInputs() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.awakekt.awake.plugin.project-content")
        val asset = project.file("assets/ui/tabs.png").apply {
            parentFile.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }

        val task = project.tasks.getByName("generateAssetsLock") as GenerateAssetsLockTask

        assertTrue(asset in task.inputs.files.asFileTree.files, "a pinned asset is an input: ${task.inputs.files.files}")
    }

    @Test
    fun aPathSegmentEncodesASpaceAsPercent20NotPlus() {
        assertEquals("a%20b.png", encodePathSegment("a b.png"))
        assertEquals("caf%C3%A9%2B.png", encodePathSegment("café+.png"), "a real plus stays encoded, apart from the space")
    }

    @Test
    fun generateContentBundlesTracksTheLooseIndexAndIndexedFilesWithCliDefaults() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.awakekt.awake.plugin.project-content")
        val asset = project.file("assets/models/crate.glb").apply {
            parentFile.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        val task = project.tasks.getByName("generateContentBundles") as GenerateContentBundlesTask
        val indexTask = project.tasks.getByName("generateProjectIndex") as GenerateProjectIndexTask
        val check = project.tasks.getByName("check")

        assertEquals(indexTask.outputFile.get().asFile, task.indexFile.get().asFile)
        assertTrue(asset in task.indexedFiles.asFileTree.files, "an indexed asset is tracked: ${task.indexedFiles.files}")
        assertEquals(project.layout.buildDirectory.dir("published").get().asFile, task.outputDirectory.get().asFile)
        assertEquals("folder", task.grouping.get())
        assertEquals(1, task.folderDepth.get())
        assertEquals(16L * 1024 * 1024, task.targetBundleBytes.get())
        assertEquals("bundles", task.bundleUrlPrefix.get())
        assertTrue("generateContentBundles" !in check.taskDependencies.getDependencies(check).map { it.name })
    }

    /** A publish folder outside the build may still serve the last release's bundles to players mid-update. */
    @Test
    fun generateContentBundlesPrunesOnlyAnOutputFolderInsideTheBuild() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.awakekt.awake.plugin.project-content")
        val task = project.tasks.getByName("generateContentBundles") as GenerateContentBundlesTask
        val extension = project.extensions.getByType(ProjectContentExtension::class.java)

        assertTrue(task.pruneStaleBundles.get(), "build/published is the build's own")

        extension.bundlesOutputDirectory.set(project.layout.projectDirectory.dir("site/publish"))
        assertEquals(false, task.pruneStaleBundles.get(), "a folder outside the build is kept as it is")

        extension.pruneStaleBundles.set(true)
        assertTrue(task.pruneStaleBundles.get(), "an explicit setting wins")
    }

    /** The bundles read the files the loose index lists, so they take the index task's inputs, not a copy of them. */
    @Test
    fun generateContentBundlesTakesTheIndexTasksInputs() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.awakekt.awake.plugin.project-content")
        project.file("awake.project.json").writeText("{}")
        project.file("scenes/harbor.scene.json").apply {
            parentFile.mkdirs()
            writeText("{}")
        }
        val extension = project.extensions.getByType(ProjectContentExtension::class.java)
        extension.indexRoots.set(listOf("scenes"))

        val bundles = project.tasks.getByName("generateContentBundles") as GenerateContentBundlesTask
        val index = project.tasks.getByName("generateProjectIndex") as GenerateProjectIndexTask

        assertEquals(index.indexedFiles.asFileTree.files, bundles.indexedFiles.asFileTree.files)
        assertTrue(project.file("scenes/harbor.scene.json") in bundles.indexedFiles.asFileTree.files)
    }
}
