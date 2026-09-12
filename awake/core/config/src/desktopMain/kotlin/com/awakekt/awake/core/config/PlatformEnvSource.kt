/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.config

import java.io.File

actual fun defaultPlatformEnvSource(): EnvSource = CompositeEnvSource(
    DesktopSystemEnvSource(),
    DesktopSystemPropertyEnvSource(),
    DesktopLocalFileEnvSource(),
)

internal class DesktopSystemEnvSource : EnvSource {
    override fun get(key: String): String? {
        val direct = System.getenv(key)
        if (!direct.isNullOrEmpty()) return direct
        val uppercase = System.getenv(key.uppercase().replace('.', '_'))
        if (!uppercase.isNullOrEmpty()) return uppercase
        return null
    }
}

internal class DesktopSystemPropertyEnvSource : EnvSource {
    override fun get(key: String): String? {
        val direct = System.getProperty(key)
        if (!direct.isNullOrEmpty()) return direct
        val dotForm = System.getProperty(key.lowercase().replace('_', '.'))
        if (!dotForm.isNullOrEmpty()) return dotForm
        return null
    }
}

internal class DesktopLocalFileEnvSource : EnvSource {
    private val parsed: Map<String, String> by lazy {
        val candidates = listOf(
            File(".env"),
            File("studio.properties"),
            File("awake.properties"),
            File(System.getProperty("user.home"), ".awake/studio.env"),
            File(System.getProperty("user.home"), ".awake/awake.env"),
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile && candidate.canRead()) {
                try {
                    val content = candidate.readText()
                    return@lazy DotEnvParser.parse(content)
                } catch (_: Throwable) {
                    // Skip unreadable files gracefully
                }
            }
        }
        emptyMap()
    }

    override fun get(key: String): String? = parsed[key]
        ?: parsed[key.uppercase().replace('.', '_')]
        ?: parsed[key.lowercase().replace('_', '.')]
}
