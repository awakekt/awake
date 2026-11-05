/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.project

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    /**
     * `assets.lock.json` is checked in, so verification, indexing and bundling never force `generateAssetsLock`
     * on their own, yet each orders itself after `generateAssetsLock` when both are selected in one build.
     */
    @Test
    fun consumersOfTheAssetsLockRunAfterItsGeneratorWithoutDependingOnIt() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.awakekt.awake.plugin.project-content")
        val lockTask = project.tasks.getByName("generateAssetsLock")

        listOf(
            "validateProject",
            "verifyAssetsLock",
            "generateProjectIndex",
            "generatePublicContentBundles",
            "generateContentBundles",
        ).forEach { name ->
            val task = project.tasks.getByName(name)
            val dependencies = task.taskDependencies.getDependencies(task)
            val mustRunAfter = task.mustRunAfter.getDependencies(task)
            assertFalse(lockTask in dependencies, "$name must not force generateAssetsLock when run standalone: $dependencies")
            assertTrue(lockTask in mustRunAfter, "$name must run after generateAssetsLock when both are scheduled: $mustRunAfter")
        }
    }

    /**
     * Regression for #1312: selecting `generateProjectIndex` (directly or through a consumer task) and
     * `generateAssetsLock` in one task graph succeeds in either requested order and hashes the fresh lock.
     */
    @Test
    fun combinedIndexAndLockGeneratorsSucceedInEitherOrderAndHashTheFreshLock() {
        withHarborProject(
            extraBuild = """
                val generateScreenSet = tasks.register("generateScreenSet") {
                    dependsOn("generateProjectIndex")
                }
            """.trimIndent(),
        ) { dir ->
            // Order 1: index requested before lock, starting from an empty stale lock on disk.
            val indexFirst = runGradle(dir, "generateProjectIndex", "generateAssetsLock")
            assertRanBefore(indexFirst, ":generateAssetsLock", ":generateProjectIndex")
            assertEquals(TaskOutcome.SUCCESS, indexFirst.task(":generateAssetsLock")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, indexFirst.task(":generateProjectIndex")?.outcome)
            assertLockMatchesAssets(File(dir, "assets.lock.json"), dir, listOf("assets/models/crate.glb"))
            assertIndexMatchesDisk(
                File(dir, "build/project.index.json"),
                dir,
                setOf(
                    "awake.project.json",
                    "assets.lock.json",
                    "assets/models/crate.glb",
                    "scenes/harbor.scene.json",
                    "strings/en.json",
                ),
            )

            // Repeat run with unchanged inputs stays up-to-date.
            val upToDate = runGradle(dir, "generateProjectIndex", "generateAssetsLock")
            assertEquals(TaskOutcome.UP_TO_DATE, upToDate.task(":generateAssetsLock")?.outcome)
            assertEquals(TaskOutcome.UP_TO_DATE, upToDate.task(":generateProjectIndex")?.outcome)

            // Order 2: lock requested before index after modifying and adding assets.
            File(dir, "assets/models/crate.glb").writeText("crate-v2-updated")
            File(dir, "assets/textures/pier.png").apply { parentFile.mkdirs() }.writeText("pier-bytes")
            val lockFirst = runGradle(dir, "generateAssetsLock", "generateProjectIndex")
            assertRanBefore(lockFirst, ":generateAssetsLock", ":generateProjectIndex")
            assertEquals(TaskOutcome.SUCCESS, lockFirst.task(":generateAssetsLock")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, lockFirst.task(":generateProjectIndex")?.outcome)
            assertLockMatchesAssets(
                File(dir, "assets.lock.json"),
                dir,
                listOf("assets/models/crate.glb", "assets/textures/pier.png"),
            )
            assertIndexMatchesDisk(
                File(dir, "build/project.index.json"),
                dir,
                setOf(
                    "awake.project.json",
                    "assets.lock.json",
                    "assets/models/crate.glb",
                    "assets/textures/pier.png",
                    "scenes/harbor.scene.json",
                    "strings/en.json",
                ),
            )

            // Consumer task selecting generateProjectIndex alongside generateAssetsLock in both orders.
            File(dir, "assets/models/crate.glb").writeText("crate-v3")
            val consumerFirst = runGradle(dir, "generateScreenSet", "generateAssetsLock")
            assertRanBefore(consumerFirst, ":generateAssetsLock", ":generateProjectIndex")
            assertRanBefore(consumerFirst, ":generateProjectIndex", ":generateScreenSet")
            assertIndexMatchesDisk(
                File(dir, "build/project.index.json"),
                dir,
                setOf(
                    "awake.project.json",
                    "assets.lock.json",
                    "assets/models/crate.glb",
                    "assets/textures/pier.png",
                    "scenes/harbor.scene.json",
                    "strings/en.json",
                ),
            )

            File(dir, "assets/models/crate.glb").writeText("crate-v4")
            val lockBeforeConsumer = runGradle(dir, "generateAssetsLock", "generateScreenSet")
            assertRanBefore(lockBeforeConsumer, ":generateAssetsLock", ":generateProjectIndex")
            assertRanBefore(lockBeforeConsumer, ":generateProjectIndex", ":generateScreenSet")
            assertIndexMatchesDisk(
                File(dir, "build/project.index.json"),
                dir,
                setOf(
                    "awake.project.json",
                    "assets.lock.json",
                    "assets/models/crate.glb",
                    "assets/textures/pier.png",
                    "scenes/harbor.scene.json",
                    "strings/en.json",
                ),
            )
        }
    }

    /**
     * Standalone `generateProjectIndex` and `checkProjectContent` must not run `generateAssetsLock` or
     * overwrite a stale lock before `verifyAssetsLock` catches it, while explicitly combining
     * `checkProjectContent` with `generateAssetsLock` updates the lock first in either CLI order.
     */
    @Test
    fun standaloneIndexAndContentChecksPreserveLockVerificationWhileCombinedRunsRefreshFirst() {
        withHarborProject { dir ->
            runGradle(dir, "generateAssetsLock")
            File(dir, "build").deleteRecursively()

            val standaloneIndex = runGradle(dir, "generateProjectIndex")
            assertNull(standaloneIndex.task(":generateAssetsLock"), "standalone index must not schedule generateAssetsLock")
            assertEquals(TaskOutcome.SUCCESS, standaloneIndex.task(":generateProjectIndex")?.outcome)

            val standaloneCheck = runGradle(dir, "checkProjectContent")
            assertNull(standaloneCheck.task(":generateAssetsLock"), "checkProjectContent must not schedule generateAssetsLock")
            assertEquals(TaskOutcome.SUCCESS, standaloneCheck.task(":validateProject")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, standaloneCheck.task(":verifyAssetsLock")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, standaloneCheck.task(":checkProjectContent")?.outcome)

            // Modify an asset without updating assets.lock.json: standalone index leaves the stale lock untouched,
            // and checkProjectContent fails in verifyAssetsLock instead of regenerating the lock.
            val committedLock = File(dir, "assets.lock.json").readText()
            File(dir, "assets/models/crate.glb").writeText("tampered-crate-bytes")

            val indexWithStaleLock = runGradle(dir, "generateProjectIndex")
            assertNull(indexWithStaleLock.task(":generateAssetsLock"))
            assertEquals(committedLock, File(dir, "assets.lock.json").readText(), "standalone index must not rewrite assets.lock.json")

            val failedCheck = GradleRunner.create()
                .withProjectDir(dir)
                .withPluginClasspath()
                .withArguments("checkProjectContent", "--stacktrace", "--max-workers=2")
                .buildAndFail()
            assertNull(failedCheck.task(":generateAssetsLock"))
            assertEquals(TaskOutcome.FAILED, failedCheck.task(":verifyAssetsLock")?.outcome)
            assertContains(failedCheck.output, "assets/models/crate.glb")

            // Explicitly requesting checkProjectContent + generateAssetsLock together in either order refreshes first.
            val checkThenLock = runGradle(dir, "checkProjectContent", "generateAssetsLock")
            assertRanBefore(checkThenLock, ":generateAssetsLock", ":validateProject")
            assertRanBefore(checkThenLock, ":generateAssetsLock", ":verifyAssetsLock")
            assertRanBefore(checkThenLock, ":generateAssetsLock", ":generateProjectIndex")
            assertLockMatchesAssets(File(dir, "assets.lock.json"), dir, listOf("assets/models/crate.glb"))
            assertIndexMatchesDisk(
                File(dir, "build/project.index.json"),
                dir,
                setOf(
                    "awake.project.json",
                    "assets.lock.json",
                    "assets/models/crate.glb",
                    "scenes/harbor.scene.json",
                    "strings/en.json",
                ),
            )

            File(dir, "assets/models/crate.glb").writeText("refreshed-again")
            val lockThenCheck = runGradle(dir, "generateAssetsLock", "checkProjectContent")
            assertRanBefore(lockThenCheck, ":generateAssetsLock", ":verifyAssetsLock")
            assertRanBefore(lockThenCheck, ":generateAssetsLock", ":generateProjectIndex")
            assertLockMatchesAssets(File(dir, "assets.lock.json"), dir, listOf("assets/models/crate.glb"))
            assertIndexMatchesDisk(
                File(dir, "build/project.index.json"),
                dir,
                setOf(
                    "awake.project.json",
                    "assets.lock.json",
                    "assets/models/crate.glb",
                    "scenes/harbor.scene.json",
                    "strings/en.json",
                ),
            )
        }
    }

    /**
     * `generateContentBundles`, `generatePublicContentBundles`, `generateProjectIndex` and `validateProject`
     * all follow a custom `projectContent.assetsLockFile` and order cleanly with `generateAssetsLock`.
     */
    @Test
    fun combinedBundleAndLockGeneratorsSucceedInEitherOrderWithCustomAssetsLockFile() {
        withHarborProject { dir ->
            File(dir, "assets.lock.json").delete()
            val workerJar = compileBundleWorkerStub(dir)
            File(dir, "build.gradle.kts").writeText(
                """
                import com.awakekt.awake.build.project.GenerateContentBundlesTask

                plugins {
                    id("com.awakekt.awake.plugin.project-content")
                }
                projectContent {
                    assetsLockFile.set(layout.projectDirectory.file("locks/assets.lock.json"))
                    assetRoots.set(listOf("assets"))
                    indexRoots.set(listOf("assets", "scenes", "strings"))
                    publicFiles.set(listOf("strings/**"))
                    publicScenes.set(listOf("scenes/harbor.scene.json"))
                }
                tasks.withType<GenerateContentBundlesTask>().configureEach {
                    workerClasspath.setFrom(files("${workerJar.invariantSeparatorsPath}"))
                }
                """.trimIndent() + "\n",
            )

            val bundlesFirst = runGradle(dir, "generateContentBundles", "generateAssetsLock")
            assertRanBefore(bundlesFirst, ":generateAssetsLock", ":generateProjectIndex")
            assertRanBefore(bundlesFirst, ":generateProjectIndex", ":generatePublicContentBundles")
            assertRanBefore(bundlesFirst, ":generatePublicContentBundles", ":generateContentBundles")
            assertLockMatchesAssets(File(dir, "locks/assets.lock.json"), dir, listOf("assets/models/crate.glb"))
            assertIndexMatchesDisk(
                File(dir, "build/project.index.json"),
                dir,
                setOf("locks/assets.lock.json", "assets/models/crate.glb"),
            )
            assertIndexMatchesDisk(
                File(dir, "build/project.public.index.json"),
                dir,
                setOf("awake.project.json", "scenes/harbor.scene.json", "strings/en.json"),
            )

            File(dir, "assets/models/crate.glb").writeText("crate-bundled-v2")
            val lockFirst = runGradle(dir, "generateAssetsLock", "generateContentBundles")
            assertRanBefore(lockFirst, ":generateAssetsLock", ":generateProjectIndex")
            assertRanBefore(lockFirst, ":generateProjectIndex", ":generateContentBundles")
            assertLockMatchesAssets(File(dir, "locks/assets.lock.json"), dir, listOf("assets/models/crate.glb"))
            assertIndexMatchesDisk(
                File(dir, "build/project.index.json"),
                dir,
                setOf("locks/assets.lock.json", "assets/models/crate.glb"),
            )

            val checkCustomLock = runGradle(dir, "checkProjectContent")
            assertNull(checkCustomLock.task(":generateAssetsLock"))
            assertEquals(TaskOutcome.SUCCESS, checkCustomLock.task(":validateProject")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, checkCustomLock.task(":verifyAssetsLock")?.outcome)
        }
    }

    private fun withHarborProject(extraBuild: String = "", block: (File) -> Unit) {
        val dir = createTempDirectory("awake-project-content-test").toFile()
        try {
            File(dir, "settings.gradle.kts").writeText("rootProject.name = \"harbor\"\n")
            File(dir, "gradle.properties").writeText("org.gradle.jvmargs=-Xmx1g -XX:ActiveProcessorCount=2\n")
            File(dir, "build.gradle.kts").writeText(
                """
                plugins {
                    id("com.awakekt.awake.plugin.project-content")
                }
                projectContent {
                    assetRoots.set(listOf("assets"))
                    indexRoots.set(listOf("assets", "scenes", "strings"))
                }
                $extraBuild
                """.trimIndent() + "\n",
            )
            File(dir, "awake.project.json").writeText(
                """{"formatVersion":1,"id":"com.example.harbor","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/harbor.scene.json"}""",
            )
            File(dir, "assets.lock.json").writeText("""{"formatVersion":1,"assets":{}}""")
            File(dir, "assets/models/crate.glb").apply { parentFile.mkdirs() }.writeText("crate-v1")
            File(dir, "scenes/harbor.scene.json").apply { parentFile.mkdirs() }.writeText("""{"version":1,"name":"harbor","nodes":[]}""")
            File(dir, "strings/en.json").apply { parentFile.mkdirs() }.writeText("""{"title":"Harbor Town"}""")
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun runGradle(dir: File, vararg tasks: String): BuildResult =
        GradleRunner.create()
            .withProjectDir(dir)
            .withPluginClasspath()
            .withArguments(*tasks, "--stacktrace", "--max-workers=2")
            .build()

    private fun assertRanBefore(result: BuildResult, earlierTask: String, laterTask: String) {
        val order = result.tasks.map { it.path }
        val earlier = order.indexOf(earlierTask)
        val later = order.indexOf(laterTask)
        assertTrue(earlier >= 0 && later >= 0 && earlier < later, "Expected $earlierTask before $laterTask, got $order")
    }

    private fun assertLockMatchesAssets(lockFile: File, projectRoot: File, expectedPaths: List<String>) {
        assertTrue(lockFile.isFile, "Missing lock file: $lockFile")
        val lock = Json.parseToJsonElement(lockFile.readText()).jsonObject
        val assets = lock.getValue("assets").jsonObject
        assertEquals(expectedPaths.sorted(), assets.keys.sorted())
        expectedPaths.forEach { path ->
            val diskFile = File(projectRoot, path)
            val pin = assets.getValue(path).jsonObject
            assertEquals(diskFile.length(), pin.getValue("sizeBytes").jsonPrimitive.long, "sizeBytes in lock for $path")
            assertEquals(sha256(diskFile), pin.getValue("sha256").jsonPrimitive.content, "sha256 in lock for $path")
        }
    }

    private fun assertIndexMatchesDisk(indexFile: File, projectRoot: File, expectedPaths: Set<String>) {
        assertTrue(indexFile.isFile, "Missing index file: $indexFile")
        val files = Json.parseToJsonElement(indexFile.readText()).jsonObject.getValue("files").jsonArray
            .associateBy { it.jsonObject.getValue("path").jsonPrimitive.content }
        assertEquals(expectedPaths, files.keys)
        expectedPaths.forEach { path ->
            val diskFile = File(projectRoot, path)
            val entry = files.getValue(path).jsonObject
            assertEquals(diskFile.length(), entry.getValue("sizeBytes").jsonPrimitive.long, "sizeBytes in index for $path")
            assertEquals(sha256(diskFile), entry.getValue("sha256").jsonPrimitive.content, "sha256 in index for $path")
        }
    }

    /**
     * Minimal stand-in for `com.awakekt.awake:project`'s `ContentBundleGradleWorker` so `build-logic` can
     * exercise `GenerateContentBundlesTask` under `GradleRunner` without linking the runtime module.
     */
    private fun compileBundleWorkerStub(dir: File): File {
        val srcRoot = File(dir, ".worker-stub/src").apply { mkdirs() }
        val classesRoot = File(dir, ".worker-stub/classes").apply { mkdirs() }
        val source = File(srcRoot, "com/awakekt/awake/project/ContentBundleGradleWorker.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.awakekt.awake.project;

                import java.io.File;
                import java.nio.file.Files;

                public final class ContentBundleGradleWorker {
                    public static long[] run(
                        File indexFile,
                        File projectDirectory,
                        File outputDirectory,
                        String grouping,
                        int folderDepth,
                        long targetBundleBytes,
                        boolean opaqueIds,
                        String pathSalt,
                        String bundleUrlPrefix,
                        boolean pruneStaleBundles,
                        String keyId,
                        File keyFile,
                        String keyEnvironmentVariable
                    ) throws Exception {
                        if (!indexFile.isFile()) {
                            throw new IllegalStateException("Missing index file: " + indexFile);
                        }
                        outputDirectory.mkdirs();
                        Files.copy(
                            indexFile.toPath(),
                            new File(outputDirectory, "project.index.json").toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        );
                        return new long[] { 1L, 1L, 0L, 1L };
                    }
                }
                """.trimIndent() + "\n",
            )
        }
        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler()) { "JDK compiler required for test worker stub" }
        val exitCode = compiler.run(null, null, null, "-d", classesRoot.absolutePath, source.absolutePath)
        check(exitCode == 0) { "Failed to compile ContentBundleGradleWorker stub: $exitCode" }
        val jarFile = File(dir, ".worker-stub/worker.jar")
        JarOutputStream(jarFile.outputStream()).use { jar ->
            classesRoot.walkTopDown().filter(File::isFile).forEach { classFile ->
                val entryName = classFile.relativeTo(classesRoot).invariantSeparatorsPath
                jar.putNextEntry(JarEntry(entryName))
                jar.write(classFile.readBytes())
                jar.closeEntry()
            }
        }
        return jarFile
    }
}
