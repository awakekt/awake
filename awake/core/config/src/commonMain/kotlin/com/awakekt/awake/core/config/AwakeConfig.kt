/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.config

/**
 * Typed facade over an [EnvSource] offering type-safe accessors for application settings.
 *
 * @property source The backing [EnvSource] providing raw string key-value pairs.
 */
class AwakeConfig(
    val source: EnvSource,
) {
    /**
     * Reads a string property, returning [default] if missing or blank.
     *
     * @param key The configuration property key.
     * @param default Fallback string value when [key] is absent or empty.
     * @return The configured string value, or [default].
     */
    fun getString(key: String, default: String = ""): String {
        val value = source.get(key)?.trim()
        return if (value.isNullOrEmpty()) default else value
    }

    /**
     * Reads an optional string property, returning null if missing or blank.
     *
     * @param key The configuration property key.
     * @return The configured string value, or null if absent or empty.
     */
    fun getStringOrNull(key: String): String? {
        val value = source.get(key)?.trim()
        return if (value.isNullOrEmpty()) null else value
    }

    /**
     * Reads a boolean property. Recognizes `true`, `1`, `yes`, `on` as true.
     *
     * @param key The configuration property key.
     * @param default Fallback boolean value when [key] is absent or unrecognized.
     * @return The parsed boolean value, or [default].
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
     *
     * @param key The configuration property key.
     * @param default Fallback integer value when [key] is absent or not a valid int.
     * @return The parsed integer, or [default].
     */
    fun getInt(key: String, default: Int = 0): Int {
        val raw = source.get(key)?.trim() ?: return default
        return raw.toIntOrNull() ?: default
    }

    /**
     * Reads a long integer property. Returns [default] if invalid or missing.
     *
     * @param key The configuration property key.
     * @param default Fallback long integer value when [key] is absent or not a valid long.
     * @return The parsed long value, or [default].
     */
    fun getLong(key: String, default: Long = 0L): Long {
        val raw = source.get(key)?.trim() ?: return default
        return raw.toLongOrNull() ?: default
    }

    /**
     * Reads an enum property by name (case-insensitive). Returns [default] if unmatched or missing.
     *
     * @param E The enum class type.
     * @param key The configuration property key.
     * @param default Fallback enum entry when [key] is absent or unrecognized.
     * @param entries Available enum constants to search against.
     * @return The matched enum constant, or [default].
     */
    inline fun <reified E : Enum<E>> getEnum(
        key: String,
        default: E,
        entries: Array<E> = enumValues(),
    ): E {
        val raw = source.get(key)?.trim()?.lowercase() ?: return default
        return entries.firstOrNull { it.name.lowercase() == raw } ?: default
    }

    /**
     * Factory methods and predefined configurations for [AwakeConfig].
     */
    companion object {
        /** An empty [AwakeConfig] instance containing no properties. */
        val Empty: AwakeConfig = AwakeConfig(MapEnvSource(emptyMap()))

        /**
         * Creates an [AwakeConfig] backed by the provided [map].
         *
         * @param map The map of key-value configuration pairs.
         * @return An [AwakeConfig] reading from [map].
         */
        fun fromMap(map: Map<String, String>): AwakeConfig = AwakeConfig(MapEnvSource(map))
    }
}
