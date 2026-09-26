/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** Verifies the Maven-local files produced by a publication before Central upload. */
@DisableCachingByDefault(because = "Inspects the external Maven local repository and publication metadata")
abstract class VerifyPublishedArtifactsTask : DefaultTask() {
    @TaskAction
    fun verify() {
        val version = project.findProperty("awake.verifyPublishedVersion")?.toString()
            ?: project.version.toString()
        val family = project.findProperty("awake.verifyPublishedFamily")?.toString()?.lowercase()
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

        val failures = directories.flatMap { directory ->
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
        logger.lifecycle("Published artifacts verified ($version, ${directories.size} module(s))")
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

    private fun belongsToFamily(artifact: String, family: String): Boolean = when (family) {
        "vulkan" -> artifact == "vulkan" || artifact == "vulkan-kmp" || artifact == "vulkan-kmp-android-native"
        "core" -> artifact != "vulkan" && artifact != "vulkan-kmp" && artifact != "vulkan-kmp-android-native"
        else -> true
    }

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
