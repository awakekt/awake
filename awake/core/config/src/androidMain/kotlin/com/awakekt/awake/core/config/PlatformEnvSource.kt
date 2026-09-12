/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.config

internal class AndroidSystemEnvSource : EnvSource {
    override fun get(key: String): String? {
        val direct = System.getenv(key)
        if (!direct.isNullOrEmpty()) return direct
        val uppercase = System.getenv(key.uppercase().replace('.', '_'))
        if (!uppercase.isNullOrEmpty()) return uppercase
        return null
    }
}

actual fun defaultPlatformEnvSource(): EnvSource = AndroidSystemEnvSource()
