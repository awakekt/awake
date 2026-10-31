/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The levels of detail of one model in a glTF document.
 *
 * @property node The node that stands for the model in the scene: the full-detail level.
 * @property levels Node indices, finest first; `levels[0]` is [node].
 * @property screenCoverage The smallest screen coverage each level is drawn at (see
 *   [LodLevelSetting.screenCoverage]), one per level, or null when the document gives none.
 */
data class GltfLodChain(val node: Int, val levels: List<Int>, val screenCoverage: List<Float>?)

/**
 * Reads the levels of detail a glTF document already carries, in either form a DCC tool or a cooker
 * writes: the `MSFT_lod` node extension with its `MSFT_screencoverage` extras, or sibling nodes named
 * `Name_LOD0`, `Name_LOD1`, ... (or `Name`, `Name_LOD1`, ...).
 */
object GltfLods {
    /** The `MSFT_lod` extension's name. */
    const val EXTENSION: String = "MSFT_lod"

    /** The node extras key holding per-level screen coverage. */
    const val SCREEN_COVERAGE: String = "MSFT_screencoverage"

    private val LOD_SUFFIX = Regex("^(.*)_LOD(\\d+)$", RegexOption.IGNORE_CASE)

    /** Every LOD chain in the glTF JSON [document]: `MSFT_lod` chains first, then named ones. */
    fun read(document: JsonObject): List<GltfLodChain> {
        val nodes = document["nodes"]?.jsonArray?.map { it.jsonObject } ?: return emptyList()
        val extensionChains = nodes.mapIndexedNotNull { index, node ->
            val ids = node.extension()?.get("ids")?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: return@mapIndexedNotNull null
            val coverage = (node["extras"] as? JsonObject)?.get(SCREEN_COVERAGE) as? JsonArray
            GltfLodChain(index, listOf(index) + ids, coverage?.mapNotNull { it.jsonPrimitive.floatOrNull })
        }
        return extensionChains + namedChains(nodes, extensionChains.flatMap { it.levels }.toSet())
    }

    /** Whether [document] already carries levels of detail in either form, so a cooker leaves it as it is. */
    fun hasAuthoredLods(document: JsonObject): Boolean {
        val used = document["extensionsUsed"]?.jsonArray?.any { it.jsonPrimitive.contentOrNull == EXTENSION } == true
        return used || read(document).isNotEmpty()
    }

    private fun JsonObject.extension(): JsonObject? = ((this["extensions"] as? JsonObject)?.get(EXTENSION)) as? JsonObject

    private fun namedChains(nodes: List<JsonObject>, taken: Set<Int>): List<GltfLodChain> {
        val byBase = LinkedHashMap<String, MutableMap<Int, Int>>()
        val names = nodes.map { (it["name"]?.jsonPrimitive?.contentOrNull) }
        names.forEachIndexed { index, name ->
            val match = name?.let(LOD_SUFFIX::matchEntire) ?: return@forEachIndexed
            val level = match.groupValues[2].toIntOrNull()
            if (index in taken || level == null) return@forEachIndexed
            byBase.getOrPut(match.groupValues[1]) { mutableMapOf() }[level] = index
        }
        return byBase.mapNotNull { (base, levels) ->
            // `Name` itself stands for level 0 when no `Name_LOD0` exists.
            val zero = levels[0] ?: names.indexOf(base).takeIf { it >= 0 && it !in taken }
            val ordered = listOfNotNull(zero) + levels.entries.filter { it.key > 0 }.sortedBy { it.key }.map { it.value }
            if (zero == null || ordered.size < 2) null else GltfLodChain(zero, ordered, null)
        }
    }
}
