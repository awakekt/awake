/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.createPlatformFileSystem

/** The longest namespace [platformContentCache] accepts. */
internal const val MAX_CONTENT_CACHE_NAMESPACE_LENGTH = 128

/** The folder, inside the namespace's folder, that a directory-backed default cache keeps its files in. */
internal const val CONTENT_CACHE_DIRECTORY = "awake-content"

// Names Windows reserves for devices in every folder, with or without an extension.
private val WINDOWS_DEVICE_NAMES = setOf("CON", "PRN", "AUX", "NUL") +
    (1..9).flatMap { listOf("COM$it", "LPT$it") }

/**
 * The platform's default [ContentCache] for [IndexedAssetSource.open], kept where the platform keeps
 * an app's disposable data, so that a second run of the app reads the files it already has instead of
 * downloading them again.
 *
 * | Platform | Where | Value |
 * | --- | --- | --- |
 * | Web (wasmJs) | the origin's IndexedDB | [IndexedDbContentCache] with [namespace] |
 * | Desktop, Linux | `$XDG_CACHE_HOME/<namespace>/awake-content`, or `~/.cache/<namespace>/awake-content` when `XDG_CACHE_HOME` is unset or not absolute | [FileSystemContentCache] |
 * | Desktop, macOS | `~/Library/Caches/<namespace>/awake-content` | [FileSystemContentCache] |
 * | Desktop, Windows | `%LOCALAPPDATA%\<namespace>\awake-content` | [FileSystemContentCache] |
 * | iOS | the app's `NSCachesDirectory`, then `<namespace>/awake-content` | [FileSystemContentCache] |
 * | Android | none: this module reaches no `Context`; call the Android `platformContentCache(namespace, context.cacheDir)` overload | null |
 *
 * [namespace] names the app or project the files belong to, such as `"harbor-town"` or
 * `"com.example.harbortown"`. Choose one that stays the same across releases: a new name starts an
 * empty cache. On desktop it is the folder name inside the user's cache folder that every app shares,
 * so make it specific to your app. It must be one safe folder name: 1 to 128 ASCII letters, digits,
 * `.`, `_` or `-`, starting with a letter or digit, not ending with `.`, and not a name Windows keeps
 * for a device (`CON`, `NUL`, `COM1` and so on).
 *
 * The cache never fails a load. A read the storage refuses is a miss, so the file comes from the
 * network, and a file it can't keep, because the disk or the browser's quota is full, is dropped,
 * costing only the next run a download. When the cache folder can't be found or created at all, this
 * returns null, which [IndexedAssetSource.open] takes as no cache.
 *
 * Files are kept by content and never expire, so open with `retainCachedOnly = true` (the default),
 * or call [ContentCache.retainOnly] with each release's index.
 *
 * @param namespace A name for the app or project, unique to it and stable across releases.
 * @return The platform's default cache, or null where the platform has none or refuses its folder.
 * @throws IllegalArgumentException When [namespace] is not one safe folder name.
 */
fun platformContentCache(namespace: String): ContentCache? {
    requireContentCacheNamespace(namespace)
    return createPlatformContentCache(namespace)
}

/** The platform half of [platformContentCache], given a [namespace] it has already checked. */
internal expect fun createPlatformContentCache(namespace: String): ContentCache?

/**
 * Checks that [namespace] is one folder name that means the same thing on every file system: no
 * separator, no `.` or `..`, no hidden or trailing-dot name, no Windows device name.
 *
 * @throws IllegalArgumentException When it isn't.
 */
internal fun requireContentCacheNamespace(namespace: String) {
    require(namespace.length in 1..MAX_CONTENT_CACHE_NAMESPACE_LENGTH) {
        "A content cache namespace has 1 to $MAX_CONTENT_CACHE_NAMESPACE_LENGTH characters, not ${namespace.length}."
    }
    require(namespace.all { it.isSafeNamespaceCharacter() }) {
        "A content cache namespace is one folder name of ASCII letters, digits, '.', '_' and '-', not \"$namespace\"."
    }
    require(namespace.first().isAsciiLetterOrDigit()) {
        "A content cache namespace starts with a letter or digit, not \"$namespace\"."
    }
    require(!namespace.endsWith('.')) {
        "A content cache namespace doesn't end with '.', which Windows drops from folder names: \"$namespace\"."
    }
    require(namespace.substringBefore('.').uppercase() !in WINDOWS_DEVICE_NAMES) {
        "A content cache namespace can't be a name Windows keeps for a device: \"$namespace\"."
    }
}

/**
 * A [FileSystemContentCache] in `<baseDirectory>/<namespace>/awake-content`, or null when that folder
 * can't be created, such as under a read-only or missing home folder.
 */
internal fun directoryContentCache(baseDirectory: String, namespace: String): ContentCache? =
    runCatching {
        FileSystemContentCache(createPlatformFileSystem("${baseDirectory.trimEnd('/', '\\')}/$namespace/$CONTENT_CACHE_DIRECTORY"))
    }.getOrNull()

private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

private fun Char.isSafeNamespaceCharacter(): Boolean = isAsciiLetterOrDigit() || this == '.' || this == '_' || this == '-'
