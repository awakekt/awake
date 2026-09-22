/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.time.LocalDate

/** Cuts a Core release without requiring a Python runtime. */
abstract class ReleaseCutTask : DefaultTask() {
    @TaskAction
    fun cut() {
        val channel = project.findProperty("release.channel")?.toString() ?: "dev"
        val bump = project.findProperty("release.bump")?.toString()
        val dryRun = project.findProperty("release.dryRun")?.toString()?.toBoolean() ?: false
        require(channel in setOf("dev", "alpha", "beta", "rc", "stable")) {
            "release.channel must be dev, alpha, beta, rc, or stable"
        }
        require(bump == null || bump in setOf("patch", "minor", "major")) {
            "release.bump must be patch, minor, or major"
        }

        val latest = latestCoreTag(channel).ifBlank { latestCoreTag("") }
        val parsed = parseTag(latest)
        val base = bumpBase(parsed.base, bump)
        val sequence = if (bump != null || channel != parsed.channel) 1 else parsed.sequence + 1
        val version = if (channel == "stable") base else "$base-$channel.$sequence"
        val tag = "v$version"
        val changelog = project.rootProject.file("CHANGELOG.md").toPath()
        require(changelog.toFile().isFile) { "CHANGELOG.md is missing" }
        val content = changelog.toFile().readText()
        require("## [Unreleased]" in content) { "CHANGELOG.md has no ## [Unreleased] section" }
        val updated = content.replaceFirst(
            "## [Unreleased]",
            "## [Unreleased]\n\n## [$version] - ${LocalDate.now()}",
        )

        logger.lifecycle("${if (dryRun) "[DRY RUN] " else ""}Awake release: $version ($tag)")
        if (dryRun) {
            logger.lifecycle(updated.lineSequence().take(40).joinToString("\n"))
            return
        }
        changelog.toFile().writeText(updated)
        run("git", "add", "CHANGELOG.md")
        run("git", "commit", "-m", "chore(release): cut $tag")
        run("git", "tag", "-a", tag, "-m", "Release $tag")
        logger.lifecycle("Created $tag. Push with: git push origin main $tag")
    }

    private fun latestCoreTag(channel: String): String {
        val pattern = if (channel.isBlank()) "v[0-9]*" else "v[0-9]*-$channel.*"
        return run("git", "describe", "--tags", "--match", pattern, "--abbrev=0", allowFailure = true)
    }

    private fun run(vararg command: String, allowFailure: Boolean = false): String {
        val process = ProcessBuilder(*command).directory(project.rootProject.projectDir).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val stderr = process.errorStream.bufferedReader().readText().trim()
        val exit = process.waitFor()
        if (!allowFailure && exit != 0) error("${command.joinToString(" ")} failed: $stderr")
        return output
    }

    private data class Tag(val base: String, val channel: String, val sequence: Int)

    private fun parseTag(tag: String): Tag {
        val match = Regex("^v?(\\d+\\.\\d+\\.\\d+)(?:-([A-Za-z]+)\\.(\\d+))?$").matchEntire(tag)
        return if (match == null) Tag("0.1.0", "dev", 0) else Tag(
            match.groupValues[1], match.groupValues[2].ifBlank { "stable" }, match.groupValues[3].ifBlank { "0" }.toInt(),
        )
    }

    private fun bumpBase(base: String, bump: String?): String {
        if (bump == null) return base
        val parts = base.split('.').map(String::toInt).toMutableList()
        when (bump) {
            "major" -> { parts[0]++; parts[1] = 0; parts[2] = 0 }
            "minor" -> { parts[1]++; parts[2] = 0 }
            "patch" -> parts[2]++
        }
        return parts.joinToString(".")
    }
}
