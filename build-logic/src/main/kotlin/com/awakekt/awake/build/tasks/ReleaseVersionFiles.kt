/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import java.io.File

// Optional version sources and targets for `releaseCut` in a repository that keeps its version in
// files rather than only in tags. Core passes none of them and keeps cutting from tags alone.

/**
 * The value of [key] in a `.properties` file's text, or null when the key is absent.
 *
 * With `-Prelease.versionProperty=game.version`, this is the repository's current version: the cut
 * counts on from it as it would from the newest tag, and writes the new version back.
 */
internal fun readVersionProperty(properties: String, key: String): String? =
    propertyLine(key).find(properties)?.groupValues?.get(2)?.trim()

/** [properties] with [key] set to [version]; fails when the key is absent, so nothing is half-cut. */
internal fun writeVersionProperty(properties: String, key: String, version: String): String {
    val match = requireNotNull(propertyLine(key).find(properties)) { "No '$key' entry to update." }
    return properties.replaceRange(match.groups[2]!!.range, version)
}

/**
 * [text] with its first version field set to [version]: a JSON `"version": "..."` member, or a
 * `version = ...` line (properties or TOML, quoted or not). Fails when there is none, rather than
 * leaving a listed file on the old version.
 */
internal fun writeVersionField(text: String, version: String, name: String): String {
    val match = requireNotNull(JSON_VERSION.find(text) ?: LINE_VERSION.find(text)) {
        "$name has no \"version\" field to update."
    }
    return text.replaceRange(match.groups[1]!!.range, version)
}

/** Rewrites the version field of each file in [paths] (comma-separated, relative to [root]). */
internal fun writeVersionFiles(root: File, paths: String, version: String): List<File> =
    paths.split(',').map(String::trim).filter(String::isNotEmpty).map { path ->
        val file = File(root, path)
        require(file.isFile) { "release.versionFiles lists $path, which does not exist." }
        file.writeText(writeVersionField(file.readText(), version, path))
        file
    }

private fun propertyLine(key: String) = Regex("(?m)^(\\s*${Regex.escape(key)}\\s*[=:]\\s*)(.*?)\\s*$")

private val JSON_VERSION = Regex("\"version\"\\s*:\\s*\"([^\"]*)\"")
private val LINE_VERSION = Regex("(?m)^\\s*version\\s*[=:]\\s*\"?([^\"\\s]+)\"?\\s*$")
