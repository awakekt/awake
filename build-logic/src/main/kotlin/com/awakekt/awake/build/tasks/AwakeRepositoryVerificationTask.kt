/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path

/** Repository gates that belong to Awake's product build rather than an agent installation. */
abstract class AwakeRepositoryVerificationTask : DefaultTask() {
    @TaskAction
    fun verify() {
        val failures = mutableListOf<String>()
        check("documentation") { documentationFailures() }.also(failures::addAll)
        check("Maven coordinates") { mavenCoordinateFailures() }.also(failures::addAll)
        check("capability boundaries") { capabilityBoundaryFailures() }.also(failures::addAll)
        check("agent runtime boundary") { agentRuntimeFailures() }.also(failures::addAll)
        check("maintained Python ownership") { pythonOwnershipFailures() }.also(failures::addAll)
        check("source headers and provenance") { sourceMetadataFailures() }.also(failures::addAll)
        check("agent lock schema") { agentLockFailures() }.also(failures::addAll)
        check("detekt baselines") { detektBaselineFailures() }.also(failures::addAll)
        check("publication closure") { publicationClosureFailures() }.also(failures::addAll)
        check("active UI documentation") { uiDocumentationFailures() }.also(failures::addAll)
        check("generated outputs") { generatedOutputFailures() }.also(failures::addAll)
        check("PR governance") { pullRequestGovernanceFailures() }.also(failures::addAll)

        if (failures.isNotEmpty()) {
            throw GradleException(
                "Awake repository verification failed (${failures.size} problem(s)):\n" +
                    failures.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("Awake repository verification passed")
    }

    private fun check(name: String, block: () -> List<String>): List<String> = try {
        block().also { result ->
            if (result.isEmpty()) logger.lifecycle("  OK: $name")
        }
    } catch (error: Exception) {
        listOf("$name: ${error.message ?: error::class.simpleName}")
    }

    private val root: Path get() = project.rootDir.toPath()

    private fun trackedFiles(): Sequence<Path> {
        val output = ProcessBuilder("git", "ls-files", "-z")
            .directory(project.rootDir)
            .start()
            .inputStream
            .readBytes()
        return output.toString(Charsets.UTF_8)
            .split('\u0000')
            .asSequence()
            .filter(String::isNotBlank)
            .map(root::resolve)
            .filter(Files::isRegularFile)
    }

    private fun documentationFailures(): List<String> {
        val modules = listOf(
            "awake/core", "awake/core/geometry", "awake/core/animation", "awake/asset/gltf",
            "awake/asset/mesh-optimizer", "awake/asset/shaders", "awake/ecs", "awake/scene",
            "awake/scene/authoring", "awake/scene/scene3d", "awake/engine/render/contract",
            "awake/engine/render/passes", "awake/ui", "awake/ui/shadcn", "awake/core/text",
            "awake/engine/platform", "awake/engine/bootstrap", "awake/backend/vulkan",
            "awake/backend/vulkan/bindings", "awake/backend/webgpu", "awake/physics/api",
            "awake/backend/jolt",
        )
        val failures = modules.mapNotNull { module ->
            val readme = root.resolve(module).resolve("README.md")
            if (!Files.isRegularFile(readme) || Files.readString(readme).trim().length < 50) {
                "$module/README.md is missing or too short"
            } else null
        }.toMutableList()
        listOf("docs/reference/performance-matrix.md" to 100, "docs/ecs-benchmark-scorecard.md" to 1)
            .forEach { (relative, minimum) ->
                val path = root.resolve(relative)
                if (!Files.isRegularFile(path) || Files.readString(path).trim().length < minimum) {
                    failures += "$relative is missing or too short"
                }
            }
        return failures
    }

    private fun mavenCoordinateFailures(): List<String> {
        val coordinates = mutableSetOf<String>()
        Files.walk(root.resolve("awake")).use { paths ->
            paths.filter { it.fileName.toString() == "build.gradle.kts" }.forEach { buildFile ->
                val text = Files.readString(buildFile)
                if (!text.contains("com.awakekt.awake.plugin.publish") &&
                    !text.contains("com.vanniktech.maven.publish")
                ) return@forEach
                val override = Regex("coordinates\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"").find(text)
                if (override != null) {
                    coordinates += "${override.groupValues[1]}:${override.groupValues[2]}"
                } else {
                    val module = buildFile.parent
                    val relative = root.relativize(module).toString().replace('\\', '/').split('/')
                    val groupSuffix = relative.dropWhile { it != "awake" }.drop(1).dropLast(1)
                    val group = "com.awakekt.awake" + if (groupSuffix.isEmpty()) "" else ".${groupSuffix.joinToString(".")}"
                    coordinates += "$group:${module.fileName}"
                }
            }
        }
        val versions = mutableSetOf(project.version.toString(), "<version>", "\${version}", "VERSION", "x.y.z")
        val tags = ProcessBuilder("git", "tag", "--list", "v[0-9]*")
            .directory(project.rootProject.projectDir).start()
        versions += tags.inputStream.bufferedReader().readLines().map { it.removePrefix("v") }
        tags.waitFor()
        val pattern = Regex("com\\.awakekt\\.awake:([A-Za-z0-9_.-]+)(?::([^\\s\\\"`)'<>]+))?")
        val failures = mutableListOf<String>()
        trackedFiles().filter { it.fileName.toString().endsWith(".md") }.forEach { path ->
            val relative = relative(path)
            if (relative.startsWith("docs/audits/") || relative.startsWith("docs/handoffs/") ||
                relative.startsWith("docs/tasks/archive/") || relative.startsWith("docs/archive/") ||
                relative.startsWith("docs/release-notes-")
            ) return@forEach
            pattern.findAll(Files.readString(path)).forEach { match ->
                val coordinate = "com.awakekt.awake:${match.groupValues[1]}"
                val version = match.groupValues[2]
                if (coordinate !in coordinates) failures += "$relative: unknown publication $coordinate"
                if (version.isNotBlank() && version !in versions && !version.endsWith("-SNAPSHOT")) {
                    failures += "$relative: version '$version' is not a known release or placeholder"
                }
            }
        }
        return failures.distinct()
    }

    private fun capabilityBoundaryFailures(): List<String> {
        val failures = mutableListOf<String>()
        trackedFiles().filter { it.toString().contains("src/commonMain") && it.toString().endsWith(".kt") }
            .forEach { path ->
                Files.readAllLines(path).forEachIndexed { index, line ->
                    if (PLATFORM_IMPORTS.any(line.trim()::startsWith)) {
                        failures += "${relative(path)}:${index + 1}: platform import in commonMain"
                    }
                }
            }
        trackedFiles().filter { path ->
            path.startsWith(root.resolve("awake")) &&
                path.fileName.toString().let { name ->
                    name.endsWith(".kt") || name.endsWith(".kts") || name.endsWith(".gradle") || name.endsWith(".toml")
                } &&
                !relative(path).contains("/build/") &&
                !relative(path).contains("/src/test") &&
                !relative(path).contains("/src/commonTest") &&
                !relative(path).contains("/src/desktopTest")
        }.forEach { path ->
            val text = Files.readString(path)
            if (PRO_BOUNDARY_TOKENS.any(text::contains) || LEGACY_READER_TOKENS.any(text::contains)) {
                failures += "${relative(path)}: references Studio/Pro-only APIs"
            }
        }
        return failures.distinct()
    }

    private fun agentRuntimeFailures(): List<String> {
        val roots = listOf(".github", "build-logic", "scripts", "tools", "hooks")
        return roots.flatMap { name ->
            val directory = root.resolve(name)
            if (!Files.exists(directory)) emptyList() else Files.walk(directory).use { paths ->
                paths.filter { path ->
                    Files.isRegularFile(path) && path.fileName.toString().let { name ->
                        name.endsWith(".py") || name.endsWith(".sh") || name.endsWith(".kts") ||
                            name.endsWith(".yml") || name.endsWith(".yaml")
                    }
                }.toList().flatMap { path ->
                    if (relative(path) == "hooks/block-edit-vendored-skills.sh") return@flatMap emptyList()
                    Files.readAllLines(path).mapIndexedNotNull { index, line ->
                        if (line.contains(".agents/")) "${relative(path)}:${index + 1}: product tooling references .agents" else null
                    }
                }
            }
        }
    }

    private fun pythonOwnershipFailures(): List<String> {
        val allowed = listOf(
            "tools/shadcn/", "tools/icons/", "tools/fonts-tooling/", "tools/jni-binding-generator/", "website/",
        )
        return trackedFiles().filter { it.toString().endsWith(".py") }.mapNotNull { path ->
            val relative = relative(path)
            if (allowed.any(relative::startsWith)) null else
                "$relative: maintained Python is limited to visual tooling or vendored generators"
        }.toList()
    }

    private fun sourceMetadataFailures(): List<String> {
        val failures = mutableListOf<String>()
        trackedFiles().filter { path ->
            path.fileName.toString().let { name ->
                name.endsWith(".kt") || name.endsWith(".kts") || name.endsWith(".sh")
            }
        }.filterNot { relative(it).startsWith("build/") || relative(it).startsWith(".gradle/") }
            .forEach { path ->
                val lines = Files.readAllLines(path).take(24)
                if (lines.none { it.contains("SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz") }) {
                    failures += "${relative(path)}: missing SPDX copyright header"
                }
                if (lines.none { it.contains("SPDX-License-Identifier: Apache-2.0") }) {
                    failures += "${relative(path)}: missing Apache-2.0 SPDX header"
                }
            }

        val provenance = root.resolve("docs/reference/source-provenance.json")
        if (!Files.isRegularFile(provenance)) {
            failures += "docs/reference/source-provenance.json is missing"
        } else {
            val text = Files.readString(provenance)
            if (!Regex("\\\"version\\\"\\s*:\\s*1").containsMatchIn(text) || !text.contains("\"entries\"")) {
                failures += "docs/reference/source-provenance.json has an invalid schema"
            }
        }
        return failures
    }

    private fun agentLockFailures(): List<String> {
        val lock = root.resolve(".agents/skills.lock.toml")
        if (!Files.isRegularFile(lock)) return emptyList()
        val text = Files.readString(lock)
        val failures = mutableListOf<String>()
        if (!Regex("(?m)^version\\s*=\\s*1\\s*$").containsMatchIn(text)) {
            failures += ".agents/skills.lock.toml: version = 1 is required"
        }
        val sources = text.split("[[source]]").drop(1)
        if (sources.isEmpty()) failures += ".agents/skills.lock.toml: at least one source is required"
        sources.forEachIndexed { index, source ->
            val prefix = ".agents/skills.lock.toml source #${index + 1}"
            val required = listOf("id", "kind", "source", "tag", "commit", "archive_sha256", "license", "skill_root", "skills", "skills_target")
            required.filterNot { Regex("(?m)^$it\\s*=").containsMatchIn(source) }
                .forEach { failures += "$prefix: missing $it" }
            val kind = Regex("(?m)^kind\\s*=\\s*[\"']([^\"']+)[\"']").find(source)?.groupValues?.get(1)
            if (kind !in setOf("vendor", "maintained-core")) {
                failures += "$prefix: public lockfile allows only vendor or maintained-core sources"
            }
            val target = Regex("(?m)^skills_target\\s*=\\s*[\"']([^\"']+)[\"']").find(source)?.groupValues?.get(1)
            if (target != ".agents/skills") failures += "$prefix: invalid skill deployment target"
            val commit = Regex("(?m)^commit\\s*=\\s*[\"']([^\"']+)[\"']").find(source)?.groupValues?.get(1)
            if (commit == null || !commit.matches(Regex("[0-9a-fA-F]{40}"))) {
                failures += "$prefix: source commit must be a full SHA"
            }
            val digest = Regex("(?m)^archive_sha256\\s*=\\s*[\"']([^\"']+)[\"']").find(source)?.groupValues?.get(1)
            if (digest == null || !digest.matches(Regex("[0-9a-fA-F]{64}"))) {
                failures += "$prefix: archive_sha256 must be a SHA-256 digest"
            }
            if (Regex("(?m)^commands\\s*=").containsMatchIn(source) &&
                !Regex("(?m)^commands_target\\s*=\\s*[\"']\\.agents/commands[\"']").containsMatchIn(source)
            ) failures += "$prefix: invalid command deployment target"
        }
        return failures
    }

    private fun detektBaselineFailures(): List<String> {
        val failures = mutableListOf<String>()
        Files.walk(root).use { paths ->
            paths.filter { path ->
                Files.isRegularFile(path) && path.fileName.toString() == "detekt-baseline.xml" &&
                    !relative(path).contains("/build/") && !relative(path).contains("/.gradle/")
            }.forEach { baseline ->
                val sourceRoot = baseline.parent.resolve("src")
                if (!Files.isDirectory(sourceRoot)) return@forEach
                val sourceNames = Files.walk(sourceRoot).use { sourcePaths ->
                    sourcePaths.filter { it.fileName.toString().endsWith(".kt") }
                        .map { it.fileName.toString() }.toList().toSet()
                }
                Regex("""[^:]+:([^:]+\\.kts?)${'$'}""").findAll(Files.readString(baseline)).forEach { match ->
                    if (match.groupValues[1] !in sourceNames) {
                        failures += "${relative(baseline)}: stale entry ${match.groupValues[1]}"
                    }
                }
            }
        }
        return failures
    }

    private fun publicationClosureFailures(): List<String> {
        val published = project.subprojects.filter(::isPublished).map { it.path }.toSet()
        val failures = mutableListOf<String>()
        project.subprojects.forEach { source ->
            source.configurations.forEach { configuration ->
                if (!configuration.name.contains("Main") && !configuration.name.contains("Implementation")) return@forEach
                configuration.dependencies.withType(ProjectDependency::class.java).forEach { dependency ->
                    val target = dependency.path
                    if (source.path in published && target !in published) {
                        failures += "${source.path} -> $target (${configuration.name}) is unpublished"
                    }
                }
            }
        }
        return failures.distinct()
    }

    private fun isPublished(candidate: Project): Boolean =
        candidate.plugins.hasPlugin("com.awakekt.awake.plugin.publish") ||
            candidate.plugins.hasPlugin("com.vanniktech.maven.publish")

    private fun uiDocumentationFailures(): List<String> {
        val forbidden = listOf(
            "awake:ui:ui-core", "awake:ui:headless", "awake:ui:testing", "awake:engine:ui-dsl",
            "awake:engine:ui:ui-core", "awake:engine:ui:ui-headless", "awake:engine:ui:ui-shadcn",
            "awake:engine:ui:ui-testing",
        )
        val docs = sequenceOf(root.resolve("docs/README.md"), root.resolve("docs/architecture.md"), root.resolve("docs/mvp-plan.md")) +
            Files.list(root.resolve("docs/reference")).use { it.filter { path -> path.toString().endsWith(".md") }.toList().asSequence() }
        return docs.flatMap { path ->
            if (!Files.isRegularFile(path)) emptySequence() else Files.readAllLines(path).asSequence().mapIndexedNotNull { index, line ->
                forbidden.firstOrNull(line::contains)?.let { token -> "${relative(path)}:${index + 1}: retired module $token" }
            }
        }.toList()
    }

    private fun generatedOutputFailures(): List<String> {
        val outputs = listOf(
            "awake/tailwind/src/commonMain/kotlin/com/awakekt/awake/tailwind/Tw.kt",
            "awake/core/text/src/commonMain/kotlin/com/awakekt/awake/core/text/font/RobotoRegularUiFontData.kt",
        )
        return outputs.filter { !Files.isRegularFile(root.resolve(it)) }.map { "$it is missing" }
    }

    private fun pullRequestGovernanceFailures(): List<String> {
        if (System.getenv("GITHUB_EVENT_NAME") != "pull_request") return emptyList()
        val eventPath = System.getenv("GITHUB_EVENT_PATH")?.let { root.resolve(it) }
            ?: return listOf("GITHUB_EVENT_PATH is missing")
        if (!Files.isRegularFile(eventPath)) return listOf("GITHUB_EVENT_PATH does not exist")
        val number = Regex("\\\"number\\\"\\s*:\\s*(\\d+)").find(Files.readString(eventPath))?.groupValues?.get(1)
            ?: return listOf("pull request number is missing from the GitHub event")
        val process = ProcessBuilder("gh", "pr", "view", number, "--json", "title,milestone")
            .directory(project.rootProject.projectDir)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        if (process.waitFor() != 0) return listOf("gh pr view failed: $stderr")
        val failures = mutableListOf<String>()
        if (output.contains("\"milestone\":null")) failures += "PR #$number has no milestone"
        val title = Regex("\\\"title\\\"\\s*:\\s*\\\"([^\"]+)").find(output)?.groupValues?.get(1).orEmpty()
        if (title.matches(Regex("(?i)^(feat|fix)(\\([^)]*\\))?:.*"))) {
            val changed = ProcessBuilder("git", "diff", "origin/main...HEAD", "--name-only")
                .directory(project.rootProject.projectDir).start()
            val files = changed.inputStream.bufferedReader().readText()
            changed.waitFor()
            if (!files.lineSequence().any { it == "CHANGELOG.md" }) failures += "feat/fix PR must update CHANGELOG.md"
        }
        return failures
    }

    private fun relative(path: Path): String = root.relativize(path).toString().replace('\\', '/')

    companion object {
        private val PLATFORM_IMPORTS = listOf("import platform.", "import kotlinx.cinterop")
        private val PRO_BOUNDARY_TOKENS = listOf(
            "awake-pro", "com.awakekt.awake.pro.", "com.awakekt.awake.studio.", "awake-pro-core-io",
        )
        private val LEGACY_READER_TOKENS = listOf("assetBytesReader", "externalResourceReader")
    }
}
