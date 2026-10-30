/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.project.IndexedAssetSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Fetches the content of the scene at [path] ahead of loading it, reporting the share of its bytes
 * that have arrived to [onProgress], on the calling coroutine.
 *
 * The bytes are those of the scene file and of every file it names, found as the strings in its
 * document that are the path of a file in these files (or the part of one before a `#`, as a model's
 * mesh is named). Only a source that can say how big its files are can be counted: an
 * [IndexedAssetSource] with a [com.awakekt.awake.project.ContentCache], through
 * [IndexedAssetSource.prefetch], which fills the cache the load then reads from; without a cache the
 * prefetch would only be fetched again. For any other source this reports nothing, and the caller
 * reports 0 and then 1.
 *
 * A scene that can't be read or isn't JSON is left for the load to report, in its own words.
 */
internal suspend fun AssetSource.prefetchSceneContent(path: String, onProgress: (Float) -> Unit) {
    val indexed = (this as? IndexedAssetSource)?.takeIf { it.hasCache } ?: return
    val document = readJson(path) ?: return
    val named = LinkedHashSet<String>()
    named += path
    collectStrings(document) { value ->
        for (candidate in listOf(value, value.substringBefore('#'))) {
            if (candidate.isNotEmpty() && candidate !in named && indexed.exists(AssetPath(candidate))) named += candidate
        }
    }
    indexed.prefetch(named.toList()) { done, total ->
        if (total > 0L) onProgress(done.toFloat() / total.toFloat())
    }
}

private suspend fun collectStrings(element: JsonElement, visit: suspend (String) -> Unit) {
    when (element) {
        is JsonObject -> element.values.forEach { collectStrings(it, visit) }
        is JsonArray -> element.forEach { collectStrings(it, visit) }
        is JsonPrimitive -> if (element.isString) visit(element.content)
    }
}

private suspend fun AssetSource.readJson(path: String): JsonElement? =
    read(AssetPath(path)).getOrNull()?.let { bytes -> runCatching { Json.parseToJsonElement(bytes.decodeToString()) }.getOrNull() }
