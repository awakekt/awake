/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.time.LocalDate

/** Cuts a Core release without requiring a Python runtime. */
@DisableCachingByDefault(because = "Creates release tags and updates the repository changelog")
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

        val root = project.rootProject.projectDir
        val version = nextReleaseVersion(channel, bump) { pattern -> latestTag(root, pattern) }
        val tag = "v$version"
        requireNewTag(root, tag)
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
        runGit(root, "add", "CHANGELOG.md")
        runGit(root, "commit", "-m", "chore(release): cut $tag")
        runGit(root, "tag", "-a", tag, "-m", "Release $tag")
        logger.lifecycle("Created $tag. Push with: git push origin main $tag")
    }
}

/**
 * The version the next cut on [channel] takes, counting on from the newest tag on that channel, or
 * from the newest tag of any channel when this is the channel's first cut.
 *
 * @param latestTag The newest tag matching a glob, or "" when none does.
 */
internal fun nextReleaseVersion(channel: String, bump: String?, latestTag: (pattern: String) -> String): String {
    val latest = latestTag("v[0-9]*-$channel.*").ifBlank { latestTag("v[0-9]*") }
    val parsed = parseTag(latest)
    val base = bumpBase(parsed.base, bump)
    val sequence = if (bump != null || channel != parsed.channel) 1 else parsed.sequence + 1
    return if (channel == "stable") base else "$base-$channel.$sequence"
}

/**
 * The newest tag in [dir] matching [pattern], or "" when none does.
 *
 * Every tag, not just those reachable from HEAD: a squash-merged release commit leaves its tag off
 * main, and `git describe` would then re-cut an existing version. A failed `git` fails the cut
 * rather than reading as "no tags", which would silently restart the channel at `.1`.
 */
internal fun latestTag(dir: File, pattern: String): String =
    runGit(dir, "tag", "--list", pattern, "--sort=-v:refname").lineSequence().firstOrNull().orEmpty()

/** Fails when [tag] already exists: a published version must never be cut twice. */
internal fun requireNewTag(dir: File, tag: String) {
    check(runGit(dir, "tag", "--list", tag).isEmpty()) {
        "$tag already exists. Fetch every tag (`git fetch --tags`) and cut again."
    }
}

/**
 * Runs `git [args]` in [dir] and returns its output; a non-zero exit fails with git's message.
 *
 * Git's repository-locating variables are cleared so [dir] alone picks the repository. A Gradle
 * daemon first started by a git hook (pre-push runs Gradle) keeps the hook's `GIT_DIR`, and a git it
 * spawns later for another checkout then fails with `fatal: not a git repository: ''`.
 */
internal fun runGit(dir: File, vararg args: String): String {
    val process = ProcessBuilder("git", *args).directory(dir)
        .apply { environment().keys.removeAll(GIT_LOCAL_ENV_VARS) }
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    val stderr = process.errorStream.bufferedReader().readText().trim()
    val exit = process.waitFor()
    check(exit == 0) { "git ${args.joinToString(" ")} failed ($exit) in $dir: $stderr" }
    return output
}

/** `git rev-parse --local-env-vars`: what git reads to locate a repository instead of the working directory. */
private val GIT_LOCAL_ENV_VARS = setOf(
    "GIT_ALTERNATE_OBJECT_DIRECTORIES", "GIT_CONFIG", "GIT_CONFIG_PARAMETERS", "GIT_CONFIG_COUNT",
    "GIT_OBJECT_DIRECTORY", "GIT_DIR", "GIT_WORK_TREE", "GIT_IMPLICIT_WORK_TREE", "GIT_GRAFT_FILE",
    "GIT_INDEX_FILE", "GIT_NO_REPLACE_OBJECTS", "GIT_REPLACE_REF_BASE", "GIT_PREFIX", "GIT_SHALLOW_FILE",
    "GIT_COMMON_DIR",
)

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
