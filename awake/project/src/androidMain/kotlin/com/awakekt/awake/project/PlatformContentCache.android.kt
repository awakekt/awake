/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import java.io.File

/**
 * Null: an app's cache folder comes from its `Context`, which this module never reaches. Android
 * code calls `platformContentCache(namespace, context.cacheDir)` instead.
 */
internal actual fun createPlatformContentCache(namespace: String): ContentCache? = null

/**
 * The Android default [ContentCache] for [IndexedAssetSource.open]: a [FileSystemContentCache] in
 * `<cacheDir>/<namespace>/awake-content`. Pass the app's `Context.cacheDir`, which only the app
 * reaches, so this module stays free of an Android `Context`:
 *
 * ```kotlin
 * val cache = platformContentCache("harbor-town", context.cacheDir)
 * ```
 *
 * The system may empty `cacheDir` when the device runs short of space; the next run then downloads
 * again. Like the other platforms' defaults, the cache never fails a load: a read the storage refuses
 * is a miss, a file it can't keep is dropped, and a folder that can't be created gives null, which
 * [IndexedAssetSource.open] takes as no cache. [namespace] follows the rules of the common
 * [platformContentCache].
 *
 * @param namespace A name for the app or project, unique to it and stable across releases.
 * @param cacheDir The app's `Context.cacheDir`, or another folder the app owns.
 * @return The cache, or null when its folder can't be created.
 * @throws IllegalArgumentException When [namespace] is not one safe folder name.
 */
fun platformContentCache(namespace: String, cacheDir: File): ContentCache? {
    requireContentCacheNamespace(namespace)
    return directoryContentCache(cacheDir.path, namespace)
}
