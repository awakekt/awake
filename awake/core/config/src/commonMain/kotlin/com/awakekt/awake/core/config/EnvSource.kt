/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.config

/**
 * Provider interface for retrieving raw string configuration values by key.
 */
interface EnvSource {
    /**
     * Retrieves the string configuration value associated with [key], or null if absent.
     */
    fun get(key: String): String?
}

/**
 * In-memory [EnvSource] backed by a simple key-value map.
 * Ideal for unit tests, fixture setup, and explicit overrides.
 */
class MapEnvSource(
    private val values: Map<String, String> = emptyMap(),
) : EnvSource {
    override fun get(key: String): String? = values[key]
}

/**
 * Cascading [EnvSource] that queries an ordered list of sources until a match is found.
 */
class CompositeEnvSource(
    private val sources: List<EnvSource>,
) : EnvSource {
    constructor(vararg sources: EnvSource) : this(sources.toList())

    override fun get(key: String): String? {
        for (source in sources) {
            val value = source.get(key)
            if (value != null && value.isNotEmpty()) {
                return value
            }
        }
        return null
    }
}

/**
 * Multiplatform parser for `.env` and standard `.properties` text formats.
 */
object DotEnvParser {
    /**
     * Parses key-value pairs from standard `.env` or properties text.
     * Supports:
     * - `#` and `//` line comments
     * - Single (`'`) and double (`"`) quoted values with trimmed quotation marks
     * - Blank and whitespace lines
     */
    fun parse(content: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lines = content.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                continue
            }
            val eqIndex = line.indexOf('=')
            if (eqIndex > 0) {
                val key = line.substring(0, eqIndex).trim()
                var value = line.substring(eqIndex + 1).trim()
                if ((value.startsWith('"') && value.endsWith('"') && value.length >= 2) ||
                    (value.startsWith('\'') && value.endsWith('\'') && value.length >= 2)
                ) {
                    value = value.substring(1, value.length - 1)
                }
                if (key.isNotEmpty()) {
                    result[key] = value
                }
            }
        }
        return result
    }
}
