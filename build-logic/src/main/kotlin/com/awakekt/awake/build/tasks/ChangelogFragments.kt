/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import java.io.File

/**
 * Keep a Changelog's section order. A changelog fragment's directory names its section:
 * `changelog/unreleased/fixed/<branch>.md` is a Fixed entry.
 *
 * Fragments exist because every PR used to insert its bullet at the same line under
 * `## [Unreleased]`, so any two open PRs conflicted there, and a PR merged after a release cut
 * landed its bullet under the version it missed. One file per change conflicts with nothing, and
 * the cut files each one under the release that actually ships it.
 */
internal val CHANGELOG_SECTIONS = listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security")

/**
 * The fragment files under [dir] (`changelog/unreleased`), by section, each section's in file-name
 * order so a cut is repeatable. Anything else there but its README fails: a misnamed section
 * directory would otherwise drop its entries from the release without a word.
 */
internal fun readChangelogFragments(dir: File): Map<String, List<File>> {
    val known = CHANGELOG_SECTIONS.associateBy { it.lowercase() }
    dir.listFiles().orEmpty().forEach { entry ->
        check(entry.name == "README.md" || (entry.isDirectory && entry.name in known)) {
            "changelog/unreleased/${entry.name} is not a changelog section; use one of ${known.keys.joinToString()}"
        }
    }
    return known.mapNotNull { (directory, section) ->
        val files = File(dir, directory).listFiles { file -> file.isFile && file.extension == "md" }.orEmpty()
        if (files.isEmpty()) null else section to files.sortedBy { it.name }
    }.toMap()
}

/**
 * [changelog] with its `## [Unreleased]` notes, plus [fragments] (section to entry texts), moved
 * into a new `## [version] - date` section, leaving an empty Unreleased above it.
 *
 * Handwritten entries already under Unreleased keep their sections and come first, so a changelog
 * mid-way between the two styles still cuts cleanly.
 */
internal fun promoteUnreleased(changelog: String, version: String, date: String, fragments: Map<String, List<String>>): String {
    val start = changelog.indexOf(UNRELEASED)
    require(start >= 0) { "CHANGELOG.md has no $UNRELEASED section" }
    val bodyStart = start + UNRELEASED.length
    val next = changelog.indexOf("\n## [", bodyStart).let { if (it < 0) changelog.length else it + 1 }

    val preface = mutableListOf<String>()
    val sections = LinkedHashMap<String, MutableList<String>>()
    var current: MutableList<String>? = null
    for (line in changelog.substring(bodyStart, next).lines()) {
        when {
            line.startsWith("### ") -> current = sections.getOrPut(line.removePrefix("### ").trim()) { mutableListOf() }
            line.isBlank() -> Unit
            else -> (current ?: preface) += line
        }
    }
    fragments.forEach { (section, entries) -> sections.getOrPut(section) { mutableListOf() } += entries.map(String::trim) }

    val order = CHANGELOG_SECTIONS + (sections.keys - CHANGELOG_SECTIONS.toSet())
    val release = buildString {
        append("## [").append(version).append("] - ").append(date).append('\n')
        if (preface.isNotEmpty()) append('\n').append(preface.joinToString("\n")).append('\n')
        order.forEach { section ->
            val entries = sections[section].orEmpty().filter(String::isNotBlank)
            if (entries.isNotEmpty()) append("\n### ").append(section).append("\n\n").append(entries.joinToString("\n")).append('\n')
        }
    }
    val rest = changelog.substring(next)
    return changelog.substring(0, start) + UNRELEASED + "\n\n" + release + (if (rest.isEmpty()) "" else "\n" + rest)
}

private const val UNRELEASED = "## [Unreleased]"
