/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Verifies the Maven-local files produced by a publication before Central upload.
 *
 * With `awake.verifyPinnedCoreVersion`, it also checks that a Vulkan or WebGPU family's module
 * metadata, platform modules included, names Core at exactly that release (see [pinnedCoreViolations]).
 */
@DisableCachingByDefault(because = "Inspects the external Maven local repository and publication metadata")
abstract class VerifyPublishedArtifactsTask : DefaultTask() {
    /** Every `group:artifact` this build publishes, root and platform publications alike. */
    @get:Input
    abstract val publishedModules: SetProperty<String>

    init {
        publishedModules.convention(emptySet())
    }

    @TaskAction
    fun verify() {
        val version = project.findProperty("awake.verifyPublishedVersion")?.toString()
            ?: project.version.toString()
        val family = project.findProperty("awake.verifyPublishedFamily")?.toString()?.lowercase()
        val pinnedCore = project.findProperty("awake.verifyPinnedCoreVersion")?.toString()
        require(pinnedCore == null || family == "vulkan" || family == "webgpu") {
            "awake.verifyPinnedCoreVersion checks the Vulkan or WebGPU family, not ${family ?: "every family"}"
        }
        val repository = Path.of(System.getProperty("user.home"), ".m2", "repository", "com", "awakekt", "awake")
        val directories = if (Files.isDirectory(repository)) {
            Files.walk(repository).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".pom") }
                    .map(Path::getParent)
                    .filter { it.fileName.toString() == version }
                    .filter { !isPlatformVariant(it.parent.fileName.toString()) }
                    .filter { family == null || belongsToFamily(it.parent.fileName.toString(), family) }
                    .distinct()
                    .toList()
            }
        } else emptyList()
        if (directories.isEmpty()) throw GradleException("No Maven-local Awake artifacts found for $version")

        val pinFailures = pinnedCore?.let { core -> verifyPinnedCore(directories, version, core, family!!) }.orEmpty()
        val failures = pinFailures + directories.flatMap { directory ->
            val pom = directory.resolve("${directory.parent.fileName}-$version.pom")
            val metadata = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.toFile())
            listOf("name", "description", "url", "licenses", "developers", "scm").filter { field ->
                metadata.getElementsByTagName(field).length == 0
            }.map { field -> "${relative(directory)}: POM is missing $field" } +
                when (directory.parent.fileName.toString()) {
                    "vulkan-kmp" -> verifyVariant(directory, version, "desktop", "jar", REQUIRED_NATIVE_ENTRIES)
                    "shader-compiler" -> verifyVariant(directory, version, "android", "aar", REQUIRED_NAGA_ANDROID_ENTRIES)
                    else -> emptyList()
                }
        }
        if (failures.isNotEmpty()) throw GradleException(failures.joinToString("\n"))
        val pinNote = pinnedCore?.let { ", Core pinned at $it" }.orEmpty()
        logger.lifecycle("Published artifacts verified ($version, ${directories.size} module(s)$pinNote)")
    }

    private fun verifyPinnedCore(directories: List<Path>, version: String, core: String, family: String): List<String> {
        val modules = directories.map { LocalModuleMetadata(it.resolve("${it.parent.fileName}-$version.module")) }
        // A walk that found no Core edge at all read nothing, which must not pass as a clean pin.
        if (modules.all { module -> module.edges.none { !it.constraint && !belongsToFamily(it.name, family) } }) {
            return listOf("No $family module metadata for $version names a Core module")
        }
        return modules.flatMap { pinnedCoreViolations(it, core, family, publishedModules.get(), ::relative) }
    }

    private fun verifyVariant(
        rootDirectory: Path,
        version: String,
        variant: String,
        extension: String,
        required: List<String>,
    ): List<String> {
        val artifact = rootDirectory.parent.fileName.toString()
        val directory = rootDirectory.parent.parent.resolve("$artifact-$variant").resolve(version)
        if (!Files.isDirectory(directory)) return listOf("${relative(directory)}: $variant publication is missing")
        val archive = Files.list(directory).use { files ->
            files.filter { it.fileName.toString() == "$artifact-$variant-$version.$extension" }.findFirst().orElse(null)
        } ?: return listOf("${relative(directory)}: $variant publication ${extension.uppercase()} is missing")
        val entries = ZipFile(archive.toFile()).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }
        return required.filterNot(entries::contains).map { "${relative(archive)}: missing $it" }
    }

    private fun isPlatformVariant(artifact: String): Boolean =
        artifact.matches(Regex(".*-(jvm|android|desktop|ios[A-Za-z0-9]*|wasm[A-Za-z0-9-]*|js|linux[A-Za-z0-9-]*|macos[A-Za-z0-9-]*|mingw[A-Za-z0-9-]*)"))

    private fun relative(path: Path): String = if (path.startsWith(project.rootDir.toPath())) {
        project.rootDir.toPath().relativize(path).toString()
    } else {
        path.toString()
    }

    companion object {
        private val REQUIRED_NATIVE_ENTRIES = listOf(
            "natives/macos-arm64/libawake-vulkan.dylib",
            "natives/linux-x86_64/libawake-vulkan.so",
        )

        // Every Vulkan shader is WGSL compiled at runtime; without these the Android renderer draws nothing.
        private val REQUIRED_NAGA_ANDROID_ENTRIES = listOf(
            "jni/arm64-v8a/libawake_naga.so",
            "jni/x86_64/libawake_naga.so",
        )
    }
}
