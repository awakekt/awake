/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.config

/**
 * Typed facade over an [EnvSource] offering type-safe accessors for application settings.
 */
class AwakeConfig(
    val source: EnvSource,
) {
    /**
     * Reads a string property, returning [default] if missing or blank.
     */
    fun getString(key: String, default: String = ""): String {
        val value = source.get(key)?.trim()
        return if (value.isNullOrEmpty()) default else value
    }

    /**
     * Reads an optional string property, returning null if missing or blank.
     */
    fun getStringOrNull(key: String): String? {
        val value = source.get(key)?.trim()
        return if (value.isNullOrEmpty()) null else value
    }

    /**
     * Reads a boolean property. Recognizes `true`, `1`, `yes`, `on` as true.
     */
    fun getBoolean(key: String, default: Boolean = false): Boolean {
        val raw = source.get(key)?.trim()?.lowercase() ?: return default
        return when (raw) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> default
        }
    }

    /**
     * Reads an integer property. Returns [default] if invalid or missing.
     */
    fun getInt(key: String, default: Int = 0): Int {
        val raw = source.get(key)?.trim() ?: return default
        return raw.toIntOrNull() ?: default
    }

    /**
     * Reads a long integer property. Returns [default] if invalid or missing.
     */
    fun getLong(key: String, default: Long = 0L): Long {
        val raw = source.get(key)?.trim() ?: return default
        return raw.toLongOrNull() ?: default
    }

    /**
     * Reads an enum property by name (case-insensitive). Returns [default] if unmatched or missing.
     */
    inline fun <reified E : Enum<E>> getEnum(
        key: String,
        default: E,
        entries: Array<E> = enumValues(),
    ): E {
        val raw = source.get(key)?.trim()?.lowercase() ?: return default
        return entries.firstOrNull { it.name.lowercase() == raw } ?: default
    }

    companion object {
        val Empty: AwakeConfig = AwakeConfig(MapEnvSource(emptyMap()))

        fun fromMap(map: Map<String, String>): AwakeConfig = AwakeConfig(MapEnvSource(map))
    }
}
