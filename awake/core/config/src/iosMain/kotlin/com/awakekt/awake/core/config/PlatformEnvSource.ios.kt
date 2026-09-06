/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.config

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
internal class IosSystemEnvSource : EnvSource {
    override fun get(key: String): String? {
        val direct = getenv(key)?.toKString()
        if (!direct.isNullOrEmpty()) return direct
        val uppercase = getenv(key.uppercase().replace('.', '_'))?.toKString()
        if (!uppercase.isNullOrEmpty()) return uppercase
        return null
    }
}

actual fun defaultPlatformEnvSource(): EnvSource = IosSystemEnvSource()
