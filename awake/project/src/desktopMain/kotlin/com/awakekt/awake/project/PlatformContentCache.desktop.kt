/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import java.io.File

/** A [FileSystemContentCache] under the user's cache folder; see [desktopCacheDirectory]. */
internal actual fun createPlatformContentCache(namespace: String): ContentCache? =
    desktopContentCache(
        namespace = namespace,
        osName = System.getProperty("os.name").orEmpty(),
        userHome = System.getProperty("user.home"),
        environment = System::getenv,
    )

/**
 * [createPlatformContentCache] with the system it reads passed in, so a test can stand in for any
 * operating system.
 */
internal fun desktopContentCache(
    namespace: String,
    osName: String,
    userHome: String?,
    environment: (String) -> String?,
): ContentCache? = desktopCacheDirectory(osName, userHome, environment)?.let { directoryContentCache(it, namespace) }

/**
 * The folder the operating system gives a user's disposable app data, or null when it can't be told:
 *
 * - Windows: `%LOCALAPPDATA%`, else `<home>\AppData\Local`.
 * - macOS: `<home>/Library/Caches`.
 * - Linux and other systems: `$XDG_CACHE_HOME` when it is set to an absolute path (the XDG Base
 *   Directory specification says to ignore a relative one), else `<home>/.cache`.
 *
 * @param osName The `os.name` system property.
 * @param userHome The `user.home` system property.
 * @param environment Reads one environment variable, null when unset.
 */
internal fun desktopCacheDirectory(osName: String, userHome: String?, environment: (String) -> String?): String? {
    val home = userHome?.takeIf { it.isNotBlank() }
    return when {
        osName.startsWith("Windows", ignoreCase = true) ->
            environment("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                ?: home?.let { File(File(it, "AppData"), "Local").path }
        osName.startsWith("Mac", ignoreCase = true) || osName.startsWith("Darwin", ignoreCase = true) ->
            home?.let { File(File(it, "Library"), "Caches").path }
        else ->
            environment("XDG_CACHE_HOME")?.takeIf { it.startsWith('/') }
                ?: home?.let { File(it, ".cache").path }
    }
}
