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
                if (directory.fileName.toString() == "vulkan-kmp") verifyDesktopVariant(directory, version) else emptyList()
        }
        if (failures.isNotEmpty()) throw GradleException(failures.joinToString("\n"))
        logger.lifecycle("Published artifacts verified ($version, ${directories.size} module(s))")
    }

    private fun verifyDesktopVariant(rootDirectory: Path, version: String): List<String> {
        val desktop = rootDirectory.parent.resolve("vulkan-kmp-desktop").resolve(version)
        val jar = Files.list(desktop).use { files ->
            files.filter { it.fileName.toString().endsWith(".jar") && it.fileName.toString().contains("-desktop-") }
                .filter { !it.fileName.toString().endsWith("-sources.jar") && !it.fileName.toString().endsWith("-javadoc.jar") }
                .findFirst().orElse(null)
        } ?: return listOf("${relative(desktop)}: desktop publication JAR is missing")
        val entries = ZipFile(jar.toFile()).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }
        return REQUIRED_NATIVE_ENTRIES.filterNot(entries::contains).map { "${relative(jar)}: missing $it" }
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
    }
}
