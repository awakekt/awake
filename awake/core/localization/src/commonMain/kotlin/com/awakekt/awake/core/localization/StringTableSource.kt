/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A string table that is not a JSON object of strings. The message names the key at fault. */
class StringTableException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * Where a [Strings] gets one locale's table of text by key.
 *
 * A source is asked for a locale only when that locale is used, so a project with many languages
 * reads and parses only the active one and the default.
 */
fun interface StringTableSource {
    /**
     * Loads the table for exactly [locale], without falling back to another.
     *
     * @param locale The locale, such as `ko` or `pt-BR`.
     * @return The text by key, or null when the project has no table for [locale].
     * @throws StringTableException when the table exists but cannot be read.
     */
    suspend fun load(locale: LocaleId): Map<String, String>?

    companion object {
        /** The directory a project keeps its tables in: `strings/en.json`, `strings/ko.json`. */
        const val DEFAULT_DIRECTORY: String = "strings"

        /**
         * Tables read from project assets, one file per locale: `<directory>/<locale tag>.json`.
         *
         * They are read through [assets], the same [AssetSource] the rest of the project's content
         * comes through. A read that fails is taken as no table for that locale.
         *
         * @param assets The project's assets.
         * @param directory The directory under the project root.
         * @return The source.
         */
        fun assets(assets: AssetSource, directory: String = DEFAULT_DIRECTORY): StringTableSource =
            StringTableSource { locale ->
                val path = AssetPath(if (directory.isEmpty()) "${locale.tag}.json" else "${directory.trimEnd('/')}/${locale.tag}.json")
                assets.read(path).getOrNull()?.let { StringTables.decode(it, path.value) }
            }

        /**
         * Tables held in memory, for tests and for text built in code.
         *
         * @param tables Each locale's text by key.
         * @return The source.
         */
        fun of(tables: Map<LocaleId, Map<String, String>>): StringTableSource = StringTableSource { tables[it] }
    }
}

/** Reads and writes the string table file format. */
object StringTables {
    private val json = Json { isLenient = false }

    /**
     * Decodes a string table: a JSON object whose values are text or, to group keys, objects of the
     * same shape. A nested key joins its parents with `.`, so `{"menu": {"start": "Start"}}` holds
     * the key `menu.start`.
     *
     * @param bytes The file, UTF-8.
     * @param name Names the table in error messages.
     * @return The text by key.
     * @throws StringTableException when the file is not JSON, not an object, or holds a value that
     *   is neither text nor an object.
     */
    fun decode(bytes: ByteArray, name: String = "string table"): Map<String, String> {
        val root = try {
            json.parseToJsonElement(bytes.decodeToString())
        } catch (e: IllegalArgumentException) {
            throw StringTableException("$name is not valid JSON: ${e.message}", e)
        }
        if (root !is JsonObject) throw StringTableException("$name must be a JSON object of keys to text.")
        val out = HashMap<String, String>(root.size * 2)
        flatten(root, prefix = "", out, name)
        return out
    }

    private fun flatten(node: JsonObject, prefix: String, out: MutableMap<String, String>, name: String) {
        for ((key, value) in node) {
            val full = if (prefix.isEmpty()) key else "$prefix.$key"
            when {
                value is JsonObject -> flatten(value, full, out, name)
                value is JsonPrimitive && value.isString -> {
                    if (out.put(full, value.content) != null) throw StringTableException("$name holds the key \"$full\" twice.")
                }
                else -> throw StringTableException("$name: \"$full\" must be text or an object, not ${describe(value)}.")
            }
        }
    }

    private fun describe(value: JsonElement): String = when (value) {
        JsonNull -> "null"
        is JsonPrimitive -> "the value ${value.content}"
        else -> "an array"
    }
}
